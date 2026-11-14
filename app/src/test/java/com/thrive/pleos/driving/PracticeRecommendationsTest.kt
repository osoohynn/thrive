package com.thrive.pleos.driving

import com.thrive.pleos.ui.driving.*
import org.junit.Assert.*
import org.junit.Test

class PracticeRecommendationsTest {
    @Test fun turnsPreferFewerLaneChangesEvenWhenSlower() {
        val result = recommendPractice(PracticeRequest(PracticeGoal.Turns, 30, false))
        assertEquals(listOf(DemoRoute.Gentle, DemoRoute.Direct), result.map { it.route })
    }
    @Test fun lanePracticeRequiresEnoughExampleChanges() {
        val result = recommendPractice(PracticeRequest(PracticeGoal.Lanes, 30, false))
        assertEquals(listOf(DemoRoute.Direct), result.map { it.route })
    }
    @Test fun durationIsAHardLimitAndEqualityIsAllowed() {
        assertTrue(recommendPractice(PracticeRequest(PracticeGoal.Turns, 15, false)).isEmpty())
        assertEquals(listOf(DemoRoute.Direct), recommendPractice(PracticeRequest(PracticeGoal.Turns, 19, false)).map { it.route })
    }
    @Test fun conflictingGoalAndLaneLimitDoNotInventAFallback() {
        assertTrue(recommendPractice(PracticeRequest(PracticeGoal.Lanes, 45, true)).isEmpty())
        assertEquals(listOf(DemoRoute.Gentle), recommendPractice(PracticeRequest(PracticeGoal.Turns, 30, true)).map { it.route })
    }
    @Test fun invalidDurationAndDestinationGoalReturnNoPracticeCourses() {
        assertTrue(recommendPractice(PracticeRequest(PracticeGoal.Turns, 0, false)).isEmpty())
        assertTrue(recommendPractice(PracticeRequest(PracticeGoal.Daily, 30, false)).isEmpty())
    }
    @Test fun absentCatalogDoesNotFallbackAndDuplicateCandidatesAreRemoved() {
        val request = PracticeRequest(PracticeGoal.Turns, 30, false)
        assertTrue(recommendPractice(request, emptyList()).isEmpty())
        assertEquals(1, recommendPractice(request, listOf(DemoRoute.Gentle, DemoRoute.Gentle)).size)
    }
    @Test fun selectedTripUsesCatalogDurationAndRejectsExcludedRoute() {
        val request = PracticeRequest(PracticeGoal.Lanes, 45, false)
        val trip = checkNotNull(practiceTrip(request, DemoRoute.Direct))
        assertEquals(19, trip.minutes)
        assertEquals("5.9", trip.distance)
        assertEquals(PracticeGoal.Lanes, trip.goal)
        assertNull(practiceTrip(request, DemoRoute.Gentle))
    }
}
