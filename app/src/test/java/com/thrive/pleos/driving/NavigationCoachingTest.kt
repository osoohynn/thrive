package com.thrive.pleos.driving

import com.thrive.pleos.driving.coaching.*
import com.thrive.pleos.driving.navigation.*
import com.thrive.pleos.driving.storage.*
import com.thrive.pleos.ui.driving.*
import org.junit.Assert.*
import org.junit.Test

class NavigationCoachingTest {
    private fun event(kind: NavigationEventKind = NavigationEventKind.TbtReceived, at: Long = 1_000,
        type: String? = "TURN_LEFT", detail: String = "좌회전 안내") = NavigationObservation(kind, at, 100_000, detail, type)
    @Test fun duplicateSdkCompletionCallbacksAreSuppressedButLaterEventsRemain() {
        val gate = NavigationEventGate()
        assertTrue(gate.accept(event(NavigationEventKind.RouteCalculated)))
        assertFalse(gate.accept(event(NavigationEventKind.RouteCalculated, 1_001)))
        assertTrue(gate.accept(event(NavigationEventKind.RouteCalculated, 2_501)))
    }
    @Test fun routeChangesInvalidateOldTbtDeduplication() {
        val gate = NavigationEventGate()
        assertTrue(gate.accept(event()))
        assertFalse(gate.accept(event(at = 5_000)))
        assertTrue(gate.accept(event(NavigationEventKind.Rerouted, 5_001)))
        assertTrue(gate.accept(event(at = 5_002)))
    }
    @Test fun tbtMustMatchSelectedGoalAndUnknownDirectionsAreNotHints() {
        val queue = CoachingQueue()
        queue.offer(event(type = "TURN_STRAIGHT"), PracticeGoal.Turns, 1_000)
        assertNull(queue.state(1_000).candidate)
        queue.offer(event(), PracticeGoal.Turns, 1_000)
        assertNotNull(queue.state(1_000).candidate)
        val lanes = CoachingQueue()
        lanes.offer(event(type = "TURN_KEEP_L"), PracticeGoal.Lanes, 1_000)
        assertNotNull(lanes.state(1_000).candidate)
        val unknown = CoachingQueue()
        unknown.offer(event(type = "TURN_UNKNOWN"), PracticeGoal.Daily, 1_000)
        assertNull(unknown.state(1_000).candidate)
    }
    @Test fun staleAndFutureHintsAreDiscardedAndPendingHintsExpire() {
        val queue = CoachingQueue()
        queue.offer(event(), PracticeGoal.Turns, 6_001)
        assertNull(queue.state(6_001).candidate)
        queue.offer(event(at = 7_000), PracticeGoal.Turns, 6_001)
        assertNull(queue.state(6_001).candidate)
        queue.offer(event(at = 7_000), PracticeGoal.Turns, 7_001)
        assertNotNull(queue.state(12_000).candidate)
        assertNull(queue.state(12_001).candidate)
    }
    @Test fun muteRouteCancellationAndResetClearPendingWithoutReplayingIt() {
        val queue = CoachingQueue()
        queue.offer(event(), PracticeGoal.Turns, 1_000)
        queue.setEnabled(false); assertNull(queue.state(1_001).candidate)
        queue.setEnabled(true); assertNull(queue.state(1_002).candidate)
        queue.offer(event(at = 2_000), PracticeGoal.Turns, 2_000)
        assertNull(queue.state(2_000).candidate)
        queue.reset(); queue.offer(event(at = 3_000), PracticeGoal.Turns, 3_000)
        assertNotNull(queue.state(3_000).candidate)
        queue.offer(event(NavigationEventKind.RouteCancelled, 3_001), PracticeGoal.Turns, 3_001)
        assertNull(queue.state(3_001).candidate)
    }
    private val record = SavedDrive("record", source = "SDK", mode = "Destination", goal = "Daily", route = "NATIVE",
        destination = "목적지", plannedMinutes = 20, startedAtEpochMs = 1, status = "Completed", activeSlot = null, feeling = "부담됐어요")
    private fun saved(id: String = "record", source: String = "SDK") = SavedNavigationEvent(eventKey = "event", sessionId = id,
        kind = "Rerouted", source = source, elapsedMs = 1_000, receivedEpochMs = 1, detail = "")
    @Test fun reviewUsesOnlyMatchingSdkEventsAndDoesNotAssessDriverSkill() {
        assertNull(navigationReviewContext(record, listOf(saved(id = "other"), saved(source = "DEMO"))))
        assertTrue(navigationReviewContext(record, listOf(saved()))!!.contains("재탐색 1회"))
        val plan = nextPracticePlan(record, listOf(saved())) as NextPracticePlan.Destination
        assertTrue(plan.reason.contains("재탐색 1회"))
        assertTrue(plan.reason.contains("부담됐어요"))
        assertFalse(plan.reason.contains("실수"))
        assertEquals(nextPracticePlan(record), nextPracticePlan(record, emptyList()))
    }
}
