package com.thrive.pleos.driving.navigation

internal enum class NavigationEventKind(val title: String) {
    RouteSearchStarted("내비 경로 계산 시작"), RouteCalculated("내비 경로 계산 완료"),
    Rerouted("내비 재탐색 완료"), DestinationChanged("내비 목적지 변경"),
    RouteCancelled("내비 경로 취소"), DestinationArrived("내비 도착 신호"),
    TbtReceived("내비 구간 안내 수신"), NavigationError("내비 연결·응답 오류"),
    RecordingStarted("기록 시작"), RecordingResumed("중단 기록 복구"),
}

internal data class NavigationObservation(val kind: NavigationEventKind, val receivedElapsedMs: Long,
    val receivedEpochMs: Long, val detail: String = "", val turnType: String? = null)

/** SDK may issue both deprecated and current completion callbacks for the same update. */
internal class NavigationEventGate {
    private val last = linkedMapOf<String, Long>()
    fun accept(event: NavigationObservation): Boolean {
        val key = "${event.kind}:${event.turnType}:${event.detail}"
        val previous = last[key]
        val limit = if (event.kind == NavigationEventKind.TbtReceived) 30_000 else 1_500
        if (previous != null && event.receivedElapsedMs - previous in 0 until limit) return false
        if (event.kind in setOf(NavigationEventKind.RouteSearchStarted, NavigationEventKind.Rerouted,
                NavigationEventKind.DestinationChanged, NavigationEventKind.RouteCancelled)) last.clear()
        last[key] = event.receivedElapsedMs
        while (last.size > 64) last.remove(last.keys.first())
        return true
    }
    fun clear() = last.clear()
}
