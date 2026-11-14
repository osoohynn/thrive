package com.thrive.pleos.ui.driving

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
internal fun PracticeScreen(goal: PracticeGoal, duration: Int, onGoal: (PracticeGoal) -> Unit, onDuration: (Int) -> Unit, onContinue: () -> Unit) {
    PageTitle("연습하기 / 01", "오늘은 무엇을 익혀볼까요?", "한 가지 목표에 집중해서, 부담 없이 시작해요.")
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
        PracticeGoal.entries.forEach { item ->
            ChoiceCard(item.title, item.description, goal == item, { onGoal(item) }, mark = item.mark)
        }
    }
    Text("얼마나 연습할까요?", style = MaterialTheme.typography.titleMedium)
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Md), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
        listOf(15, 30, 45).forEach { minutes ->
            SelectionChip("${minutes}분", duration == minutes) { onDuration(minutes) }
        }
    }
    Text("출발 · 현재 위치 / 기본 코스 · 출발지로 돌아오기", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
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
internal fun RoutesScreen(trip: DemoTrip, onRoute: (DemoRoute) -> Unit, onContinue: () -> Unit) {
    PageTitle(if (trip.mode == DriveMode.Practice) "연습하기 / 02" else "운전하기", "나에게 맞는 길로.", "${trip.title} · 추천 이유를 확인하고 선택해요.")
    ResponsivePair(
        first = { CourseMap(trip.route, it) },
        second = { modifier ->
            Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
                DemoRoute.entries.forEach { route ->
                    val candidate = trip.copy(route = route)
                    ChoiceCard(route.title, "${candidate.minutes}분 · ${candidate.distance} km\n차선 변경 ${route.laneChanges}회 · 예시", route == trip.route, { onRoute(route) })
                }
            }
        },
    )
    Text("검토용 코스입니다. 실시간 교통 정보와 실제 경로 분석은 연동하지 않았어요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
    PrimaryAction("이 코스로 준비하기", onContinue)
}

@Composable
internal fun BriefScreen(trip: DemoTrip, voice: Boolean, onVoice: (Boolean) -> Unit, onStart: () -> Unit) {
    PageTitle("출발 전, 잠깐", "길을 먼저 알면, 조금 더 여유롭게.", "${trip.title} · 예상 ${trip.minutes}분 · ${trip.distance} km")
    ResponsivePair(
        first = { CourseMap(trip.route, it) },
        second = { modifier ->
            ContentPanel("미리 살펴볼 구간", modifier) {
                Text("01  성수이로 진입", style = MaterialTheme.typography.titleSmall)
                Text("출발 후 우회전 구간이 있어요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
                Text("02  성수역 교차로", style = MaterialTheme.typography.titleSmall)
                Text("회전할 방향을 미리 확인해요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
                Text("03  ${trip.endLabel}", style = MaterialTheme.typography.titleSmall)
                Text("도착 후 오늘의 운전을 돌아봐요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
            }
        },
    )
    ContentPanel("음성 학습 안내") {
        Text("필요한 순간에 짧게 알려드리는 흐름입니다. 현재 시연에서는 실제 음성을 재생하지 않아요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Lg), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Sm), itemVerticalAlignment = Alignment.CenterVertically) {
            Switch(checked = voice, onCheckedChange = onVoice, modifier = Modifier.sizeIn(minWidth = ThriveSize.TouchTarget, minHeight = ThriveSize.TouchTarget))
            Text(if (voice) "안내 켜짐" else "안내 꺼짐", style = MaterialTheme.typography.labelLarge)
        }
    }
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
