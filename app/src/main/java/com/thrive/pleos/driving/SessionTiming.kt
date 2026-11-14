package com.thrive.pleos.driving

/** Uses a monotonic clock; stopped-app time is never counted as recorded driving. */
internal class SessionTiming {
    private var baseMs = 0L
    private var anchorMs: Long? = null

    fun start(savedElapsedMs: Long, monotonicNowMs: Long) {
        baseMs = savedElapsedMs.coerceAtLeast(0)
        anchorMs = monotonicNowMs
    }

    fun elapsed(monotonicNowMs: Long): Long = baseMs +
        (anchorMs?.let { (monotonicNowMs - it).coerceAtLeast(0) } ?: 0)

    fun stop(monotonicNowMs: Long): Long {
        val result = elapsed(monotonicNowMs)
        baseMs = result
        anchorMs = null
        return result
    }
}
