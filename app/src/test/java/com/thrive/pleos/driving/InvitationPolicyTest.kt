package com.thrive.pleos.driving

import com.thrive.pleos.driving.invitation.*
import com.thrive.pleos.driving.navigation.*
import org.junit.Assert.*
import org.junit.Test

class InvitationPolicyTest {
    private val destination = LiveDestination("연습 목적지", 37.5, 127.0, poiId = "place", poiProvider = "native")
    private val now = 10_000L
    private val epoch = 1_700_000_000_000L
    private val nav = NavigationState(connected = true, destination = destination, routeMode = "ROUTE_GUIDING",
        confirmedAtElapsedMs = now, routeConfirmedAtMs = now)
    private val park = ParkState("GEAR_PARK", now)
    private fun candidate(navigation: NavigationState = nav, parking: ParkState = park, enabled: Boolean = true,
        active: Boolean = false, visible: Boolean = false, allowed: Boolean = true, history: Set<String> = emptySet()) =
        invitationCandidate(navigation, parking, enabled, active, visible, allowed, history, now, epoch)

    @Test fun onlyFreshParkAndRealGuidanceCanProduceAnOffer() {
        assertEquals(destination, candidate())
        for (gear in listOf(null, "GEAR_UNKNOWN", "GEAR_DRIVE", "GEAR_NEUTRAL", "GEAR_REVERSE")) assertNull(candidate(parking = park.copy(gear = gear)))
        assertNull(candidate(parking = park.copy(receivedAtMs = now - 5_001)))
        assertNull(candidate(parking = park.copy(receivedAtMs = now + 1)))
        assertNull(candidate(navigation = nav.copy(routeMode = "SIMULATION_GUIDING")))
        assertNull(candidate(navigation = nav.copy(confirmedAtElapsedMs = now - 30_001)))
    }
    @Test fun userConsentPermissionsVisibilityAndActiveRecordGateDelivery() {
        assertNull(candidate(enabled = false)); assertNull(candidate(active = true))
        assertNull(candidate(visible = true)); assertNull(candidate(allowed = false))
        assertNull(candidate(navigation = nav.copy(querying = true)))
        assertNull(candidate(navigation = nav.copy(destination = destination.copy(latitude = Double.NaN))))
    }
    @Test fun poiEntranceCoordinateAndNameChangesDoNotBypassSuppression() {
        val key = invitationKey(destination)
        assertEquals(64, key.length)
        assertEquals(key, invitationKey(destination.copy(name = "입구", latitude = 37.501)))
        assertNotEquals(key, invitationKey(destination.copy(poiProvider = "other")))
    }
    @Test fun sameDestinationIsSuppressedForOneDayAndOtherPlacesForOneMinute() {
        val key = invitationKey(destination)
        assertNull(candidate(history = setOf("${epoch - 60_001}:$key")))
        assertNotNull(candidate(history = setOf("${epoch - INVITATION_DAY_MS - 1}:$key")))
        val other = invitationKey(destination.copy(poiId = "other"))
        assertNull(candidate(history = setOf("${epoch - 59_999}:$other")))
        assertNotNull(candidate(history = setOf("${epoch - 60_000}:$other")))
    }
    @Test fun clockRollbackDoesNotSpamAndInvalidHistoryIsIgnored() {
        val key = invitationKey(destination)
        assertTrue(invitationWasSeen(key, setOf("${epoch + 60_000}:$key"), epoch))
        assertFalse(invitationWasSeen(key, setOf("garbage", "bad:$key", "-1:$key", "0:invalid"), epoch))
    }
    @Test fun pendingOfferCannotOutliveItsTwoMinuteWindow() {
        val item = PracticeInvitation("token", destination, now)
        assertTrue(item.validAt(now)); assertTrue(item.validAt(now + INVITATION_LIFETIME_MS - 1))
        assertFalse(item.validAt(now - 1)); assertFalse(item.validAt(now + INVITATION_LIFETIME_MS))
    }
}
