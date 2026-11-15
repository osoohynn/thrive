package com.thrive.pleos.driving.recording

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.thrive.pleos.driving.navigation.*
import com.thrive.pleos.driving.coaching.*
import com.thrive.pleos.ui.driving.PracticeGoal
import java.util.UUID
import com.thrive.pleos.driving.SessionTiming
import com.thrive.pleos.driving.location.*
import com.thrive.pleos.driving.storage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class RecorderState(val elapsedMs: Long = 0, val error: String? = null)

/** One recorder per app process; Activities observe it without owning its lifetime. */
internal class DriveRecorder private constructor(context: Context) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dao = DriveDatabase.get(context).drives()
    private val operations = Mutex()
    private val location = LocationConnection(context, scope)
    val locationState = location.state
    private val mutableState = MutableStateFlow(RecorderState())
    val state = mutableState.asStateFlow()
    private val timing = SessionTiming()
    private var timerJob: Job? = null
    private var sessionId: String? = null
    private var sdkRecording = false
    private var track: LocationTrack? = null
    private var lastLocationSequence = 0L
    private var lastLocationMessage: String? = null
    private var pendingForeground: Pair<String, CompletableDeferred<Unit>>? = null
    // Only process startup interrupts orphaned records, never a new Activity/ViewModel.
    private var initialized = false
    private var acceptingNavigation = false
    private var navigationAnchorMs = 0L
    private var currentGoal = PracticeGoal.Daily
    private val eventGate = NavigationEventGate()
    private val navigationBuffer = ArrayDeque<SavedNavigationEvent>()
    private var navigationLoss = 0
    private var navigationSaveError: String? = null
    private val coaching = CoachingQueue()
    private val mutableCoaching = MutableStateFlow(CoachingState())
    val coachingState = mutableCoaching.asStateFlow()

    fun setCoachingEnabled(enabled: Boolean) {
        coaching.setEnabled(enabled)
        mutableCoaching.value = coaching.state(SystemClock.elapsedRealtime())
    }

    fun observeNavigation(event: NavigationObservation) {
        val id = sessionId ?: return
        if (!sdkRecording || !acceptingNavigation || event.receivedElapsedMs < navigationAnchorMs) return
        if (!eventGate.accept(event)) return
        coaching.offer(event, currentGoal, SystemClock.elapsedRealtime())
        mutableCoaching.value = coaching.state(SystemClock.elapsedRealtime())
        if (navigationBuffer.size >= 256) { navigationLoss++; return }
        navigationBuffer.addLast(SavedNavigationEvent(eventKey = UUID.randomUUID().toString(), sessionId = id,
            kind = event.kind.name, source = "SDK", elapsedMs = timing.elapsed(event.receivedElapsedMs),
            receivedEpochMs = event.receivedEpochMs, detail = event.detail.take(200), turnType = event.turnType))
    }

    private suspend fun flushNavigation(id: String) {
        if (navigationBuffer.isEmpty() && navigationLoss == 0) return
        val events = navigationBuffer.toList()
        val lost = navigationLoss
        dao.appendNavigationEvents(id, events, lost)
        repeat(events.size) { navigationBuffer.removeFirst() }
        navigationLoss -= lost
        if (mutableState.value.error == navigationSaveError) mutableState.value = state.value.copy(error = null)
        navigationSaveError = null
    }

    private suspend fun initialize() {
        if (initialized) return
        dao.interruptOpenSessions()
        initialized = true
    }

    suspend fun awaitReady() { operations.withLock { initialize() } }

    private suspend fun <T> operation(block: suspend () -> T): T = scope.async {
        operations.withLock { initialize(); block() }
    }.await()

    suspend fun start(proposed: SavedDrive): SavedDrive = operation {
        val existing = dao.active()
        check(existing == null) { "진행 중인 기록을 먼저 이어가거나 종료해 주세요." }
        if (proposed.source == "SDK") check(context.hasRecordingLocationPermission()) { "위치 권한을 허용해 주세요." }
        val session = dao.startOnce(proposed)
        attach(session)
        session
    }

    suspend fun resume(): SavedDrive = operation {
        val session = checkNotNull(dao.active())
        if (session.source == "SDK") check(context.hasRecordingLocationPermission()) { "위치 권한을 허용해 주세요." }
        if (sessionId != session.id) {
            if (session.status == SessionStatus.Interrupted.name) {
                dao.resume(session.id)
            }
            if (session.source == "SDK") dao.markRecordingGap(session.id)
            attach(checkNotNull(dao.find(session.id)))
        }
        checkNotNull(dao.find(session.id))
    }

    private suspend fun attach(session: SavedDrive) {
        check(sessionId == null)
        mutableState.value = RecorderState(session.elapsedMs)
        try {
            if (session.source == "SDK") {
                val confirmation = CompletableDeferred<Unit>()
                pendingForeground = session.id to confirmation
                context.startForegroundService(Intent(context, DriveRecordingService::class.java)
                    .putExtra(DriveRecordingService.SESSION_ID, session.id))
                withTimeout(5_000) { confirmation.await() }
            }
            sessionId = session.id
            sdkRecording = session.source == "SDK"
            navigationAnchorMs = SystemClock.elapsedRealtime()
            timing.start(session.elapsedMs, navigationAnchorMs)
            currentGoal = PracticeGoal.entries.firstOrNull { it.name == session.goal } ?: PracticeGoal.Daily
            eventGate.clear(); coaching.reset(); navigationBuffer.clear(); navigationLoss = 0
            acceptingNavigation = sdkRecording
            if (sdkRecording) navigationBuffer.addLast(SavedNavigationEvent(eventKey = UUID.randomUUID().toString(),
                sessionId = session.id, kind = if (session.elapsedMs > 0) "RecordingResumed" else "RecordingStarted", source = "APP",
                elapsedMs = session.elapsedMs, receivedEpochMs = System.currentTimeMillis(), detail = ""))
            if (sdkRecording) startLocation(session)
            timerJob = scope.launch {
                var ticks = 0
                while (isActive) {
                    operations.withLock {
                        if (sessionId != session.id) return@launch
                        val elapsed = timing.elapsed(SystemClock.elapsedRealtime())
                        mutableState.value = state.value.copy(elapsedMs = elapsed)
                        if (sdkRecording) {
                            sampleLocation(session.id)
                            try { flushNavigation(session.id) }
                            catch (error: Exception) { if (error is CancellationException) throw error; reportNavigationError(error) }
                            mutableCoaching.value = coaching.state(SystemClock.elapsedRealtime())
                        }
                        if (ticks++ % 5 == 0) {
                            try { dao.checkpoint(session.id, elapsed) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (error: Exception) { reportError("경과 시간을 저장하지 못했어요", error) }
                        }
                    }
                    delay(1_000)
                }
            }
        } catch (error: Exception) {
            try { dao.interrupt(session.id) } finally { stopRecording() }
            throw error
        } finally { pendingForeground = null }
    }

    fun expectsForeground(id: String): Boolean = pendingForeground?.first == id || (sessionId == id && sdkRecording)

    fun foregroundStarted(id: String) {
        pendingForeground?.takeIf { it.first == id }?.second?.complete(Unit)
    }

    fun foregroundFailed(id: String, error: Exception) {
        pendingForeground?.takeIf { it.first == id }?.second?.completeExceptionally(error)
    }

    fun foregroundStopped(id: String) {
        pendingForeground?.takeIf { it.first == id }?.second?.completeExceptionally(IllegalStateException("기록 서비스가 중단됐어요."))
        scope.launch {
            operations.withLock {
                if (sessionId != id || !sdkRecording) return@withLock
                try {
                    acceptingNavigation = false
                    try { flushNavigation(id) }
                    catch (error: Exception) { if (error is CancellationException) throw error; reportError("중단 직전 내비 사건을 저장하지 못했어요", error) }
                    dao.checkpoint(id, timing.elapsed(SystemClock.elapsedRealtime()))
                    dao.interrupt(id)
                } catch (error: Exception) { reportError("서비스 중단 상태를 저장하지 못했어요", error) }
                finally {
                    stopRecording()
                    mutableState.value = state.value.copy(error = "기록 서비스가 중단됐어요. 저장된 기록에서 이어가거나 종료해 주세요.")
                }
            }
        }
    }

    suspend fun reconnectLocation() = operation {
        val session = dao.active() ?: return@operation
        if (session.id == sessionId && sdkRecording) {
            check(context.hasRecordingLocationPermission()) { "위치 권한을 허용해 주세요." }
            dao.markRecordingGap(session.id)
            startLocation(checkNotNull(dao.find(session.id)))
        }
    }

    /** A notification carries its session ID so a late action cannot end a later drive. */
    suspend fun finish(expectedId: String? = null): SavedDrive? = operation {
        val session = dao.active() ?: return@operation null
        if (expectedId != null && session.id != expectedId) return@operation null
        val elapsed = if (sessionId == session.id) timing.elapsed(SystemClock.elapsedRealtime()) else session.elapsedMs
        // Keep recording until the completion is durable; a failed write remains retryable.
        acceptingNavigation = false
        coaching.clear(); mutableCoaching.value = coaching.state(SystemClock.elapsedRealtime())
        try {
            if (session.source == "SDK") flushNavigation(session.id)
            dao.finishOnce(session.id, System.currentTimeMillis(), elapsed)
        } catch (error: Exception) { acceptingNavigation = sdkRecording; throw error }
        stopRecording()
        checkNotNull(dao.find(session.id))
    }

    fun finishFromNotification(id: String) {
        scope.launch {
            try { finish(id) }
            catch (error: Exception) { reportError("기록을 종료하지 못했어요. 앱에서 다시 시도해 주세요", error) }
        }
    }

    private fun stopRecording() {
        acceptingNavigation = false
        navigationBuffer.clear(); navigationLoss = 0; eventGate.clear(); coaching.reset()
        mutableCoaching.value = coaching.state(SystemClock.elapsedRealtime())
        sessionId = null
        sdkRecording = false
        timerJob?.cancel(); timerJob = null
        timing.stop(SystemClock.elapsedRealtime())
        location.stop(); track = null
        val service = Intent(context, DriveRecordingService::class.java)
        context.stopService(service)
    }

    fun refreshNotification() {
        val id = sessionId ?: return
        if (!sdkRecording) return
        try {
            context.startForegroundService(Intent(context, DriveRecordingService::class.java)
                .putExtra(DriveRecordingService.SESSION_ID, id))
        } catch (error: Exception) { reportError("기록 알림을 갱신하지 못했어요", error) }
    }

    fun clearError() { navigationSaveError = null; mutableState.value = state.value.copy(error = null) }
    private fun reportNavigationError(error: Exception) {
        navigationSaveError = "내비 사건을 저장하지 못했어요. 다음 저장에서 다시 시도해요 (${error.javaClass.simpleName})"
        mutableState.value = state.value.copy(error = navigationSaveError)
    }
    private fun reportError(message: String, error: Exception) {
        mutableState.value = state.value.copy(error = "$message (${error.javaClass.simpleName})")
    }

    private fun startLocation(session: SavedDrive) {
        track = LocationTrack(TrackSummary(session.distanceMeters, session.acceptedFixes, session.rejectedFixes, session.gapCount))
        lastLocationSequence = 0
        lastLocationMessage = null
        location.start()
    }

    private suspend fun sampleLocation(sessionId: String) {
        val processor = track ?: return
        val update = location.state.value
        try {
            val fix = update.fix
            if (update.sequence != lastLocationSequence && fix != null) {
                val result = processor.accept(fix, SystemClock.elapsedRealtime())
                if (dao.appendLocation(sessionId, result, update.message)) lastLocationSequence = update.sequence
            }
            val stale = fix != null && SystemClock.elapsedRealtime() - fix.receivedElapsedMs > LocationTrack.MAX_RECEIPT_AGE_MS
            val message = if (stale) "위치 수신이 끊겼어요. 해당 구간의 이동을 연결해 계산하지 않아요." else update.message
            if (message != lastLocationMessage) {
                if (stale) {
                    processor.breakSegment()
                    val summary = processor.summary
                    dao.updateLocationSummary(sessionId, summary.distanceMeters, summary.accepted, summary.rejected, summary.gaps, message)
                } else dao.updateLocationMessage(sessionId, message)
                lastLocationMessage = message
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            // Do not carry uncommitted distance into the next sample after an I/O failure.
            track = null
            location.stop()
            reportError("위치 저장을 중단했어요. 위치 연결을 다시 시도해 주세요", error)
        }
    }


    companion object {
        // Only the application context is retained for the lifetime of this process.
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: DriveRecorder? = null
        fun get(context: Context): DriveRecorder = instance ?: synchronized(this) {
            instance ?: DriveRecorder(context).also { instance = it }
        }
    }
}
