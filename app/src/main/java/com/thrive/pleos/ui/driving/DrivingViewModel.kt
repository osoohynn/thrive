package com.thrive.pleos.ui.driving

import android.app.Application
import android.os.SystemClock
import android.os.Build
import com.thrive.pleos.driving.location.*
import com.thrive.pleos.driving.navigation.LiveDestination
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thrive.pleos.driving.voice.VoiceGuidance
import com.thrive.pleos.driving.SessionTiming
import com.thrive.pleos.driving.navigation.NavigationConnection
import com.thrive.pleos.driving.storage.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class DrivingLocalState(
    val preferences: DriverPreferences = DriverPreferences(),
    val preferencesLoaded: Boolean = false,
    val recordsLoaded: Boolean = false,
    val records: List<SavedDrive> = emptyList(),
    val elapsedMs: Long = 0,
    val busy: Boolean = false,
    val error: String? = null,
    val locationPermissionNotice: String? = null,
) {
    val ready: Boolean get() = preferencesLoaded && recordsLoaded
    val active: SavedDrive? get() = records.firstOrNull { it.activeSlot == 1 }
}

internal class DrivingViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = DriveDatabase.get(application).drives()
    private val preferences = DriverPreferencesStore(application)
    private val mutableState = MutableStateFlow(DrivingLocalState())
    val local = mutableState.asStateFlow()
    private val navigation = NavigationConnection(application, viewModelScope)
    val navigationState = navigation.state
    private val location = LocationConnection(application, viewModelScope)
    val locationState = location.state
    private val voiceGuidance = VoiceGuidance(application, viewModelScope)
    val voiceState = voiceGuidance.state
    private var track: LocationTrack? = null
    private var lastLocationSequence = 0L
    private var lastLocationMessage: String? = null
    private val operations = Mutex()
    private val timing = SessionTiming()
    private var timerJob: Job? = null
    private var loadingJob: Job? = null
    private var preferencesJob: Job? = null

    init { reload() }

    fun reload() {
        loadingJob?.cancel()
        preferencesJob?.cancel()
        mutableState.update { it.copy(error = null) }
        preferencesJob = viewModelScope.launch {
            try {
                preferences.preferences.collect { profile -> mutableState.update { it.copy(preferences = profile, preferencesLoaded = true) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { showError("설정을 읽지 못했어요", error) }
        }
        loadingJob = viewModelScope.launch {
            try {
                // A new ViewModel means no in-memory recorder owns these sessions.
                if (timerJob == null) dao.interruptOpenSessions()
                dao.observeAll().collect { records -> mutableState.update { it.copy(records = records, recordsLoaded = true) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { showError("기록을 읽지 못했어요", error) }
        }
    }

    private fun operation(block: suspend () -> Unit) {
        viewModelScope.launch {
            operations.withLock {
                mutableState.update { it.copy(busy = true, error = null) }
                try { block() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { showError("저장을 완료하지 못했어요. 다시 시도해 주세요", error) }
                finally { mutableState.update { it.copy(busy = false) } }
            }
        }
    }

    private fun showError(message: String, error: Exception) {
        mutableState.update { it.copy(error = "$message (${error.javaClass.simpleName})") }
    }

    fun setExperience(value: String) = operation { preferences.setExperience(value) }
    fun toggleConcern(value: String) = operation { preferences.toggleConcern(value) }
    fun setVoice(enabled: Boolean) {
        voiceGuidance.setMuted(!enabled)
        operation { preferences.setVoice(enabled) }
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
    fun finishProfileEdit(onSaved: () -> Unit) = operation { onSaved() }
    fun setFeeling(id: String, value: String) = operation { dao.setFeeling(id, value) }
    fun deleteRecord(id: String) = operation { dao.deleteFinished(id) }
    fun deleteAllFinished() = operation { dao.deleteAllFinished() }

    fun locationPermissionDenied() {
        mutableState.update { it.copy(locationPermissionNotice = "위치 권한이 허용되지 않았어요. 위치를 기록하려면 권한을 허용해 주세요. 앱 설정에서도 변경할 수 있어요.") }
    }

    fun clearLocationPermissionNotice() {
        mutableState.update { it.copy(locationPermissionNotice = null) }
    }

    private fun ensureLocationPermission(): Boolean {
        if (!getApplication<Application>().hasFusedLocationPermission()) {
            locationPermissionDenied()
            return false
        }
        clearLocationPermissionNotice()
        return true
    }

    fun prepareNextPractice(id: String, onPrepared: (NextPracticePlan) -> Unit) = operation {
        val record = dao.find(id)
        val plan = record?.let { nextPracticePlan(it) }
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
        val session = dao.startOnce(proposed)
        mutableState.update { it.copy(records = listOf(session) + it.records.filterNot { row -> row.id == session.id }) }
        startTimer(session)
        onStarted()
    }

    fun startLive(expected: LiveDestination, onStarted: () -> Unit) = operation {
        if (!ensureLocationPermission()) return@operation
        if (dao.active() != null) {
            mutableState.update { it.copy(error = "진행 중인 기록을 먼저 이어가거나 종료해 주세요.") }
            return@operation
        }
        val destination = navigation.state.value.freshDestination(SystemClock.elapsedRealtime())
        if (destination == null || destination != expected) {
            mutableState.update { it.copy(error = "목적지가 변경됐거나 정보가 오래됐어요. 내비게이션 목적지를 다시 조회해 주세요.") }
            return@operation
        }
        val emulator = Build.HARDWARE in setOf("goldfish", "ranchu") || Build.FINGERPRINT.contains("generic")
        val proposed = SavedDrive(id = UUID.randomUUID().toString(), source = "SDK", mode = DriveMode.Destination.name,
            goal = PracticeGoal.Daily.name, route = "NATIVE", destination = destination.name, plannedMinutes = 0,
            startedAtEpochMs = System.currentTimeMillis(), environment = if (emulator) "EMULATOR" else "DEVICE",
            destinationLatitude = destination.latitude, destinationLongitude = destination.longitude)
        val session = dao.startOnce(proposed)
        mutableState.update { it.copy(records = listOf(session) + it.records.filterNot { row -> row.id == session.id }) }
        startTimer(session)
        onStarted()
    }

    fun resume(onResumed: () -> Unit) = operation {
        val session = checkNotNull(dao.active())
        if (session.source == "SDK" && !ensureLocationPermission()) return@operation
        if (session.status == SessionStatus.Interrupted.name) {
            dao.resume(session.id)
            if (session.source == "SDK") dao.markRecordingGap(session.id)
        }
        if (timerJob == null) startTimer(checkNotNull(dao.find(session.id)))
        onResumed()
    }

    private fun startTimer(session: SavedDrive) {
        if (session.source == "SDK") startLocation(session)
        voiceGuidance.stop()
        timing.start(session.elapsedMs, SystemClock.elapsedRealtime())
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var ticks = 0
            while (isActive) {
                val elapsed = timing.elapsed(SystemClock.elapsedRealtime())
                mutableState.update { it.copy(elapsedMs = elapsed) }
                if (session.source == "SDK") operations.withLock { sampleLocation(session.id) }
                if (ticks++ % 5 == 0) {
                    try { dao.checkpoint(session.id, elapsed) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { showError("경과 시간을 저장하지 못했어요", error) }
                }
                delay(1_000)
            }
        }
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
            showError("위치 저장을 중단했어요. 위치 연결을 다시 시도해 주세요", error)
        }
    }

    fun reconnectLocation() = operation {
        val session = dao.active() ?: return@operation
        if (session.source == "SDK" && session.status == SessionStatus.Recording.name) {
            if (!ensureLocationPermission()) return@operation
            dao.markRecordingGap(session.id)
            startLocation(checkNotNull(dao.find(session.id)))
        }
    }

    fun finish(onFinished: (String) -> Unit) = operation {
        voiceGuidance.stop()
        val session = dao.active() ?: return@operation
        val elapsed = if (timerJob != null) timing.elapsed(SystemClock.elapsedRealtime()) else session.elapsedMs
        // Stop the timer only after durable completion succeeds; failed writes can be retried.
        val endedAt = System.currentTimeMillis()
        dao.finishOnce(session.id, endedAt, elapsed)
        timerJob?.cancel(); timerJob = null
        timing.stop(SystemClock.elapsedRealtime())
        location.stop(); track = null
        val finished = checkNotNull(dao.find(session.id))
        mutableState.update { it.copy(records = it.records.map { row -> if (row.id == finished.id) finished else row }) }
        onFinished(session.id)
    }

    fun connectNavigation() = navigation.connect()
    fun queryDestination() = navigation.queryDestination()

    override fun onCleared() {
        voiceGuidance.stop()
        timerJob?.cancel()
        navigation.release()
        location.stop()
        super.onCleared()
    }
}

internal fun SavedDrive.toDemoTrip() = DemoTrip(
    DriveMode.valueOf(mode), PracticeGoal.valueOf(goal), DemoRoute.valueOf(route), destination, plannedMinutes,
)
