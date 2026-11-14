package com.thrive.pleos.driving.voice

/** No queued playback: one owner/request at a time, with a short repeat limit. */
internal class VoiceRequestGate {
    private var nextToken = 0L
    private var active: Long? = null
    private var lastKey: String? = null
    private var requestedAt = 0L

    fun begin(key: String, nowMs: Long, enabled: Boolean): Long? {
        if (!enabled || active != null) return null
        if (key == lastKey && nowMs - requestedAt in 0 until REPEAT_LIMIT_MS) return null
        lastKey = key
        requestedAt = nowMs
        return (++nextToken).also { active = it }
    }

    fun isCurrent(token: Long): Boolean = active == token
    fun isFresh(token: Long, nowMs: Long): Boolean = isCurrent(token) && nowMs - requestedAt in 0..MAX_PENDING_MS
    fun finish(token: Long) { if (isCurrent(token)) active = null }
    fun cancel() { active = null }

    companion object {
        const val REPEAT_LIMIT_MS = 10_000L
        const val MAX_PENDING_MS = 5_000L
    }
}
