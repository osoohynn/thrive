package com.thrive.pleos.ui.driving

import android.app.Application
import android.os.SystemClock
import android.os.Build
import com.thrive.pleos.driving.location.*
import com.thrive.pleos.driving.navigation.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thrive.pleos.driving.voice.VoiceGuidance
import com.thrive.pleos.driving.recording.DriveRecorder
import com.thrive.pleos.driving.invitation.PracticeInvitations
import com.thrive.pleos.driving.invitation.ParkConnection
import com.thrive.pleos.driving.storage.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class SaveFeedback(val id: Long, val key: String, val savedAtMs: Long)

internal data class DrivingLocalState(
    val preferences: DriverPreferences = DriverPreferences(),
    val preferencesLoaded: Boolean = false,
    val recordsLoaded: Boolean = false,
    val records: List<SavedDrive> = emptyList(),
    val elapsedMs: Long = 0,
    val busy: Boolean = false,
    val error: String? = null,
    val locationPermissionNotice: String? = null,
    val saved: SaveFeedback? = null,
    val savingKey: String? = null,
) {
    val ready: Boolean get() = preferencesLoaded && recordsLoaded
    val active: SavedDrive? get() = records.firstOrNull { it.activeSlot == 1 }
}

internal class DrivingViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = DriveDatabase.get(application).drives()
    private val preferences = DriverPreferencesStore(application)
    private val mutableState = MutableStateFlow(DrivingLocalState())
    val local = mutableState.asStateFlow()
    private val invitations = PracticeInvitations.get(application)
    val invitationState = invitations.state
    private val navigation = invitations.navigation
    val navigationState = navigation.state
    private val recorder = DriveRecorder.get(application)
    val locationState = recorder.locationState
    val coachingState = recorder.coachingState
    private val mutableTimeline = MutableStateFlow(NavigationTimeline())
    val timeline = mutableTimeline.asStateFlow()
    private var timelineJob: Job? = null
    private val voiceGuidance = VoiceGuidance(application, viewModelScope)
    val voiceState = voiceGuidance.state
    private val motionParking = ParkConnection(application, viewModelScope)
    val parkingState = motionParking.state
    private var saveSequence = 0L
    private val operations = Mutex()
    private var loadingJob: Job? = null
    private var preferencesJob: Job? = null
    private var previousRecorderError: String? = null

    init {
        reload()
        viewModelScope.launch {
            recorder.state.collect { recording ->
                mutableState.update { it.copy(elapsedMs = recording.elapsedMs, error = recording.error ?:
                    if (it.error == previousRecorderError) null else it.error) }
                previousRecorderError = recording.error
            }
        }
    }

    fun reload() {
        loadingJob?.cancel()
        preferencesJob?.cancel()
        recorder.clearError()
        timelineJob?.cancel(); timelineJob = null
        observeTimeline(mutableTimeline.value.sessionId)
        mutableState.update { it.copy(error = null) }
        preferencesJob = viewModelScope.launch {
            try {
                preferences.preferences.collect { profile -> mutableState.update { it.copy(preferences = profile, preferencesLoaded = true) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { showError("설정을 읽지 못했어요", error) }
        }
        loadingJob = viewModelScope.launch {
            try {
                recorder.awaitReady()
                dao.observeAll().collect { records -> mutableState.update { it.copy(records = records, recordsLoaded = true) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { showError("기록을 읽지 못했어요", error) }
        }
    }

    private fun operation(block: suspend () -> Unit) {
        viewModelScope.launch {
            operations.withLock {
                mutableState.update { it.copy(busy = true, error = null) }
                try { recorder.awaitReady(); block() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { showError("저장을 완료하지 못했어요. 다시 시도해 주세요", error) }
                finally { mutableState.update { it.copy(busy = false) } }
            }
        }
    }

    private fun showError(message: String, error: Exception) {
        mutableState.update { it.copy(error = "$message (${error.javaClass.simpleName})") }
    }

    private fun save(key: String, block: suspend () -> Unit) {
        val revision = ++saveSequence
        mutableState.update { it.copy(saved = null, savingKey = key) }
        operation {
            try {
                block()
                // A newer edit supersedes this receipt, even if it is still queued.
                if (revision == saveSequence) mutableState.update { it.copy(saved = SaveFeedback(revision, key, SystemClock.elapsedRealtime())) }
            } finally {
                if (revision == saveSequence) mutableState.update { it.copy(savingKey = null) }
            }
        }
    }

    suspend fun observeMotionParking() {
        motionParking.connect()
        try {
            while (currentCoroutineContext().isActive) { delay(3_000); motionParking.query() }
        } finally { motionParking.release() }
    }

    fun setExperience(value: String) = save("profile") { preferences.setExperience(value) }
    fun toggleConcern(value: String) = save("profile") { preferences.toggleConcern(value) }
    fun setReducedMotion(value: Boolean) = save("profile") { preferences.setReducedMotion(value) }
    fun setVoice(enabled: Boolean) {
        voiceGuidance.setMuted(!enabled)
        recorder.setCoachingEnabled(enabled)
        save("voice") { preferences.setVoice(enabled) }
    }

    fun previewGuidance(goal: PracticeGoal) {
        // Live navigation/audio priority is not validated; keep this feature in preparation and DEMO.
        if (local.value.active?.source == "SDK") return
        voiceGuidance.play("preview:${goal.name}", goal.voiceCue, local.value.ready && local.value.preferences.voice)
    }

    fun playDemoGuidance() {
        val session = local.value.active ?: return
        if (session.source != "DEMO" || session.status != SessionStatus.Recording.name) return
        val goal = PracticeGoal.valueOf(session.goal)
        voiceGuidance.play("${session.id}:${goal.name}", "운전 연습 시연 안내예요. ${goal.voiceCue}", local.value.preferences.voice)
    }

    fun stopGuidance() = voiceGuidance.stop()
    fun setInvitations(enabled: Boolean) = invitations.setEnabled(enabled)
    fun reconnectInvitations() = invitations.reconnect()
    fun invitationPermissionDenied() {
        mutableState.update { it.copy(error = "참여 제안에는 차량 주행 정보와 알림 권한이 필요해요. 권한을 허용하거나 알림을 꺼둘 수 있어요.") }
    }
    fun openInvitation(token: String, onOpened: () -> Unit) = operation {
        invitations.open(token)
        onOpened()
    }

    fun refreshRecordingNotification() = recorder.refreshNotification()
    fun finishProfileEdit(onSaved: () -> Unit) = operation { onSaved() }
    fun setFeeling(id: String, value: String) = save("feeling:$id") { dao.setFeeling(id, value) }
    fun deleteRecord(id: String) = operation { dao.deleteFinished(id) }
    fun deleteAllFinished() = operation { dao.deleteAllFinished() }

    fun locationPermissionDenied() {
        mutableState.update { it.copy(locationPermissionNotice = "위치 기록에는 정확한 위치와 차량 위치 권한이 필요해요. 권한을 허용해 주세요. 앱 설정에서도 변경할 수 있어요.") }
    }

    fun clearLocationPermissionNotice() {
        mutableState.update { it.copy(locationPermissionNotice = null) }
    }

    private fun ensureLocationPermission(): Boolean {
        if (!getApplication<Application>().hasRecordingLocationPermission()) {
            locationPermissionDenied()
            return false
        }
        clearLocationPermissionNotice()
        return true
    }

    fun prepareNextPractice(id: String, onPrepared: (NextPracticePlan) -> Unit) = operation {
        val record = dao.find(id)
        val plan = record?.let { nextPracticePlan(it, dao.navigationEvents(it.id)) }
        if (plan == null) {
            mutableState.update { it.copy(error = "다음 연습을 위해 기록과 저장한 느낌을 다시 확인해 주세요.") }
            return@operation
        }
        onPrepared(plan)
    }

    fun startDemo(trip: DemoTrip, request: PracticeRequest? = null, onStarted: () -> Unit) = operation {
        if (trip.mode == DriveMode.Practice && (request == null || practiceTrip(request, trip.route) != trip)) {
            mutableState.update { it.copy(error = "연습 조건과 선택 코스가 달라요. 코스를 다시 선택해 주세요.") }
            return@operation
        }
        val existing = dao.active()
        if (existing != null) {
            if (existing.status == SessionStatus.Recording.name) onStarted()
            else mutableState.update { it.copy(error = "중단된 기록을 먼저 이어가거나 종료해 주세요.") }
            return@operation
        }
        val proposed = SavedDrive(
            id = UUID.randomUUID().toString(), mode = trip.mode.name, goal = trip.goal.name, route = trip.route.name,
            destination = trip.destination, plannedMinutes = trip.durationMinutes, startedAtEpochMs = System.currentTimeMillis(),
        )
        val session = recorder.start(proposed)
        mutableState.update { it.copy(records = listOf(session) + it.records.filterNot { row -> row.id == session.id }) }
        voiceGuidance.stop()
        onStarted()
    }

    fun startLive(expected: LiveDestination, onStarted: () -> Unit) = operation {
        createSdkRecord(expected, null, onStarted)
    }

    fun startNativePractice(plan: NativePracticePlan, onStarted: () -> Unit) = operation {
        val destination = navigation.state.value.freshDestination(SystemClock.elapsedRealtime())
        if (destination == null) {
            mutableState.update { it.copy(error = "경로 정보가 오래됐어요. 현재 경로를 다시 확인해 주세요.") }
            return@operation
        }
        createSdkRecord(destination, plan, onStarted)
    }

    private suspend fun createSdkRecord(expected: LiveDestination, plan: NativePracticePlan?, onStarted: () -> Unit) {
        if (!ensureLocationPermission()) return
        if (dao.active() != null) {
            mutableState.update { it.copy(error = "진행 중인 기록을 먼저 이어가거나 종료해 주세요.") }
            return
        }
        val snapshot = navigation.state.value
        val destination = snapshot.freshDestination(SystemClock.elapsedRealtime())
        val course = plan?.let { snapshot.confirmedCourse(it, SystemClock.elapsedRealtime()) }
        if (destination == null || destination != expected || (plan != null && course == null)) {
            mutableState.update { it.copy(error = "목적지나 경로 조건이 변경됐어요. 현재 경로를 다시 확인해 주세요.") }
            return
        }
        val estimate = course?.estimate ?: snapshot.estimate
        val emulator = Build.HARDWARE in setOf("goldfish", "ranchu") || Build.FINGERPRINT.contains("generic")
        val proposed = SavedDrive(id = UUID.randomUUID().toString(), source = "SDK",
            mode = if (plan == null) DriveMode.Destination.name else DriveMode.Practice.name,
            goal = plan?.goal?.name ?: PracticeGoal.Daily.name, route = "NATIVE", destination = destination.name,
            plannedMinutes = estimate?.minutes ?: 0, estimatedSeconds = estimate?.seconds,
            plannedDistanceMeters = estimate?.meters?.toDouble(), routeOption = snapshot.routeOption,
            practiceStopName = plan?.stop?.name, practiceStopLatitude = plan?.stop?.latitude,
            practiceStopLongitude = plan?.stop?.longitude, practiceReturn = plan?.roundTrip ?: false,
            practiceMaxMinutes = plan?.maxMinutes,
            startedAtEpochMs = System.currentTimeMillis(), environment = if (emulator) "EMULATOR" else "DEVICE",
            destinationLatitude = destination.latitude, destinationLongitude = destination.longitude)
        val session = recorder.start(proposed)
        mutableState.update { it.copy(records = listOf(session) + it.records.filterNot { row -> row.id == session.id }) }
        voiceGuidance.stop()
        navigation.queryDestination()
        onStarted()
    }

    fun requestNativePractice(plan: NativePracticePlan) = operation {
        if (dao.active() != null) {
            mutableState.update { it.copy(error = "진행 중인 기록을 종료한 뒤 경로를 변경해 주세요.") }
            return@operation
        }
        if (plan.roundTrip && navigation.state.value.freshOrigin(SystemClock.elapsedRealtime())?.samePlace(checkNotNull(plan.returnPoint)) != true) {
            mutableState.update { it.copy(error = "출발지 정보가 오래됐어요. 현재 경로를 다시 확인해 주세요.") }
            return@operation
        }
        navigation.requestPractice(plan)
    }

    fun navigationPermissionDenied() {
        mutableState.update { it.copy(error = "경로 변경 권한이 필요해요. 앱 권한 설정에서 허용한 뒤 다시 시도해 주세요.") }
    }

    fun openNavigation() = navigation.openNavigation()
    fun refreshNavigation() {
        if (navigation.state.value.connected && navigation.state.value.course?.phase != CoursePhase.Calculating && !navigation.state.value.querying)
            navigation.queryDestination()
    }

    fun resume(onResumed: () -> Unit) = operation {
        val session = checkNotNull(dao.active())
        if (session.source == "SDK" && !ensureLocationPermission()) return@operation
        voiceGuidance.stop()
        recorder.resume()
        onResumed()
    }

    fun reconnectLocation() = operation {
        if (!ensureLocationPermission()) return@operation
        recorder.reconnectLocation()
    }

    fun finish(onFinished: (String) -> Unit) = operation {
        voiceGuidance.stop()
        val finished = recorder.finish() ?: return@operation
        mutableState.update { it.copy(records = it.records.map { row -> if (row.id == finished.id) finished else row }) }
        onFinished(finished.id)
    }

    fun observeTimeline(id: String?) {
        if (mutableTimeline.value.sessionId == id && timelineJob?.isActive == true) return
        timelineJob?.cancel()
        mutableTimeline.value = NavigationTimeline(id, loaded = id == null)
        if (id == null) return
        timelineJob = viewModelScope.launch {
            try {
                dao.observeNavigationEvents(id).collect { events ->
                    if (mutableTimeline.value.sessionId == id) mutableTimeline.value = NavigationTimeline(id, true, events)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (mutableTimeline.value.sessionId == id) mutableTimeline.value = NavigationTimeline(id, true,
                    error = "내비 사건을 읽지 못했어요. 다시 읽기를 선택해 주세요.")
            }
        }
    }

    fun connectNavigation() = navigation.connect()
    fun queryDestination() = navigation.queryDestination()

    override fun onCleared() {
        motionParking.release()
        voiceGuidance.stop()
        super.onCleared()
    }
}

internal fun SavedDrive.toDemoTrip() = DemoTrip(
    DriveMode.valueOf(mode), PracticeGoal.valueOf(goal), DemoRoute.valueOf(route), destination, plannedMinutes,
)
