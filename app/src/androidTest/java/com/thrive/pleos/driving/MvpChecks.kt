package com.thrive.pleos.driving

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import androidx.room.Room
import com.thrive.pleos.MainActivity
import com.thrive.pleos.driving.invitation.PracticeInvitations
import com.thrive.pleos.driving.navigation.*
import com.thrive.pleos.driving.recording.DriveRecorder
import com.thrive.pleos.driving.storage.*
import com.thrive.pleos.ui.driving.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.UUID

internal suspend fun Instrumentation.liveNavigation(): NavigationConnection {
    check(targetContext.packageName.endsWith(".verification"))
    val navigation = withContext(Dispatchers.Main) { PracticeInvitations.get(targetContext).navigation }
    withContext(Dispatchers.Main) { navigation.queryDestination() }
    withTimeout(12_000) { navigation.state.first { it.connected && !it.querying } }
    if (navigation.state.value.freshDestination(SystemClock.elapsedRealtime()) == null) {
        val previous = DriveDatabase.get(targetContext).drives().observeAll().first().firstOrNull {
            it.source == "SDK" && it.destinationLatitude != null && it.destinationLongitude != null
        } ?: error("먼저 기본 내비에서 목적지를 설정해 주세요.")
        val recent = withTimeoutOrNull(2_000) { navigation.state.first { it.recentDestinations.isNotEmpty() } }?.recentDestinations?.firstOrNull()
        val target = recent ?: LiveDestination(previous.destination, previous.destinationLatitude!!, previous.destinationLongitude!!)
        val plan = NativePracticePlan(PracticeGoal.Daily, 120, target, option = "RECOMMENDED")
        withContext(Dispatchers.Main) { navigation.requestPractice(plan) }
        val ready = withTimeout(35_000) { navigation.state.first { it.course?.phase in setOf(CoursePhase.Ready, CoursePhase.Failed) } }
        check(ready.freshDestination(SystemClock.elapsedRealtime()) != null) { "Initial route failed: ${ready.course?.message}" }
    }
    return navigation
}
internal fun Instrumentation.openActivity() = startActivitySync(Intent(targetContext, MainActivity::class.java)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
private suspend fun restoreRoute(navigation: NavigationConnection, place: LiveDestination) {
    withContext(Dispatchers.Main) { navigation.connect() }
    withTimeout(12_000) { navigation.state.first { it.connected && !it.querying } }
    withTimeout(10_000) { navigation.state.first { it.connected && !it.querying } }
    val plan = NativePracticePlan(PracticeGoal.Daily, 120, place, option = "RECOMMENDED")
    withContext(Dispatchers.Main) { navigation.requestPractice(plan) }
    val restored = withTimeout(35_000) { navigation.state.first { it.course?.phase in setOf(CoursePhase.Ready, CoursePhase.Failed) } }
    check(restored.confirmedCourse(plan, SystemClock.elapsedRealtime()) != null) { "Original route restoration failed: ${restored.course?.message}" }
}
internal fun proposed(snapshot: NavigationState, practice: NativePracticePlan? = null): SavedDrive {
    val target = checkNotNull(snapshot.destination)
    return SavedDrive(UUID.randomUUID().toString(), source = "SDK", mode = if (practice == null) "Destination" else "Practice",
        goal = practice?.goal?.name ?: "Daily", route = "NATIVE", destination = target.name,
        destinationLatitude = target.latitude, destinationLongitude = target.longitude,
        plannedMinutes = snapshot.estimate?.minutes ?: 0, estimatedSeconds = snapshot.estimate?.seconds,
        plannedDistanceMeters = snapshot.estimate?.meters?.toDouble(), routeOption = snapshot.routeOption,
        startedAtEpochMs = System.currentTimeMillis(), environment = "EMULATOR", practiceStopName = practice?.stop?.name,
        practiceStopLatitude = practice?.stop?.latitude, practiceStopLongitude = practice?.stop?.longitude,
        practiceReturn = practice?.roundTrip ?: false, practiceMaxMinutes = practice?.maxMinutes)
}

internal suspend fun checkMvpEvents(test: Instrumentation): String {
    val activity = test.openActivity()
    val context = test.targetContext
    val navigation = test.liveNavigation()
    val original = checkNotNull(navigation.state.value.destination)
    val database = DriveDatabase.get(context)
    val dao = database.drives()
    val recorder = DriveRecorder.get(context)
    recorder.awaitReady(); check(dao.active() == null)
    val first = recorder.start(proposed(navigation.state.value))
    val temporary = original.copy(name = "사건 수신 검증 지점", latitude = original.latitude + 0.003,
        longitude = original.longitude + 0.002, poiId = "", poiProvider = "", poiSubId = "0")
    try {
        withContext(Dispatchers.IO) { database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER mvp_fail_navigation BEFORE INSERT ON navigation_events WHEN NEW.sessionId = '${first.id}' BEGIN SELECT RAISE(ABORT, 'mvp fault injection'); END") }
        try {
            withTimeout(10_000) { navigation.state.first { it.connected && !it.querying } }
            withContext(Dispatchers.Main) { navigation.requestPractice(NativePracticePlan(PracticeGoal.Daily, 120, temporary, option = "RECOMMENDED")) }
            val changed = withTimeout(35_000) { navigation.state.first { it.course?.phase in setOf(CoursePhase.Ready, CoursePhase.Failed) } }
            check(changed.freshDestination(SystemClock.elapsedRealtime())?.samePlace(original) == false) { "Temporary route failed: ${changed.course?.message}" }
            withTimeout(8_000) { recorder.state.first { it.error?.contains("내비 사건") == true } }
        } finally {
            withContext(Dispatchers.IO) { database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS mvp_fail_navigation") }
        }
        test.runOnMainSync { activity.finish() }; test.waitForIdleSync()
        withContext(Dispatchers.Main) { navigation.queryDestination() }
        val events = withTimeout(10_000) { dao.observeNavigationEvents(first.id).first { list -> list.any { it.source == "SDK" } } }
        withTimeout(5_000) { recorder.state.first { it.error == null } }
        check(events.all { it.sessionId == first.id && it.elapsedMs >= 0 })
        check(events.map { it.eventKey }.distinct().size == events.size)
        check(events.any { it.kind in setOf("RouteCancelled", "RouteCalculated", "RouteSearchStarted") && it.source == "SDK" })
        val finished = checkNotNull(recorder.finish())
        val count = dao.navigationEventCount(first.id)
        test.openActivity()
        val secondSnapshot = withTimeout(10_000) { navigation.state.first { it.freshDestination(SystemClock.elapsedRealtime()) != null } }
        val second = recorder.start(proposed(secondSnapshot))
        withContext(Dispatchers.Main) { navigation.queryDestination() }
        delay(2_000)
        recorder.finish()
        check(dao.navigationEventCount(first.id) == count) { "Late callbacks entered a finished session" }
        check(dao.navigationEvents(second.id).all { it.sessionId == second.id })
        dao.setFeeling(first.id, "부담됐어요")
        val review = nextPracticePlan(checkNotNull(dao.find(first.id)), dao.navigationEvents(first.id))
        check(review != null && review.reason.contains("부담됐어요"))
        return "PASS: Room v4; real SDK events persisted while Activity absent; injected write failure retried without duplicates; finish flushed; later session isolated; saved facts connected to next practice. events=$count elapsed=${finished.elapsedMs}"
    } finally {
        withContext(Dispatchers.IO) { database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS mvp_fail_navigation") }
        if (dao.active() != null) recorder.finish()
        test.openActivity()
        restoreRoute(navigation, original)
    }
}

internal suspend fun checkRoundTrip(test: Instrumentation): String {
    test.openActivity()
    val navigation = test.liveNavigation()
    val original = checkNotNull(navigation.state.value.destination)
    val origin = withTimeout(8_000) { navigation.state.first { it.freshOrigin(SystemClock.elapsedRealtime()) != null } }.freshOrigin(SystemClock.elapsedRealtime())!!
    val coordinateStop = origin.copy(name = "좌표 경유지 검증 지점", latitude = origin.latitude + 0.003, longitude = origin.longitude + 0.003,
        poiId = "", poiProvider = "", poiSubId = "0")
    val stop = (navigation.state.value.recentDestinations + original).firstOrNull {
        it.poiId.isNotBlank() && it.poiId != "0" && !it.samePlace(origin)
    } ?: error("A real SDK POI is required to check identity when waypoint coordinates are absent")
    val plan = NativePracticePlan(PracticeGoal.Turns, 120, stop, origin, "RECOMMENDED")
    val recorder = DriveRecorder.get(test.targetContext)
    val dao = DriveDatabase.get(test.targetContext).drives()
    check(dao.active() == null)
    try {
        withTimeout(10_000) { navigation.state.first { it.connected && !it.querying } }
        val coordinatePlan = plan.copy(stop = coordinateStop)
        withContext(Dispatchers.Main) { navigation.requestPractice(coordinatePlan) }
        val coordinateResult = withTimeout(35_000) { navigation.state.first { it.course?.phase in setOf(CoursePhase.Ready, CoursePhase.Failed) } }
        if (coordinateResult.course?.phase == CoursePhase.Failed) check(coordinateResult.course?.message?.contains("경유지 위치") == true) { "Unexpected coordinate route failure: ${coordinateResult.course}" }
        withTimeout(10_000) { navigation.state.first { it.connected && !it.querying } }
        withContext(Dispatchers.Main) { navigation.requestPractice(plan) }
        val state = withTimeout(35_000) { navigation.state.first { it.course?.phase in setOf(CoursePhase.Ready, CoursePhase.Failed) } }
        check(state.confirmedCourse(plan, SystemClock.elapsedRealtime()) != null) { "Round trip not ready: ${state.course} waypoints=${state.waypoints} destination=${state.destination} estimate=${state.estimate} mode=${state.routeMode}" }
        val session = recorder.start(proposed(state, plan))
        withContext(Dispatchers.Main) { navigation.queryDestination() }
        delay(2_000)
        val saved = checkNotNull(recorder.finish())
        check(saved.id == session.id && saved.practiceReturn && saved.practiceStopName == stop.name && saved.goal == "Turns")
        check(saved.estimatedSeconds != null && saved.plannedDistanceMeters != null && saved.status == "Completed")
        return "PASS: real SDK POI waypoint round trip confirmed destination, POI identity and time budget; SDK practice persisted goal, stop, return flag, estimate and report fields. Coordinate-only waypoint response=${coordinateResult.course?.phase}; ${coordinateResult.course?.message}"
    } finally {
        if (dao.active() != null) recorder.finish()
        test.openActivity()
        restoreRoute(navigation, original)
    }
}

internal suspend fun checkEventStorageLimits(test: Instrumentation): String {
    check(test.targetContext.packageName.endsWith(".verification"))
    val database = Room.inMemoryDatabaseBuilder(test.targetContext, DriveDatabase::class.java).build()
    try {
        val dao = database.drives()
        val record = SavedDrive("isolated", source = "SDK", mode = "Destination", goal = "Daily", route = "NATIVE",
            destination = "독립 DB 검사", plannedMinutes = 1, startedAtEpochMs = 1)
        dao.insert(record)
        val event = SavedNavigationEvent(eventKey = "unique", sessionId = record.id, kind = "TbtReceived",
            source = "SDK", elapsedMs = 1, receivedEpochMs = 1, detail = "독립 검사 입력")
        dao.appendNavigationEvents(record.id, listOf(event), 0)
        dao.appendNavigationEvents(record.id, listOf(event), 0)
        check(dao.navigationEventCount(record.id) == 1)
        dao.appendNavigationEvents(record.id, (1..2000).map { event.copy(eventKey = "event-$it") }, 0)
        check(dao.navigationEventCount(record.id) == 2000 && dao.find(record.id)?.droppedNavigationEvents == 1)
        check(dao.deleteFinished(record.id) == 0)
        dao.finishOnce(record.id, 2, 1)
        check(dao.deleteFinished(record.id) == 1 && dao.navigationEventCount(record.id) == 0)
        return "PASS: isolated Room storage deduplicates retries, caps journal at 2000 with visible loss count, protects active session and cascades deletion."
    } finally { withContext(Dispatchers.IO) { database.close() } }
}
