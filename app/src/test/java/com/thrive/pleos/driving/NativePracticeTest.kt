package com.thrive.pleos.driving

import com.thrive.pleos.driving.navigation.*
import com.thrive.pleos.driving.storage.SavedDrive
import com.thrive.pleos.ui.driving.*
import org.junit.Assert.*
import org.junit.Test

class NativePracticeTest {
    private val stop = LiveDestination("목적지", 37.5, 127.0)
    private val origin = LiveDestination("출발지로 돌아오기", 37.4, 127.1)
    private val plan = NativePracticePlan(PracticeGoal.Turns, 30, stop, origin)
    private val estimate = NativeRouteEstimate(1200, 8000)
    private fun ready() = NavigationState(connected = true, destination = origin, routeMode = "ROUTE_GUIDING",
        routeOption = "COMFORTABLE", estimate = estimate, confirmedAtElapsedMs = 1000, routeConfirmedAtMs = 1000,
        waypoints = listOf(stop), course = NativeCourseState(plan, 500, CoursePhase.Ready, estimate = estimate, routingCompleted = true))

    @Test fun sdkPoiIdentityCanMatchEntranceCoordinatesButPlaceholderCannot() {
        val point = stop.copy(poiId = "sdk-place", poiProvider = "native")
        assertTrue(point.samePlace(point.copy(latitude = point.latitude + 0.001)))
        assertFalse(point.samePlace(point.copy(latitude = point.latitude + 0.001, poiProvider = "other")))
        assertFalse(stop.copy(poiId = "0").samePlace(stop.copy(poiId = "0", latitude = stop.latitude + 0.001)))
    }
    @Test fun roundTripMustHaveADistinctValidStopAndOrigin() {
        assertTrue(plan.valid)
        assertFalse(plan.copy(returnPoint = stop).valid)
        assertFalse(plan.copy(stop = stop.copy(latitude = Double.NaN)).valid)
        assertFalse(plan.copy(option = "unknown").valid)
    }
    @Test fun budgetUsesSecondsAndDoesNotRoundDownBeforeValidation() {
        assertTrue(NativeRouteEstimate(1800, 1).fits(30))
        assertFalse(NativeRouteEstimate(1801, 1).fits(30))
        assertFalse(NativeRouteEstimate(0, 1).fits(30))
        assertFalse(NativeRouteEstimate(Int.MAX_VALUE, 1).fits(120))
        assertEquals(31, NativeRouteEstimate(1801, 1).minutes)
    }
    @Test fun aReadyRoundTripMatchesRouteDestinationOptionAndWaypoint() {
        assertNotNull(ready().confirmedCourse(plan, 2000))
        assertNull(ready().copy(waypoints = emptyList()).confirmedCourse(plan, 2000))
        assertNull(ready().copy(routeOption = "RECOMMENDED").confirmedCourse(plan, 2000))
        assertNull(ready().copy(destination = stop).confirmedCourse(plan, 2000))
    }
    @Test fun staleOrChangedEstimateDoesNotStartCourse() {
        assertNull(ready().confirmedCourse(plan, 32_000))
        assertNull(ready().copy(estimate = NativeRouteEstimate(3000, 8000)).confirmedCourse(plan, 2000))
        assertNull(ready().copy(course = ready().course?.copy(routingCompleted = false)).confirmedCourse(plan, 2000))
        assertNull(ready().confirmedCourse(plan.copy(maxMinutes = 15), 2000))
    }
    @Test fun oneWayDoesNotRequireWaypoints() {
        val direct = plan.copy(returnPoint = null)
        val state = ready().copy(destination = stop, waypoints = null,
            course = NativeCourseState(direct, 500, CoursePhase.Ready, estimate = estimate, routingCompleted = true))
        assertNotNull(state.confirmedCourse(direct, 2000))
    }
    @Test fun candidateListRejectsInvalidAndDeduplicatesSdkHistory() {
        val record = SavedDrive("sdk", source = "SDK", mode = "Destination", goal = "Daily", route = "NATIVE",
            destination = stop.name, destinationLatitude = stop.latitude, destinationLongitude = stop.longitude,
            plannedMinutes = 0, startedAtEpochMs = 1)
        val state = NavigationState(destination = stop, recentDestinations = listOf(stop.copy(name = ""), origin))
        assertEquals(listOf(stop), nativePracticeCandidates(state, listOf(record, record.copy(source = "DEMO"))))
    }
    @Test fun sdkPracticeNextStepPreservesGoalAndRealStop() {
        val record = SavedDrive("sdk", source = "SDK", mode = "Practice", goal = "Lanes", route = "NATIVE",
            destination = origin.name, plannedMinutes = 20, startedAtEpochMs = 1, status = "Completed", activeSlot = null,
            practiceStopName = stop.name, practiceStopLatitude = stop.latitude, practiceStopLongitude = stop.longitude,
            practiceReturn = true, practiceMaxMinutes = 45, feeling = "부담됐어요")
        val next = nextPracticePlan(record) as NextPracticePlan.NativePractice
        assertEquals(PracticeGoal.Lanes, next.goal)
        assertEquals(30, next.maxMinutes)
        assertTrue(next.roundTrip)
        assertTrue(next.stop.samePlace(stop))
    }
}
