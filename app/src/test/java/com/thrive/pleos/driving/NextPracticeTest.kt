package com.thrive.pleos.driving

import com.thrive.pleos.driving.storage.SavedDrive
import com.thrive.pleos.ui.driving.*
import org.junit.Assert.*
import org.junit.Test

class NextPracticeTest {
    private val completed = SavedDrive("review", mode = "Practice", goal = "Turns", route = "Direct",
        destination = "출발지", plannedMinutes = 45, startedAtEpochMs = 1, status = "Completed", activeSlot = null)

    @Test fun missingResponseAndUnfinishedRecordCannotProduceRecommendation() {
        assertNull(nextPracticePlan(completed))
        assertNull(nextPracticePlan(completed.copy(feeling = "unknown")))
        assertNull(nextPracticePlan(completed.copy(feeling = "보통이에요", status = "Recording", activeSlot = 1)))
    }
    @Test fun burdenedTurnPracticeKeepsGoalAndAppliesExplicitLaneLimit() {
        val plan = nextPracticePlan(completed.copy(feeling = "부담됐어요")) as NextPracticePlan.Practice
        assertEquals(PracticeRequest(PracticeGoal.Turns, 30, true), plan.request)
        assertEquals(DemoRoute.Gentle, plan.preferredRoute)
        assertTrue(plan.reason.contains("부담됐어요"))
    }
    @Test fun lanePracticeDoesNotGetContradictoryLaneLimit() {
        val plan = nextPracticePlan(completed.copy(goal = "Lanes", feeling = "부담됐어요")) as NextPracticePlan.Practice
        assertFalse(plan.request.minimizeLaneChanges)
        assertEquals(PracticeGoal.Lanes, plan.request.goal)
        assertEquals(DemoRoute.Direct, plan.preferredRoute)
    }
    @Test fun comfortableResponsePreservesChosenRouteWithoutAutomaticDifficultyIncrease() {
        val plan = nextPracticePlan(completed.copy(feeling = "여유 있었어요")) as NextPracticePlan.Practice
        assertEquals(45, plan.request.maxMinutes)
        assertEquals(PracticeGoal.Turns, plan.request.goal)
        assertEquals(DemoRoute.Direct, plan.preferredRoute)
    }
    @Test fun oldShortBudgetDoesNotInventCandidate() {
        val plan = nextPracticePlan(completed.copy(plannedMinutes = 15, feeling = "보통이에요")) as NextPracticePlan.Practice
        assertEquals(15, plan.request.maxMinutes)
        assertNull(plan.preferredRoute)
    }
    @Test fun nativeRecordReturnsToNativeLookupRegardlessOfLocationQuality() {
        val sdk = completed.copy(source = "SDK", route = "NATIVE", plannedMinutes = 0, destination = "저장된 목적지", feeling = "부담됐어요")
        val plan = nextPracticePlan(sdk) as NextPracticePlan.Destination
        assertTrue(plan.native)
        assertEquals(sdk.destination, plan.name)
        assertEquals(plan, nextPracticePlan(sdk.copy(acceptedFixes = 20, rejectedFixes = 100, gapCount = 5)))
    }
    @Test fun demoDestinationStaysSeparateAndInvalidInputsDoNotFallBackToExampleGoal() {
        val plan = nextPracticePlan(completed.copy(mode = "Destination", feeling = "보통이에요")) as NextPracticePlan.Destination
        assertFalse(plan.native)
        assertNull(nextPracticePlan(completed.copy(goal = "unknown", feeling = "보통이에요")))
        assertNull(nextPracticePlan(completed.copy(source = "unknown", feeling = "보통이에요")))
        assertNull(nextPracticePlan(completed.copy(plannedMinutes = 0, feeling = "보통이에요")))
    }
}
