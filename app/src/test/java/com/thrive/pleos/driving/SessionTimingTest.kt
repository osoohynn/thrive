package com.thrive.pleos.driving

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionTimingTest {
    @Test fun countsOnlyMonotonicRecordedTime() {
        val clock = SessionTiming()
        clock.start(0, 1000)
        assertEquals(2500L, clock.elapsed(3500))
        assertEquals(2500L, clock.stop(3500))
        assertEquals(2500L, clock.elapsed(50000))
    }

    @Test fun recoveryDoesNotCountTimeWhileAppWasStopped() {
        val recovered = SessionTiming()
        recovered.start(5000, 90000)
        assertEquals(6000L, recovered.elapsed(91000))
    }

    @Test fun clockResetAndNegativeSavedValuesNeverReduceElapsed() {
        val clock = SessionTiming()
        clock.start(-1, 5000)
        assertEquals(0L, clock.elapsed(2000))
        assertEquals(1000L, clock.elapsed(6000))
    }
}
