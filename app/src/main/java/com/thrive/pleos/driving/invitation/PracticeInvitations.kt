package com.thrive.pleos.driving.invitation

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import com.thrive.pleos.MainActivity
import com.thrive.pleos.R
import com.thrive.pleos.driving.navigation.*
import com.thrive.pleos.driving.recording.DriveRecorder
import com.thrive.pleos.driving.storage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

internal data class InvitationState(val enabled: Boolean = false,
    val message: String = "참여 제안 알림이 꺼져 있어요.", val error: String? = null)

/** Process-lifetime SDK listener, without a keep-alive service, wake lock or boot receiver. */
internal class PracticeInvitations private constructor(context: Context) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val navigation = NavigationConnection(context, scope, DriveRecorder.get(context)::observeNavigation)
    private val park = ParkConnection(context, scope)
    private val store = DriverPreferencesStore(context)
    private val dao = DriveDatabase.get(context).drives()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val operations = Mutex()
    private val mutableState = MutableStateFlow(InvitationState())
    val state = mutableState.asStateFlow()
    private var preferences = DriverPreferences()
    private var recordsLoaded = false
    private var lastVoiceSetting: Boolean? = null
    private var activeSession = true
    private var visibleActivities = 0
    private var parkConnected = false
    private var lastParkRequest = Long.MIN_VALUE
    private var pending: PracticeInvitation? = null

    init {
        navigation.connect()
        scope.launch {
            try {
                DriveRecorder.get(context).awaitReady()
                combine(store.preferences, dao.observeAll()) { settings, records -> settings to records }.collect { (settings, records) ->
                    preferences = settings; activeSession = records.any { it.activeSlot == 1 }; recordsLoaded = true
                    if (lastVoiceSetting != settings.voice) {
                        DriveRecorder.get(context).setCoachingEnabled(settings.voice)
                        lastVoiceSetting = settings.voice
                    }
                    operations.withLock { evaluate() }
                }
            } catch (error: Exception) { if (error is CancellationException) throw error; failed(error) }
        }
        scope.launch { combine(navigation.state, park.state) { _, _ -> Unit }.collect { operations.withLock { evaluate() } } }
        scope.launch {
            while (isActive) {
                operations.withLock { evaluate() }
                delay(if (preferences.invitations) 1_000 else 10_000)
            }
        }
    }

    fun activityStarted() { visibleActivities++ }
    fun activityStopped() {
        visibleActivities = (visibleActivities - 1).coerceAtLeast(0)
        if (visibleActivities == 0 && preferences.invitations) navigation.queryDestination()
    }

    fun setEnabled(enabled: Boolean) {
        scope.launch { operations.withLock {
            try {
                if (enabled) check(permissionsAllowed()) { "차량 주행 정보와 알림 권한을 허용해 주세요." }
                if (!enabled) cancelPending()
                store.setInvitations(enabled)
                preferences = preferences.copy(invitations = enabled)
                mutableState.value = state.value.copy(enabled = enabled, error = null)
                evaluate()
            } catch (error: Exception) { if (error is CancellationException) throw error; failed(error) }
        } }
    }

    fun reconnect() {
        parkConnected = false; park.release()
        if (!navigation.state.value.connected && !navigation.state.value.connecting) navigation.connect() else navigation.queryDestination()
        scope.launch { operations.withLock { mutableState.value = state.value.copy(error = null); evaluate() } }
    }

    private fun permissionsAllowed(): Boolean = context.checkSelfPermission(CAR_DRIVE_PERMISSION) == PackageManager.PERMISSION_GRANTED &&
        (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        manager.areNotificationsEnabled()

    private suspend fun evaluate() {
        if (!recordsLoaded) return
        val allowed = permissionsAllowed()
        if (!preferences.invitations || !allowed || activeSession) {
            cancelPending()
            if (parkConnected) { park.release(); parkConnected = false }
            mutableState.value = state.value.copy(enabled = preferences.invitations, message = when {
                !preferences.invitations -> "참여 제안 알림이 꺼져 있어요."
                !allowed -> "차량 주행 정보와 알림 권한을 확인해 주세요."
                else -> "진행 중인 기록이 있어 참여 제안을 보내지 않아요."
            })
            return
        }
        if (!parkConnected) { parkConnected = true; park.connect(); lastParkRequest = now() }
        val nav = navigation.state.value
        val current = nav.freshDestination(now())
        val proposal = pending
        if (proposal != null && (!proposal.validAt(now()) || !nav.connected ||
                (!nav.querying && (nav.routeMode != "ROUTE_GUIDING" || nav.destination?.samePlace(proposal.destination) != true)) ||
                (!park.state.value.querying && !park.state.value.freshPark(now())))) cancelPending()
        val needsPark = pending != null || (visibleActivities == 0 && current != null &&
            !invitationWasSeen(invitationKey(current), preferences.invitationHistory, System.currentTimeMillis()))
        if (needsPark && !park.state.value.querying && now() - lastParkRequest >= 3_000) {
            lastParkRequest = now(); park.query()
        }
        mutableState.value = state.value.copy(enabled = true, message = when {
            !nav.connected -> "기본 내비 연결을 확인해 주세요."
            pending != null -> "참여 제안을 보냈어요. 정차 후 알림함에서 확인해 주세요."
            park.state.value.querying -> "주차 상태를 확인하고 있어요."
            park.state.value.gear != "GEAR_PARK" -> park.state.value.message
            visibleActivities > 0 -> "기본 내비를 사용하는 동안 주차(P) 상태에서 제안해요."
            current == null -> "기본 내비에서 안내 중인 목적지를 기다리고 있어요."
            else -> park.state.value.message
        })
        if (pending != null) return
        val target = invitationCandidate(nav, park.state.value, preferences.invitations, activeSession,
            visibleActivities > 0, allowed, preferences.invitationHistory, now(), System.currentTimeMillis()) ?: return
        try {
            // ponytail: a state change during persistence can suppress an undelivered offer; add retry bookkeeping only if delivery guarantees are required.
            val at = System.currentTimeMillis()
            store.markInvitationShown(invitationKey(target), at)
            preferences = preferences.copy(invitationHistory = preferences.invitationHistory + "$at:${invitationKey(target)}")
            if (dao.active() != null) return
            val rechecked = invitationCandidate(navigation.state.value, park.state.value, preferences.invitations,
                false, visibleActivities > 0, permissionsAllowed(), emptySet(), now(), System.currentTimeMillis())
            if (rechecked?.samePlace(target) != true) return
            val item = PracticeInvitation(UUID.randomUUID().toString(), rechecked, now())
            publish(item); pending = item
            mutableState.value = state.value.copy(message = "참여 제안을 보냈어요. 정차 후 알림함에서 확인해 주세요.", error = null)
        } catch (error: Exception) { if (error is CancellationException) throw error; failed(error) }
    }

    private fun publish(item: PracticeInvitation) {
        manager.createNotificationChannel(NotificationChannel("practice_invitations", "운전 연습 참여 제안", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "기본 내비의 목적지로 연습할지 선택하는 알림"; setSound(null, null); enableVibration(false); setShowBadge(false) })
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
            .setData(Uri.parse("thrive://practice-invitation/${item.token}"))
            .putExtra(INVITATION_EXTRA, item.token).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val dismiss = PendingIntent.getBroadcast(context, 0, Intent(context, InvitationDismissReceiver::class.java)
            .setData(Uri.parse("thrive://dismiss-invitation/${item.token}"))
            .putExtra(INVITATION_EXTRA, item.token), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notice = Notification.Builder(context, "practice_invitations").setSmallIcon(R.drawable.ic_drive_recording)
            .setContentTitle("이번 길을 연습해 볼까요?")
            .setContentText("기본 내비의 현재 목적지로 연습을 준비해요. 정차 후 확인해 주세요.")
            .setContentIntent(open).setDeleteIntent(dismiss).setAutoCancel(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_RECOMMENDATION).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setTimeoutAfter(INVITATION_LIFETIME_MS)
            .addAction(Notification.Action.Builder(null, "연습 준비", open).build())
            .addAction(Notification.Action.Builder(null, "나중에", dismiss).build()).build()
        manager.notify(item.token, INVITATION_NOTIFICATION_ID, notice)
    }

    /** Opening the app never starts recording; re-query route and PARK before showing preparation. */
    suspend fun open(token: String): LiveDestination = try { scope.async {
        val item = operations.withLock {
            val current = checkNotNull(pending) { "참여 제안이 만료됐어요. 기본 내비 목적지를 다시 확인해 주세요." }
            check(current.token == token && current.validAt(now())) { "참여 제안이 만료됐어요. 기본 내비 목적지를 다시 확인해 주세요." }
            cancelPending()
            current
        }
        check(preferences.invitations && permissionsAllowed()) { "참여 제안 설정과 권한을 확인해 주세요." }
        check(dao.active() == null) { "진행 중인 기록을 먼저 이어가거나 종료해 주세요." }
        val requestedAt = now()
        navigation.queryDestination(); park.query()
        val snapshot = withTimeout(9_000) { navigation.state.first {
            !it.querying && it.confirmedAtElapsedMs?.let { at -> at >= requestedAt } == true &&
                it.routeConfirmedAtMs?.let { at -> at >= requestedAt } == true
        } }
        val parked = withTimeout(6_000) { park.state.first { !it.querying && it.receivedAtMs?.let { at -> at >= requestedAt } == true } }
        val destination = snapshot.freshDestination(now())
        check(destination?.samePlace(item.destination) == true && snapshot.routeMode == "ROUTE_GUIDING") { "내비 목적지가 변경됐어요. 현재 경로를 다시 확인해 주세요." }
        check(parked.freshPark(now())) { "주차(P) 상태에서 연습을 준비해 주세요." }
        check(item.validAt(now()) && preferences.invitations && permissionsAllowed() && dao.active() == null) { "제안이나 기록 상태가 변경됐어요. 다시 확인해 주세요." }
        checkNotNull(destination)
    }.await() } catch (error: TimeoutCancellationException) {
        throw IllegalStateException("목적지와 주차 상태를 다시 확인하지 못했어요. 연결을 확인하고 다시 시도해 주세요.", error)
    }

    fun dismiss(token: String) { if (pending?.token == token) cancelPending() }
    private fun cancelPending() {
        pending?.let { manager.cancel(it.token, INVITATION_NOTIFICATION_ID) }
        pending = null
    }
    private fun failed(error: Exception) { mutableState.value = state.value.copy(error = "참여 제안을 처리하지 못했어요 (${error.message ?: error.javaClass.simpleName}).") }
    private fun now() = SystemClock.elapsedRealtime()

    companion object {
        // Only the application context is retained; the SDK listener dies with this app process.
        @SuppressLint("StaticFieldLeak") @Volatile private var instance: PracticeInvitations? = null
        fun get(context: Context): PracticeInvitations = instance ?: synchronized(this) {
            instance ?: PracticeInvitations(context).also { instance = it }
        }
        fun existing(): PracticeInvitations? = instance
    }
}

internal class InvitationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra(INVITATION_EXTRA) ?: return
        context.getSystemService(NotificationManager::class.java).cancel(token, INVITATION_NOTIFICATION_ID)
        PracticeInvitations.existing()?.dismiss(token)
    }
}
