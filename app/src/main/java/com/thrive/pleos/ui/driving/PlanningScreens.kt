package com.thrive.pleos.ui.driving

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.thrive.pleos.ui.theme.*
import com.thrive.pleos.driving.navigation.NavigationState

@Composable
internal fun DrivingHome(navigation: NavigationState, onNavigate: (DemoScreen) -> Unit) {
    PageTitle("홈")
    ResponsivePair(
        first = { modifier -> ContentPanel("내비 목적지", modifier, shadowElevation = 3.dp) {
            Text(navigation.destinationName ?: "목적지 없음", style = MaterialTheme.typography.headlineSmall)
            PrimaryAction("실제 목적지 기록", { onNavigate(DemoScreen.Navigation) })
        } },
        second = { modifier -> ContentPanel("연습", modifier, shadowElevation = 3.dp) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Md), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
                SecondaryAction("연습 코스 찾기", { onNavigate(DemoScreen.Practice) })
                SecondaryAction("목적지 찾기", { onNavigate(DemoScreen.Destination) })
            }
        } }, firstWeight = 845f, secondWeight = 611f, equalHeight = true,
    )
    ContentPanel("시연", shadowElevation = 3.dp) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Md), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
            SecondaryAction("출발 준비하기", { onNavigate(DemoScreen.Brief) })
            SecondaryAction("지도 연동 체험", { onNavigate(DemoScreen.DemoNavigation) })
        }
    }
}

@Composable
internal fun PracticeScreen(
    goal: PracticeGoal,
    duration: Int,
    onGoal: (PracticeGoal) -> Unit,
    onDuration: (Int) -> Unit,
    minimizeLaneChanges: Boolean,
    onMinimizeLaneChanges: (Boolean) -> Unit,
    onNative: () -> Unit,
    previous: @Composable () -> Unit = {},
    onContinue: () -> Unit,
) {
    PageTitle("연습 목표", badge = "예시 코스")
    previous()
    ChoiceGrid(PracticeGoal.entries.map { item -> { modifier ->
        ChoiceCard(item.title, item.description, goal == item, { onGoal(item) }, modifier)
    } })
    ResponsivePair(
        first = { modifier -> Column(modifier, verticalArrangement = Arrangement.spacedBy(ThriveSpace.Lg)) {
            Text("최대 연습 시간", style = MaterialTheme.typography.titleMedium)
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Lg), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
                practiceTimeOptions.forEach { minutes -> SelectionChip("${minutes}분", duration == minutes) { onDuration(minutes) } }
            }
        } },
        second = { modifier -> if (goal != PracticeGoal.Daily) ContentPanel("차선 변경 최소화", modifier) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Lg)) {
                ThriveToggle(minimizeLaneChanges, onMinimizeLaneChanges, "차선 변경 최소화")
                Text(if (minimizeLaneChanges) "예시 변경 구간 1회 이하" else "변경 횟수 제한 없음", style = MaterialTheme.typography.bodyLarge)
            }
            Text("차선 변경 연습: 변경 구간 2회 이상", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
        } }, firstWeight = 636f, secondWeight = 820f,
    )
    Text("예시 코스 · 실시간 도로·교통 미반영", style = MaterialTheme.typography.bodySmall, color = ThriveColors.TextSecondary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Lg), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
        PrimaryAction(if (goal == PracticeGoal.Daily) "목적지 찾기" else "연습 코스 찾기", onContinue)
        SecondaryAction("실제 연습 경로 준비", onNative)
    }
}

@Composable
internal fun DestinationScreen(query: String, onQuery: (String) -> Unit, onDestination: (String) -> Unit) {
    PageTitle("목적지", badge = "예시 장소")
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().heightIn(min = ThriveSize.InputMinHeight),
        label = { Text("장소 이름 검색", style = MaterialTheme.typography.labelMedium) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = ThriveShape.Panel,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = ThriveColors.Text,
            unfocusedTextColor = ThriveColors.Text,
            focusedContainerColor = ThriveColors.ReadingSurface,
            unfocusedContainerColor = ThriveColors.ReadingSurface,
            focusedBorderColor = ThriveColors.Accent,
            unfocusedBorderColor = ThriveColors.ControlBorder,
            focusedLabelColor = ThriveColors.Accent,
            unfocusedLabelColor = ThriveColors.TextSecondary,
            cursorColor = ThriveColors.Accent,
        ),
    )
    Text("검색 결과 · 예시", style = MaterialTheme.typography.labelMedium, color = ThriveColors.Accent)
    val matches = demoDestinations.filter { it.contains(query.trim(), ignoreCase = true) }
    if (matches.isEmpty()) {
        ContentPanel("일치하는 예시 장소가 없어요") {
            Text("검색어: 성수, 서울숲, 뚝섬", style = MaterialTheme.typography.bodyLarge)
        }
    }
    matches.forEach { name ->
        Surface(onClick = { onDestination(name) }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
            shape = ThriveShape.Panel, color = ThriveColors.ReadingSurface,
            border = androidx.compose.foundation.BorderStroke(ThriveSize.Border, ThriveColors.Border)) {
            Row(Modifier.padding(horizontal = ThriveSpace.Panel, vertical = ThriveSpace.Lg), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text("↗", style = MaterialTheme.typography.headlineSmall, color = ThriveColors.Brand)
            }
        }
    }
}

