package com.thrive.pleos.ui.driving

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.thrive.pleos.driving.storage.SavedDrive
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.Modifier
import com.thrive.pleos.ui.theme.*

@Composable
internal fun DrivingScreen(trip: DemoTrip, onArrive: () -> Unit, elapsedMs: Long, guidance: @Composable () -> Unit) {
    PageTitle("주행 시연 · 실제 길 안내 아님", "300 m 앞, 우회전", "성수이로 방향 · 지도와 거리는 예시입니다.")
    CourseMap(trip.route, driving = true)
    guidance()
    Text("기록 중 ${elapsedMs / 1000}초 · ${trip.endLabel} · 예시 주행 흐름", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
    PrimaryAction("시연 기록 종료하기", onArrive)
}

@Composable
internal fun ReportScreen(
    record: SavedDrive,
    busy: Boolean,
    onFeeling: (String) -> Unit,
    onSuggested: () -> Unit,
    onManual: () -> Unit,
) {
    val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(record.startedAtEpochMs))
    val feeling = record.feeling
    PageTitle("$date · ${record.sourceLabel}", "오늘의 기록을 돌아봐요.", record.displayTitle)
    if (record.isSdk) LiveReportSummary(record) else {
        val trip = record.toDemoTrip()
        ResponsivePair(
            first = { modifier ->
                Column(modifier, verticalArrangement = Arrangement.spacedBy(ThriveSpace.Xl)) {
                    CourseMap(trip.route)
                    Text("기록 시간 ${record.elapsedMs / 1000}초", style = MaterialTheme.typography.headlineSmall)
                    Text("거리 미측정 · 위치 센서 미연동", style = MaterialTheme.typography.bodyLarge)
                    Text("선택 코스 ${trip.route.title} · 예정 ${trip.minutes}분과 실제 기록 시간을 구분해 저장했어요. 지도와 주행 장면은 예시입니다.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
                }
            },
            second = { modifier ->
                ContentPanel("이번에 선택한 연습", modifier) {
                    Text("목표 · ${trip.goal.title}", style = MaterialTheme.typography.titleSmall)
                    Text("예시 경로 · ${trip.route.title}", style = MaterialTheme.typography.bodyLarge)
                    Text("예시 차선 변경 ${trip.route.laneChanges}회 · ${trip.route.kilometers} km", style = MaterialTheme.typography.bodyMedium)
                    Text("선택했던 예시 경로의 속성이에요. 실제 회전·감속·차선 변경을 분석한 결과는 아니에요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
                }
            },
        )
    }
    Text("오늘 운전은 어땠나요?", style = MaterialTheme.typography.titleMedium)
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Md), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
        drivingFeelings.forEach { label ->
            SelectionChip(label, feeling == label) { onFeeling(label) }
        }
    }
    ContentPanel("다음 연습 제안") {
        val plan = nextPracticePlan(record)
        if (plan == null) {
            Text("오늘의 느낌을 고르면 응답에 맞춰 다음 연습을 제안해요. 직접 조건을 정해도 좋아요.", style = MaterialTheme.typography.bodyLarge)
        } else {
            Text(plan.reason, style = MaterialTheme.typography.bodyLarge)
            when (plan) {
                is NextPracticePlan.Practice -> {
                    Text("${plan.request.goal.title} · 최대 ${plan.request.maxMinutes}분", style = MaterialTheme.typography.titleSmall)
                    Text(if (plan.request.minimizeLaneChanges) "차선 변경 최소화 · 예시 변경 구간 1회 이하" else "차선 변경 횟수 제한 없음",
                        style = MaterialTheme.typography.bodyMedium)
                    val route = plan.preferredRoute
                    Text(route?.let { "제안하는 예시 · ${it.title} · ${it.minutes}분" } ?: "현재 조건에 맞는 예시 코스가 없어 조건 조정이 필요해요.",
                        style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
                }
                is NextPracticePlan.Destination -> {
                    Text("지난 목적지 · ${plan.name}", style = MaterialTheme.typography.titleSmall)
                    Text(if (plan.native) "기본 내비의 현재 목적지를 다시 조회해요." else "예시 목적지 선택으로 이어져요.",
                        style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
                }
            }
            Text("사용자가 남긴 느낌과 선택 목표에 따른 제안이에요. 운전 실력이나 안전성 평가가 아니에요.",
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
    PageTitle("주행 기록", "조금씩 쌓이는, 나만의 길.", "기기에 저장한 기록이에요. 시연 기록은 실제 주행과 구분해요.")
    if (records.isEmpty()) ContentPanel("아직 저장한 기록이 없어요") {
        Text("연습 코스를 선택하고 시연을 시작해 보세요.", style = MaterialTheme.typography.bodyLarge)
    }
    records.forEach { record ->
        val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(record.startedAtEpochMs))
        ContentPanel(record.displayTitle) {
            Text("$date · ${record.sourceLabel}", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.Accent)
            Text("기록 시간 ${record.elapsedMs / 1000}초 · ${if (record.activeSlot == null) "완료" else "진행·복구 대기"}", style = MaterialTheme.typography.bodyLarge)
            if (record.activeSlot == null) {
                SecondaryAction("리포트 보기", { onOpen(record) })
                TextButton(onClick = { pendingDelete = record.id }, modifier = Modifier.heightIn(min = ThriveSize.TouchTarget)) {
                    Text("이 기록 삭제", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    if (records.any { it.activeSlot == null }) SecondaryAction("완료 기록 전체 삭제", { pendingDelete = "all" })
    if (pendingDelete != null) AlertDialog(
        onDismissRequest = { pendingDelete = null },
        title = { Text("저장한 기록을 삭제할까요?", style = MaterialTheme.typography.titleMedium) },
        text = { Text("삭제한 기록은 복구할 수 없어요. 진행 중인 기록은 유지돼요.", style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { PrimaryAction("삭제하기", {
            val id = pendingDelete
            pendingDelete = null
            if (id == "all") onDeleteAll() else if (id != null) onDelete(id)
        }) },
        dismissButton = { SecondaryAction("취소", { pendingDelete = null }) },
        containerColor = ThriveColors.Background,
        titleContentColor = ThriveColors.Text,
        textContentColor = ThriveColors.TextSecondary,
    )
}

@Composable
internal fun ProfileScreen(experience: String, concerns: List<String>, onExperience: (String) -> Unit, onConcern: (String) -> Unit, onSave: () -> Unit) {
    PageTitle("내 운전", "지금의 나에게 맞춰서.", "실제 경험과 관심 상황을 선택해 주세요.")
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
        listOf("처음 시작해요", "쉬었다 다시 시작해요", "가끔 운전해요").forEach { label ->
            ChoiceCard(label, "실제 운전 경험 기준", experience == label, { onExperience(label) })
        }
    }
    Text("조금 더 익숙해지고 싶은 상황", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Md), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
        listOf("회전·교차로", "차선 변경", "낯선 경로", "주차").forEach { label ->
            SelectionChip(label, concerns.contains(label)) { onConcern(label) }
        }
    }
    Text("주차는 관심 상황으로만 저장돼요. 주차 전용 코스는 준비 중이에요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
    Text("선택한 경험과 관심 상황은 기기에 저장돼요. 앱을 다시 열어도 유지돼요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
    PrimaryAction("이 설정으로 시작하기", onSave)
}
