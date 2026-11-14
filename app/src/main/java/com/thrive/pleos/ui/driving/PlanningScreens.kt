package com.thrive.pleos.ui.driving

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.thrive.pleos.ui.theme.*

@Composable
internal fun DrivingHome(trip: DemoTrip, onNavigate: (DemoScreen) -> Unit) {
    PageTitle("나의 다음 한 걸음", "오늘의 길이, 내일은 익숙해지도록.", "가야 할 길도, 연습하고 싶은 길도 함께해요.")
    ResponsivePair(
        first = { modifier ->
            ContentPanel("${trip.destination}까지 함께 가볼까요?", modifier) {
                Text("설정된 목적지 · 예시", style = MaterialTheme.typography.labelMedium, color = ThriveColors.Accent)
                TripSummary(trip.copy(mode = DriveMode.Destination))
                Text("회전 구간을 미리 확인하고 출발해요.", style = MaterialTheme.typography.bodyLarge)
                PrimaryAction("출발 준비하기", { onNavigate(DemoScreen.Brief) })
            }
        },
        second = { modifier ->
            ContentPanel("목적지 없이 가볍게", modifier) {
                Text("익히고 싶은 상황부터 골라요.", style = MaterialTheme.typography.bodyLarge, color = ThriveColors.TextSecondary)
                PrimaryAction("연습 코스 찾기", { onNavigate(DemoScreen.Practice) })
                SecondaryAction("목적지 찾기", { onNavigate(DemoScreen.Destination) })
            }
        },
    )
    ContentPanel("익숙한 지도에서 시작해요") {
        Text("목적지 설정 후 쓰라이브가 함께 운전하기를 제안하는 흐름을 체험해 보세요.", style = MaterialTheme.typography.bodyLarge, color = ThriveColors.TextSecondary)
        SecondaryAction("지도 연동 체험", { onNavigate(DemoScreen.Navigation) })
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
    onContinue: () -> Unit,
) {
    PageTitle("연습하기 / 01", "오늘은 무엇을 익혀볼까요?", "한 가지 목표에 집중해서, 부담 없이 시작해요.")
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
        PracticeGoal.entries.forEach { item ->
            ChoiceCard(item.title, item.description, goal == item, { onGoal(item) }, mark = item.mark)
        }
    }
    Text("최대 몇 분까지 연습할까요?", style = MaterialTheme.typography.titleMedium)
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Md), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
        practiceTimeOptions.forEach { minutes ->
            SelectionChip("${minutes}분", duration == minutes) { onDuration(minutes) }
        }
    }
    if (goal != PracticeGoal.Daily) ContentPanel("차선 변경은 최소로") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
            Switch(checked = minimizeLaneChanges, onCheckedChange = onMinimizeLaneChanges,
                modifier = Modifier.sizeIn(minWidth = ThriveSize.TouchTarget, minHeight = ThriveSize.TouchTarget)
                    .semantics { contentDescription = "차선 변경 최소화" })
            Text(if (minimizeLaneChanges) "예시 변경 구간 1회 이하" else "변경 횟수 제한 없음", style = MaterialTheme.typography.bodyLarge)
        }
        Text("차선 변경 준비 목표는 변경 구간 2회 이상의 예시를 찾아요. 조건이 겹치면 코스가 없을 수 있어요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
    }
    Text("예시 코스에서 조건을 먼저 확인해요. 실제 출발 위치와 도로·교통 정보는 연동하지 않았어요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
    PrimaryAction(if (goal == PracticeGoal.Daily) "목적지 선택하기" else "연습 코스 찾기", onContinue)
}