@Composable
internal fun RoutesScreen(
    trip: DemoTrip,
    recommendations: List<PracticeRecommendation>,
    onRoute: (DemoRoute) -> Unit,
    onAdjust: () -> Unit,
    onContinue: () -> Unit,
) {
    val practice = trip.mode == DriveMode.Practice
    PageTitle("코스 선택", if (practice) "${trip.goal.title} · ${recommendations.size}개" else trip.title, badge = "예시 경로")
    if (practice && recommendations.isEmpty()) {
        ContentPanel("조건에 맞는 예시 코스가 없어요") {
            Text("시간 또는 차선 변경 조건을 조정해 주세요.", style = MaterialTheme.typography.bodyLarge)
            SecondaryAction("연습 조건 바꾸기", onAdjust)
        }
        return
    }
    ResponsivePair(
        first = { CourseMap(trip.route, it, height = 560.dp) },
        second = { modifier ->
            Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
                if (practice) recommendations.forEachIndexed { index, item ->
                    ChoiceCard(item.route.title,
                        "${item.route.minutes}분 · ${item.route.kilometers} km · 차선 변경 ${item.route.laneChanges}회",
                        item.route == trip.route, { onRoute(item.route) }, mark = if (index == 0) "추천" else "대안")
                } else DemoRoute.entries.forEach { route ->
                    ChoiceCard(route.title, "${route.minutes}분 · ${route.kilometers} km\n차선 변경 ${route.laneChanges}회 · 예시", route == trip.route, { onRoute(route) })
                }
            }
        },
        firstWeight = 820f, secondWeight = 636f,
    )
    Text("예시 경로 · 실시간 도로·교통 미반영", style = MaterialTheme.typography.bodySmall, color = ThriveColors.TextSecondary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Lg), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
        if (practice) SecondaryAction("연습 조건 바꾸기", onAdjust)
        PrimaryAction("이 코스로 준비하기", onContinue)
    }
}

@Composable
internal fun BriefScreen(trip: DemoTrip, onStart: () -> Unit, guidance: @Composable () -> Unit) {
    PageTitle("출발 준비", "${trip.title} · ${trip.minutes}분 · ${trip.distance} km", badge = "시연")
    ResponsivePair(
        first = { CourseMap(trip.route, it, height = 400.dp) },
        second = { modifier ->
            ContentPanel("주요 구간 · 예시", modifier) {
                Text("01  성수이로 진입", style = MaterialTheme.typography.titleSmall)
                Text("우회전", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
                Text("02  성수역 교차로", style = MaterialTheme.typography.titleSmall)
                Text("회전 구간", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
                Text("03  ${trip.endLabel}", style = MaterialTheme.typography.titleSmall)
                Text("도착", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
            }
        },
        firstWeight = 820f, secondWeight = 636f,
    )
    if (trip.mode == DriveMode.Practice) ResponsivePair(
        first = { modifier -> ContentPanel("선택한 예시 코스", modifier) {
            Text("${trip.route.title} · ${trip.minutes}분 · ${trip.route.kilometers} km · 차선 변경 ${trip.route.laneChanges}회", style = MaterialTheme.typography.bodyLarge)
            Text("예시 지도 · 실제 길 안내 아님", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
        } },
        second = { modifier -> Box(modifier) { guidance() } }, firstWeight = 636f, secondWeight = 820f,
    ) else guidance()
    PrimaryAction("시연 시작", onStart)
}

@Composable
internal fun NavigationDemo(trip: DemoTrip, invitation: Boolean, onInvite: () -> Unit, onAccept: () -> Unit, onDismiss: () -> Unit, connection: @Composable () -> Unit) {
    PageTitle("지도 연동 체험", badge = "앱 내부 시연")
    ResponsivePair(
        first = { modifier -> Box(modifier) { connection() } },
        second = { modifier -> Column(modifier, verticalArrangement = Arrangement.spacedBy(ThriveSpace.Lg)) {
            CourseMap(trip.route, height = 380.dp)
            ContentPanel(trip.destination) {
                TripSummary(trip.copy(mode = DriveMode.Destination))
                Text("성수이로 · 예시 경로", style = MaterialTheme.typography.bodyLarge)
                PrimaryAction("이 경로로 출발", onInvite)
            }
        } }, firstWeight = 620f, secondWeight = 836f,
    )
    if (invitation) {
        ThriveDialog(
            title = "시연 경로 사용",
            text = "앱 내부의 예시 경로입니다.",
            onDismiss = onDismiss, confirmLabel = "경로 선택", onConfirm = onAccept, dismissLabel = "건너뛰기",
        )
    }
}
