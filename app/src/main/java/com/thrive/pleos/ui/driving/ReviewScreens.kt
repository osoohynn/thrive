package com.thrive.pleos.ui.driving

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.thrive.pleos.ui.theme.*

@Composable
internal fun DrivingScreen(trip: DemoTrip, voice: Boolean, onVoice: () -> Unit, onArrive: () -> Unit) {
    PageTitle("주행 시연 · 실제 길 안내 아님", "300 m 앞, 우회전", "성수이로 방향 · 지도와 거리는 예시입니다.")
    CourseMap(trip.route, driving = true)
    ContentPanel(if (voice) "앞으로 우회전이 예정되어 있어요." else "음성 안내를 쉬고 있어요.") {
        Text(if (voice) "경로 안내를 미리 확인해 주세요." else "안내 설정을 다시 켤 수 있어요.", style = MaterialTheme.typography.bodyLarge)
        SecondaryAction(if (voice) "안내 끄기" else "안내 켜기", onVoice)
    }
    Text("${trip.endLabel} · 예시 주행 흐름", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
    PrimaryAction("도착 상황 보기 · 시연", onArrive)
}

@Composable
internal fun ReportScreen(trip: DemoTrip, date: String, feeling: String, onFeeling: (String) -> Unit, onRepeat: () -> Unit) {
    PageTitle("$date · 예시 리포트", "한 번 더, 익숙해졌어요.", "${trip.title} · 이번에 경험한 길을 돌아봐요.")
    ResponsivePair(
        first = { modifier ->
            Column(modifier, verticalArrangement = Arrangement.spacedBy(ThriveSpace.Xl)) {
                CourseMap(trip.route)
                TripSummary(trip)
                Text("시간·거리·상황은 시연용 예시입니다.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
            }
        },
        second = { modifier ->
            ContentPanel("이번 운전의 발견", modifier) {
                Text("01  회전 구간을 경험했어요", style = MaterialTheme.typography.titleSmall)
                Text("다음에도 회전할 방향을 미리 살펴봐요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
                Text("02  다시 살펴볼 곳이 있어요", style = MaterialTheme.typography.titleSmall)
                Text("성수역 교차로의 감속 장면을 예시로 표시했어요. 감속 자체를 잘못된 운전으로 평가하지 않아요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
            }
        },
    )
    Text("오늘 운전은 어땠나요?", style = MaterialTheme.typography.titleMedium)
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Md), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
        listOf("여유 있었어요", "보통이에요", "부담됐어요").forEach { label ->
            SelectionChip(label, feeling == label) { onFeeling(label) }
        }
    }
    ContentPanel("같은 길을 한 번 더, 여유롭게.") {
        Text("방금 살펴본 상황을 다음 연습으로 이어가요.", style = MaterialTheme.typography.bodyLarge)
        PrimaryAction("다음 연습 설정", onRepeat)
    }
}

@Composable
internal fun HistoryScreen(completedTrip: DemoTrip?, onOpen: (DemoTrip, String) -> Unit) {
    PageTitle("주행 기록", "조금씩 쌓이는, 나만의 길.", "예시 기록과 이번 앱 시연 기록을 구분해서 표시해요.")
    if (completedTrip != null) {
        ContentPanel("오늘 · 앱에서 완료한 시연") {
            Text(completedTrip.title, style = MaterialTheme.typography.titleSmall)
            TripSummary(completedTrip)
            SecondaryAction("리포트 보기", { onOpen(completedTrip, "오늘의 시연") })
        }
    }
    exampleRecords.forEach { record ->
        ContentPanel(record.date) {
            Text(record.trip.title, style = MaterialTheme.typography.titleSmall)
            TripSummary(record.trip)
            SecondaryAction("예시 리포트 보기", { onOpen(record.trip, record.date) })
        }
    }
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
    Text("현재 설정은 화면 재생성 중 유지되지만 앱 데이터로 영구 저장하지 않아요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
    PrimaryAction("이 설정으로 시작하기", onSave)
}