@Composable
internal fun DestinationScreen(query: String, onQuery: (String) -> Unit, onDestination: (String) -> Unit) {
    PageTitle("운전하기", "어디까지 함께 갈까요?", "익숙해지고 싶은 목적지를 선택해 주세요.")
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
    Text("검색 가능한 예시 장소", style = MaterialTheme.typography.labelMedium, color = ThriveColors.Accent)
    val matches = demoDestinations.filter { it.contains(query.trim(), ignoreCase = true) }
    if (matches.isEmpty()) {
        ContentPanel("일치하는 예시 장소가 없어요") {
            Text("성수, 서울숲, 뚝섬을 검색해 보세요.", style = MaterialTheme.typography.bodyLarge)
        }
    }
    matches.forEach { name ->
        SecondaryAction("$name ↗", { onDestination(name) }, Modifier.fillMaxWidth())
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
    PageTitle(if (practice) "연습하기 / 02" else "운전하기", "나에게 맞는 길로.",
        if (practice) "${trip.goal.title} · 조건에 맞는 예시 코스 ${recommendations.size}개" else "${trip.title} · 예시 경로를 선택해요.")
    if (practice && recommendations.isEmpty()) {
        ContentPanel("조건에 맞는 예시 코스가 없어요") {
            Text("시간을 늘리거나 차선 변경 최소화 조건을 조정해 주세요. 조건에 맞지 않는 길을 대신 선택하지 않아요.", style = MaterialTheme.typography.bodyLarge)
            SecondaryAction("연습 조건 바꾸기", onAdjust)
        }
        return
    }
    ResponsivePair(
        first = { CourseMap(trip.route, it) },
        second = { modifier ->
            Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
                if (practice) recommendations.forEachIndexed { index, item ->
                    ChoiceCard(item.route.title,
                        "${item.route.minutes}분 · ${item.route.kilometers} km · 차선 변경 ${item.route.laneChanges}회 · 예시\n${item.reason}",
                        item.route == trip.route, { onRoute(item.route) }, mark = if (index == 0) "추천" else "대안")
                } else DemoRoute.entries.forEach { route ->
                    ChoiceCard(route.title, "${route.minutes}분 · ${route.kilometers} km\n차선 변경 ${route.laneChanges}회 · 예시", route == trip.route, { onRoute(route) })
                }
            }
        },
    )
    Text("예시 속성을 비교한 추천이에요. 실제 도로 검토·실시간 교통·내비 경로 생성은 연동하지 않았어요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
    if (practice) SecondaryAction("연습 조건 바꾸기", onAdjust)
    PrimaryAction("이 코스로 준비하기", onContinue)
}

@Composable
internal fun BriefScreen(trip: DemoTrip, onStart: () -> Unit, guidance: @Composable () -> Unit) {
    PageTitle("출발 전, 잠깐", "길을 먼저 알면, 조금 더 여유롭게.", "${trip.title} · 예상 ${trip.minutes}분 · ${trip.distance} km")
    if (trip.mode == DriveMode.Practice) ContentPanel("선택한 예시 코스") {
        Text("${trip.route.title} · ${trip.minutes}분 · ${trip.route.kilometers} km · 차선 변경 ${trip.route.laneChanges}회", style = MaterialTheme.typography.bodyLarge)
        Text("목표와 조건을 바탕으로 선택했어요. 아래 지도·구간은 실제 경로 안내가 아닌 예시입니다.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
    }
    ResponsivePair(
        first = { CourseMap(trip.route, it) },
        second = { modifier ->
            ContentPanel("미리 살펴볼 구간 · 예시", modifier) {
                Text("01  성수이로 진입", style = MaterialTheme.typography.titleSmall)
                Text("출발 후 우회전 구간이 있어요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
                Text("02  성수역 교차로", style = MaterialTheme.typography.titleSmall)
                Text("회전할 방향을 미리 확인해요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
                Text("03  ${trip.endLabel}", style = MaterialTheme.typography.titleSmall)
                Text("도착 후 오늘의 운전을 돌아봐요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
            }
        },
    )
    guidance()
    PrimaryAction("함께 출발하기", onStart)
}

@Composable
internal fun NavigationDemo(trip: DemoTrip, invitation: Boolean, onInvite: () -> Unit, onAccept: () -> Unit, onDismiss: () -> Unit) {
    PageTitle("기본 내비게이션 연동 구상", "지도에서 정한 목적지 그대로.", "이 화면과 제안은 앱 내부 시연입니다. 다른 앱의 경로를 감지하지 않습니다.")
    ResponsivePair(
        first = { CourseMap(trip.route, it) },
        second = { modifier ->
            ContentPanel(trip.destination, modifier) {
                TripSummary(trip.copy(mode = DriveMode.Destination))
                Text("성수이로를 따라 이동하는 예시 경로", style = MaterialTheme.typography.bodyLarge)
                PrimaryAction("이 경로로 출발", onInvite)
            }
        },
    )
    if (invitation) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("가는 길, 함께 연습할까요?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("쓰라이브가 필요한 순간에 안내하고, 도착하면 함께 돌아봐요.\n\n앱 내부의 연동 체험 제안입니다.", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { PrimaryAction("함께 운전하기", onAccept) },
            dismissButton = { SecondaryAction("건너뛰기", onDismiss) },
            containerColor = ThriveColors.Background,
            titleContentColor = ThriveColors.Text,
            textContentColor = ThriveColors.TextSecondary,
            shape = ThriveShape.Panel,
        )
    }
}
