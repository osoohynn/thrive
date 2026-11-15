package com.thrive.pleos.ui.driving

import com.thrive.pleos.driving.curriculum.CurriculumTrack
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.thrive.pleos.driving.storage.SavedDrive
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thrive.pleos.ui.theme.*

@Composable
internal fun DrivingScreen(trip: DemoTrip, onArrive: () -> Unit, elapsedMs: Long, guidance: @Composable () -> Unit) {
    PageTitle(trip.title, status = "주행 중", badge = "시연 · 실제 길 안내 아님")
    ResponsivePair(
        first = { modifier -> CourseMap(trip.route, modifier, driving = true, height = 840.dp) },
        second = { modifier -> ContentPanel("", modifier.heightIn(min = 840.dp)) {
            Text("다음 안내 · 예시", style = MaterialTheme.typography.labelSmall, color = ThriveColors.Brand)
            Text("300 m 앞", style = MaterialTheme.typography.displaySmall)
            Text("우회전", style = MaterialTheme.typography.headlineSmall)
            Text("성수이로 방향 · 지도와 거리는 예시입니다.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
            guidance()
            Spacer(Modifier.height(160.dp))
            Text("기록 중 ${elapsedMs / 1000}초 · ${trip.endLabel} · 예시 주행 흐름", style = MaterialTheme.typography.bodySmall, color = ThriveColors.TextSecondary)
            PrimaryAction("시연 기록 종료하기", onArrive)
        } }, firstWeight = 840f, secondWeight = 616f,
    )
}

@Composable
internal fun ReportScreen(
    record: SavedDrive,
    busy: Boolean,
    onFeeling: (String) -> Unit,
    onSuggested: () -> Unit,
    onManual: () -> Unit,
    timeline: NavigationTimeline = NavigationTimeline(),
    onRetryEvidence: () -> Unit = {},
) {
    val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(record.startedAtEpochMs))
    PageTitle("주행 리포트", "$date · ${record.displayTitle}", badge = record.badgeLabel)
    if (record.isSdk) {
        ResponsivePair(
            first = { modifier -> Column(modifier, verticalArrangement = Arrangement.spacedBy(ThriveSpace.Xxl)) { LiveReportSummary(record, timeline, onRetryEvidence) } },
            second = { modifier -> Column(modifier, verticalArrangement = Arrangement.spacedBy(ThriveSpace.Xxl)) {
                ReportReflection(record, busy, onFeeling, onSuggested, onManual, timeline)
            } },
        )
    } else {

        val trip = record.toDemoTrip()
        ResponsivePair(
            first = { modifier ->
                Column(modifier, verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
                    CourseMap(trip.route, height = 300.dp)
                    ContentPanel("") {
                        Text("기록 시간 ${record.elapsedMs / 1000}초", style = MaterialTheme.typography.headlineSmall)
                        Text("거리 미측정 · 위치 센서 미연동", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            second = { modifier ->
                ContentPanel("선택한 연습", modifier) {
                    Text("목표 · ${trip.goal.title}", style = MaterialTheme.typography.titleSmall)
                    Text("예시 경로 · ${trip.route.title}", style = MaterialTheme.typography.bodyLarge)
                    Text("예시 차선 변경 ${trip.route.laneChanges}회 · ${trip.route.kilometers} km", style = MaterialTheme.typography.bodyMedium)
                    Text("예시 경로 정보 · 실제 주행 분석 아님", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
                }
            },
        )
        ReportReflection(record, busy, onFeeling, onSuggested, onManual, timeline)
    }
}

@Composable
private fun ReportReflection(record: SavedDrive, busy: Boolean, onFeeling: (String) -> Unit, onSuggested: () -> Unit, onManual: () -> Unit, timeline: NavigationTimeline) {
    val feeling = record.feeling
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Lg), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Sm),
        itemVerticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("주행 느낌", style = MaterialTheme.typography.titleMedium)
        SaveStatus("feeling:${record.id}", Modifier.size(32.dp))
    }
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Md), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
        drivingFeelings.forEach { label ->
            SelectionChip(label, feeling == label) { onFeeling(label) }
        }
    }
    ContentPanel("다음 연습") {
        val plan = nextPracticePlan(record, timeline.events.takeIf { timeline.sessionId == record.id && timeline.loaded }.orEmpty())
        if (plan == null) {
            Text("주행 느낌 미선택", style = MaterialTheme.typography.bodyLarge)
        } else {
            when (plan) {
                is NextPracticePlan.Practice -> {
                    Text("${plan.request.goal.title} · 최대 ${plan.request.maxMinutes}분", style = MaterialTheme.typography.titleSmall)
                    Text(if (plan.request.minimizeLaneChanges) "차선 변경 최소화 · 예시 변경 구간 1회 이하" else "차선 변경 횟수 제한 없음",
                        style = MaterialTheme.typography.bodyMedium)
                    val route = plan.preferredRoute
                    Text(route?.let { "제안하는 예시 · ${it.title} · ${it.minutes}분" } ?: "현재 조건에 맞는 예시 코스가 없어 조건 조정이 필요해요.",
                        style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
                }
                is NextPracticePlan.NativePractice -> {
                    Text("${plan.goal.title} · 최대 ${plan.maxMinutes}분 · ${if (plan.roundTrip) "왕복" else "편도"}", style = MaterialTheme.typography.titleSmall)
                    Text("연습 목적지 · ${plan.stop.name}", style = MaterialTheme.typography.bodyMedium)
                }
                is NextPracticePlan.Destination -> {
                    Text("지난 목적지 · ${plan.name}", style = MaterialTheme.typography.titleSmall)
                    Text(if (plan.native) "기본 내비의 현재 목적지를 다시 조회해요." else "예시 목적지 선택으로 이어져요.",
                        style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
                }
            }
            Text("선택 목표·느낌 기준 · 운전 능력 평가 아님",
                style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
            PrimaryAction(if (plan is NextPracticePlan.Destination && plan.native) "기본 내비 목적지 확인" else "이 제안으로 준비하기",
                onSuggested, enabled = !busy)
        }
        SecondaryAction("직접 연습 조건 정하기", onManual)
    }
}

@Composable
internal fun HistoryScreen(records: List<SavedDrive>, onOpen: (SavedDrive) -> Unit, onDelete: (String) -> Unit, onDeleteAll: () -> Unit) {
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    PageTitle("주행 기록")
    if (records.isEmpty()) ContentPanel("저장된 기록 없음") {}
    if (records.isNotEmpty()) ContentPanel("") {
        records.forEachIndexed { index, record ->
            if (index > 0) HorizontalDivider(color = ThriveColors.Border)
            val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(record.startedAtEpochMs))
            ResponsivePair(
                first = { modifier -> Column(modifier, verticalArrangement = Arrangement.spacedBy(ThriveSpace.Sm)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Md), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Sm)) {
                        Text(record.displayTitle, style = MaterialTheme.typography.titleSmall)
                        StatusBadge(record.badgeLabel)
                    }
                    Text("$date · ${record.elapsedMs / 1000}초 · ${if (record.activeSlot == null) "완료" else "진행·복구 대기"}",
                        style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
                } },
                second = { modifier -> FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Md, androidx.compose.ui.Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(ThriveSpace.Sm)) {
                    if (record.activeSlot == null) {
                        SecondaryAction("리포트 보기", { onOpen(record) })
                        TextButton(onClick = { pendingDelete = record.id }, modifier = Modifier.heightIn(min = ThriveSize.TouchTarget)) {
                            Text("이 기록 삭제", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                } }, firstWeight = 3f, secondWeight = 2f,
            )
        }
    }
    if (records.any { it.activeSlot == null }) SecondaryAction("완료 기록 전체 삭제", { pendingDelete = "all" })
    if (pendingDelete != null) ThriveDialog(
        onDismiss = { pendingDelete = null }, title = "저장한 기록을 삭제할까요?",
        text = "삭제한 기록은 복구할 수 없어요. 진행 중인 기록은 유지돼요.",
        confirmLabel = "삭제하기", onConfirm = {
            val id = pendingDelete
            pendingDelete = null
            if (id == "all") onDeleteAll() else if (id != null) onDelete(id)
        },
    )
}

@Composable
internal fun ProfileScreen(experience: String, concerns: List<String>, onExperience: (String) -> Unit, onConcern: (String) -> Unit, onSave: () -> Unit, reducedMotion: Boolean = false, onReducedMotion: (Boolean) -> Unit = {}, curriculumTrack: String? = null, onCurriculumStage: () -> Unit = {}) {
    PageTitle("내 운전")
    ContentPanel("연습 단계") {
        Text(CurriculumTrack.from(curriculumTrack)?.title ?: "미선택", style = MaterialTheme.typography.titleSmall)
        SecondaryAction("연습 단계 변경", onCurriculumStage)
    }
    Text("운전 경험", style = MaterialTheme.typography.titleMedium)
    ChoiceGrid(listOf("처음 시작해요", "쉬었다 다시 시작해요", "가끔 운전해요").map { label -> { modifier ->
        ChoiceCard(label, "", experience == label, { onExperience(label) }, modifier)
    } })
    Text("관심 상황", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Md), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
        listOf("회전·교차로", "차선 변경", "낯선 경로", "주차").forEach { label ->
            SelectionChip(label, concerns.contains(label)) { onConcern(label) }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Lg), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        ThriveToggle(reducedMotion, onReducedMotion, "화면 움직임 줄이기")
        Column(Modifier.weight(1f)) {
            Text("화면 움직임 줄이기", style = MaterialTheme.typography.titleSmall)
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Lg), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Sm),
        itemVerticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        PrimaryAction("완료", onSave)
        SaveStatus("profile", Modifier.size(32.dp))
    }
}
