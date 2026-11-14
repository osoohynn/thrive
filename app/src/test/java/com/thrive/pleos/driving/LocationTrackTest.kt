package com.thrive.pleos.driving

import com.thrive.pleos.driving.location.*
import org.junit.Assert.*
import org.junit.Test

class LocationTrackTest {
    private fun fix(time: Long = 1000, lat: Double? = 37.0, lon: Double? = 127.0) = LocationFix(lat, lon, time, time, time)

    @Test fun onePointIsNotMeasuredDistance() {
        val track = LocationTrack()
        assertNull(track.accept(fix(), 1000).summary.distanceMeters)
    }
    @Test fun twoStationaryPointsMeasureZero() {
        val track = LocationTrack()
        track.accept(fix(), 1000)
        assertEquals(0.0, track.accept(fix(2000), 2000).summary.distanceMeters!!, 0.001)
    }
    @Test fun plausibleMovementAddsDistance() {
        val track = LocationTrack()
        track.accept(fix(), 1000)
        assertEquals(55.6, track.accept(fix(2000, 37.0005), 2000).summary.distanceMeters!!, 0.2)
    }
    @Test fun jumpDoesNotAddDistance() {
        val track = LocationTrack()
        track.accept(fix(), 1000)
        val result = track.accept(fix(2000, 40.0), 2000)
        assertEquals(FixQuality.Jump, result.quality)
        assertNull(result.summary.distanceMeters)
    }
    @Test fun gapsAreNotBridged() {
        val track = LocationTrack()
        track.accept(fix(), 1000)
        val result = track.accept(fix(20000, 38.0), 20000)
        assertNull(result.summary.distanceMeters)
        assertEquals(1, result.summary.gaps)
    }
    @Test fun missingPositionBreaksTheSegment() {
        val track = LocationTrack()
        track.accept(fix(), 1000)
        assertEquals(FixQuality.Missing, track.accept(fix(2000, null), 2000).quality)
        assertNull(track.accept(fix(3000, 38.0), 3000).summary.distanceMeters)
    }
    @Test fun duplicateAndOutOfOrderPositionsAreExcluded() {
        val track = LocationTrack()
        track.accept(fix(2000), 2000)
        assertEquals(FixQuality.Duplicate, track.accept(fix(2000), 2000).quality)
        assertEquals(FixQuality.OutOfOrder, track.accept(fix(1000), 1000).quality)
    }
    @Test fun staleAndInvalidCoordinatesAreExcluded() {
        assertEquals(FixQuality.Stale, LocationTrack().accept(fix(), 10000).quality)
        assertEquals(FixQuality.InvalidCoordinates, LocationTrack().accept(fix(lat = Double.NaN), 1000).quality)
        assertEquals(FixQuality.InvalidCoordinates, LocationTrack().accept(fix(lat = 91.0), 1000).quality)
    }
    @Test fun resumePreservesDistanceWithoutJoiningToOldPosition() {
        val track = LocationTrack(TrackSummary(distanceMeters = 100.0, accepted = 10))
        assertEquals(100.0, track.accept(fix(lat = 38.0), 1000).summary.distanceMeters!!, 0.001)
    }
    @Test fun explicitOutageBreaksTheSegment() {
        val track = LocationTrack()
        track.accept(fix(), 1000)
        track.breakSegment()
        assertNull(track.accept(fix(2000, 38.0), 2000).summary.distanceMeters)
    }
}
