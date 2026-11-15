package com.thrive.pleos.ui.driving

import com.thrive.pleos.driving.curriculum.practiceTopic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.thrive.pleos.driving.coaching.CoachingState
import androidx.compose.runtime.Composable
import com.thrive.pleos.driving.storage.SavedDrive
import com.thrive.pleos.ui.theme.ThriveColors
import java.util.Locale

internal val SavedDrive.isSdk: Boolean get() = source == "SDK"
internal val SavedDrive.displayTitle: String get() = practiceTopic(curriculumItemId)?.title ?: if (isSdk) destination else toDemoTrip().title
internal val SavedDrive.sourceLabel: String get() = if (source == "STATIONARY") "정차 연습" else if (!isSdk) "시연 데이터" else if (environment == "EMULATOR") "에뮬레이터 SDK 수신" else "SDK 수신 기록"
internal val SavedDrive.badgeLabel: String get() = when {
    status == "Interrupted" -> "이어갈 기록"
    source == "STATIONARY" -> "정차 연습"
    isSdk -> "실제 기록"
    else -> "시연"
}
internal val SavedDrive.distanceLabel: String get() = distanceMeters?.let { String.format(Locale.getDefault(), "위치 기반 추정 %.2f km", it / 1000) } ?: "거리 미측정"

@Composable
internal fun LiveDriveScreen(record: SavedDrive, elapsedMs: Long, onReconnect: () -> Unit, onFinish: () -> Unit, coaching: CoachingState = CoachingState(),
    onCoaching: (Boolean) -> Unit = {}) {
    PageTitle(record.destination, status = "기록 중", badge = record.badgeLabel)
    if (record.mode == DriveMode.Practice.name) ContentPanel("연습 목표") {
        Text(PracticeGoal.entries.firstOrNull { it.name == record.goal }?.title ?: "목표 확인", style = MaterialTheme.typography.titleMedium)
        Text("${record.practiceStopName.orEmpty()} · ${if (record.practiceReturn) "출발지로 돌아오기" else "목적지까지"}", style = MaterialTheme.typography.bodyLarge)
    }
    ContentPanel("주행 기록") {
        Text("기록 시간 ${elapsedMs / 1000}초", style = MaterialTheme.typography.displaySmall)
        Text(record.distanceLabel, style = MaterialTheme.typography.headlineSmall)
        Text(record.locationMessage, style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
        Text("위치 ${record.acceptedFixes}건 반영 · ${record.rejectedFixes}건 제외 · 공백 ${record.gapCount}회", style = MaterialTheme.typography.bodyMedium)
        SecondaryAction("위치 다시 연결", onReconnect)
    }
    Text("다른 앱을 사용해도 기록을 이어가요. 시스템 강제 종료·재시작 시에는 마지막 저장 지점부터 직접 이어갈 수 있어요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
    PrimaryAction("주행 기록 종료하기", onFinish)
    ContentPanel("이번 연습 포인트") {
        val candidate = coaching.candidate
        Text(candidate?.text ?: coaching.message, style = MaterialTheme.typography.bodyLarge)
        candidate?.let { Text(it.reason, style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary) }
        Text("기본 내비의 안내와 선택 목표로 준비한 문구예요. 자동 음성은 내비·통화 우선순위 검증 후 제공해요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
        SecondaryAction(if (coaching.enabled) "학습 안내 끄기" else "학습 안내 켜기", { onCoaching(!coaching.enabled) })
    }

}

@Composable
internal fun LiveReportSummary(record: SavedDrive, timeline: NavigationTimeline = NavigationTimeline(), onRetry: () -> Unit = {}) {
    var expandedEvents by rememberSaveable(record.id) { mutableStateOf(false) }
    ContentPanel("기록 요약") {
        if (record.mode == DriveMode.Practice.name) {
            Text("목표 · ${PracticeGoal.entries.firstOrNull { it.name == record.goal }?.title.orEmpty()}", style = MaterialTheme.typography.titleSmall)
            Text("연습 목적지 · ${record.practiceStopName.orEmpty()} · ${if (record.practiceReturn) "왕복" else "편도"}", style = MaterialTheme.typography.bodyLarge)
        }
        record.estimatedSeconds?.let { Text("출발 전 예상 ${(it + 59) / 60}분 · 실제 기록 시간과 구분해요.", style = MaterialTheme.typography.bodyMedium) }
        Text("기록 시간 ${record.elapsedMs / 1000}초", style = MaterialTheme.typography.headlineSmall)
        Text(record.distanceLabel, style = MaterialTheme.typography.bodyLarge)
        Text("반영한 위치 ${record.acceptedFixes}건 · 제외한 위치 ${record.rejectedFixes}건", style = MaterialTheme.typography.bodyMedium)
        Text("위치·기록 공백 ${record.gapCount}회", style = MaterialTheme.typography.bodyMedium)
        Text("마지막 위치 상태: ${record.locationMessage}", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
        Text("추정 거리는 수신한 구간만 계산해요. 차량 주행계 거리와 다를 수 있으며, 미수신·불연속 구간을 연결해 채우지 않아요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
        Text("회전·감속·차선 변경은 아직 분석하지 않았어요. 운전 점수나 잘못한 행동을 임의로 만들지 않아요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
    }
    ContentPanel("내비가 보낸 사건") {
        when {
            timeline.sessionId != record.id || !timeline.loaded -> Text("내비 사건을 읽고 있어요.", style = MaterialTheme.typography.bodyMedium)
            timeline.error != null -> {
                Text(timeline.error, style = MaterialTheme.typography.bodyMedium)
                SecondaryAction("내비 사건 다시 읽기", onRetry)
            }
            timeline.events.isEmpty() -> Text("저장된 내비 사건이 없어요. 과거 기록·미수신 구간의 사건을 만들어 채우지 않아요.", style = MaterialTheme.typography.bodyMedium)
            else -> {
                timeline.events.takeLast(if (expandedEvents) 20 else 5).forEach { event ->
                    Text("${event.timeLabel} · ${event.title} · ${if (event.source == "SDK") "내비 수신" else "앱 기록"}", style = MaterialTheme.typography.bodyLarge)
                    if (event.detail.isNotBlank() && event.kind != "NavigationError") Text(event.detail, style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
                }
                if (timeline.events.size > 5) SecondaryAction(if (expandedEvents) "최근 5건만 보기" else "최근 20건까지 보기", { expandedEvents = !expandedEvents })
                Text("전체 ${timeline.events.size}건 중 최근 ${minOf(timeline.events.size, if (expandedEvents) 20 else 5)}건이에요.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("저장 한도로 제외된 내비 사건 ${record.droppedNavigationEvents}건 · 수신하지 못한 신호는 알 수 없어요.", style = MaterialTheme.typography.bodyMedium)
        Text("안내 수신은 실제 회전·감속 수행이나 잘못한 행동을 뜻하지 않아요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
    }

}
