package com.thrive.pleos.driving

import com.thrive.pleos.driving.voice.VoiceRequestGate
import org.junit.Assert.*
import org.junit.Test

class VoiceRequestGateTest {
    @Test fun mutedRequestsNeverStart() {
        val gate = VoiceRequestGate()
        assertNull(gate.begin("goal", 0, false))
        assertNotNull(gate.begin("goal", 0, true))
    }
    @Test fun inFlightRequestCannotBeQueuedOrReplaced() {
        val gate = VoiceRequestGate()
        val token = checkNotNull(gate.begin("one", 0, true))
        assertNull(gate.begin("two", 20_000, true))
        assertTrue(gate.isCurrent(token))
    }
    @Test fun cancelledCallbackCannotCompleteNewOwner() {
        val gate = VoiceRequestGate()
        val old = checkNotNull(gate.begin("old-session", 0, true))
        gate.cancel()
        val current = checkNotNull(gate.begin("new-session", 1, true))
        gate.finish(old)
        assertFalse(gate.isCurrent(old))
        assertTrue(gate.isCurrent(current))
    }
    @Test fun repeatLimitExpiresAtTenSeconds() {
        val gate = VoiceRequestGate()
        gate.finish(checkNotNull(gate.begin("one", 0, true)))
        assertNull(gate.begin("one", 9_999, true))
        assertNotNull(gate.begin("one", 10_000, true))
    }
    @Test fun oldInitializationCannotSpeakAfterFreshnessDeadline() {
        val gate = VoiceRequestGate()
        val token = checkNotNull(gate.begin("one", 100, true))
        assertTrue(gate.isFresh(token, 5_100))
        assertFalse(gate.isFresh(token, 5_101))
        assertFalse(gate.isFresh(token, 99))
        gate.cancel()
        assertFalse(gate.isFresh(token, 101))
    }
}
