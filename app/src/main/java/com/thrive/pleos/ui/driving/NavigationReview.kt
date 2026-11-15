package com.thrive.pleos.ui.driving

import com.thrive.pleos.driving.navigation.NavigationEventKind
import com.thrive.pleos.driving.storage.SavedDrive
import com.thrive.pleos.driving.storage.SavedNavigationEvent

internal data class NavigationTimeline(val sessionId: String? = null, val loaded: Boolean = false,
    val events: List<SavedNavigationEvent> = emptyList(), val error: String? = null)

internal fun navigationReviewContext(record: SavedDrive, events: List<SavedNavigationEvent>): String? {
    val facts = events.filter { it.sessionId == record.id && it.source == "SDK" }
    val reroutes = facts.count { it.kind == NavigationEventKind.Rerouted.name }
    val changes = facts.count { it.kind == NavigationEventKind.DestinationChanged.name }
    val cancels = facts.count { it.kind == NavigationEventKind.RouteCancelled.name }
    return when {
        reroutes > 0 || changes > 0 -> "이 기록에서 내비 재탐색 ${reroutes}회·목적지 변경 ${changes}회 신호를 받았어요. 출발 전 경로를 다시 살펴봐요."
        cancels > 0 -> "이 기록에서 내비 경로 취소 신호 ${cancels}회를 받았어요. 다음 출발 전에 목적지와 경로를 다시 확인해요."
        facts.any { it.kind == NavigationEventKind.DestinationArrived.name } -> "이 기록에서 내비 도착 신호를 받았어요. 다음 연습에서도 목적지를 새로 확인해요."
        else -> null
    }
}
internal val SavedNavigationEvent.title: String get() = NavigationEventKind.entries.firstOrNull { it.name == kind }?.title ?: "내비 사건 수신"
internal val SavedNavigationEvent.timeLabel: String get() = "${elapsedMs / 60_000}분 ${(elapsedMs / 1000) % 60}초"
