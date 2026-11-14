package com.thrive.pleos.driving

import com.thrive.pleos.driving.navigation.*
import org.junit.Assert.*
import org.junit.Test

class LiveDestinationTest {
    private val destination = LiveDestination("테스트 목적지", 37.5, 127.0)
    private fun ready() = NavigationState(connected = true, destination = destination, routeMode = "ROUTE_GUIDING", confirmedAtElapsedMs = 1000)
    @Test fun confirmedGuidanceAllowsRecording() { assertEquals(destination, ready().freshDestination(2000)) }
    @Test fun staleDestinationDoesNotAllowRecording() { assertNull(ready().freshDestination(32000)) }
    @Test fun noGuidanceOrDisconnectedDoesNotAllowRecording() {
        assertNull(ready().copy(routeMode = "SAFE_GUIDING").freshDestination(2000))
        assertNull(ready().copy(connected = false).freshDestination(2000))
    }
    @Test fun emptyNameAndInvalidCoordinatesDoNotAllowRecording() {
        assertFalse(destination.copy(name = "").valid)
        assertFalse(destination.copy(latitude = 100.0).valid)
    }
}
