package com.thrive.pleos.driving.location

import kotlin.math.*

internal data class LocationFix(
    val latitude: Double?, val longitude: Double?, val sourceTimestampMs: Long?,
    val receivedElapsedMs: Long, val receivedEpochMs: Long, val confidence: Double? = null,
)

internal data class TrackSummary(
    val distanceMeters: Double? = null,
    val accepted: Int = 0,
    val rejected: Int = 0,
    val gaps: Int = 0,
)

internal enum class FixQuality { Accepted, Missing, InvalidCoordinates, MissingTime, OutOfOrder, Duplicate, Stale, Jump }
internal data class TrackedFix(val fix: LocationFix, val quality: FixQuality, val summary: TrackSummary)

/** Conservative trace estimate, not an odometer or driving-safety assessment. */
internal class LocationTrack(initial: TrackSummary = TrackSummary()) {
    var summary = initial
        private set
    private var anchor: LocationFix? = null

    fun breakSegment() {
        anchor = null
        summary = summary.copy(gaps = summary.gaps + 1)
    }

    fun accept(fix: LocationFix, nowElapsedMs: Long): TrackedFix {
        fun reject(quality: FixQuality, breakSegment: Boolean = false): TrackedFix {
            if (breakSegment) anchor = null
            summary = summary.copy(rejected = summary.rejected + 1, gaps = summary.gaps + if (breakSegment) 1 else 0)
            return TrackedFix(fix, quality, summary)
        }
        val latitude = fix.latitude ?: return reject(FixQuality.Missing, true)
        val longitude = fix.longitude ?: return reject(FixQuality.Missing, true)
        if (!validCoordinates(latitude, longitude)) return reject(FixQuality.InvalidCoordinates, true)
        val timestamp = fix.sourceTimestampMs ?: return reject(FixQuality.MissingTime, true)
        if (timestamp <= 0) return reject(FixQuality.MissingTime, true)
        if (nowElapsedMs < fix.receivedElapsedMs || nowElapsedMs - fix.receivedElapsedMs > MAX_RECEIPT_AGE_MS) return reject(FixQuality.Stale, true)
        val previous = anchor
        if (previous != null) {
            if (timestamp == previous.sourceTimestampMs) return reject(FixQuality.Duplicate)
            val sourceDelta = timestamp - requireNotNull(previous.sourceTimestampMs)
            val receiptDelta = fix.receivedElapsedMs - previous.receivedElapsedMs
            if (sourceDelta <= 0 || receiptDelta <= 0) return reject(FixQuality.OutOfOrder)
            if (sourceDelta > MAX_SEGMENT_GAP_MS || receiptDelta > MAX_SEGMENT_GAP_MS) {
                summary = summary.copy(gaps = summary.gaps + 1)
            } else {
                val meters = haversine(requireNotNull(previous.latitude), requireNotNull(previous.longitude), latitude, longitude)
                if (meters / (sourceDelta / 1000.0) > MAX_PLAUSIBLE_METERS_PER_SECOND) return reject(FixQuality.Jump)
                // Suppress very small single-step jitter. This remains an estimate with documented limits.
                summary = summary.copy(distanceMeters = (summary.distanceMeters ?: 0.0) + if (meters >= MIN_STEP_METERS) meters else 0.0)
            }
        }
        anchor = fix
        summary = summary.copy(accepted = summary.accepted + 1)
        return TrackedFix(fix, FixQuality.Accepted, summary)
    }

    companion object {
        const val MAX_SEGMENT_GAP_MS = 15_000L
        const val MAX_RECEIPT_AGE_MS = 5_000L
        const val MAX_PLAUSIBLE_METERS_PER_SECOND = 60.0
        const val MIN_STEP_METERS = 3.0

        fun validCoordinates(latitude: Double, longitude: Double) =
            latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

        private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val latDelta = Math.toRadians(lat2 - lat1)
            val lonDelta = Math.toRadians(lon2 - lon1)
            val a = sin(latDelta / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(lonDelta / 2).pow(2)
            return 6_371_000.0 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        }
    }
}
