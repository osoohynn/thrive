package com.thrive.pleos.ui.driving

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.thrive.pleos.driving.navigation.*
import com.thrive.pleos.driving.storage.SavedDrive
import com.thrive.pleos.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale

internal fun nativePracticeCandidates(state: NavigationState, records: List<SavedDrive>): List<LiveDestination> {
    val saved = records.filter { it.isSdk }.mapNotNull { row ->
        val name = row.practiceStopName ?: row.destination
        val lat = row.practiceStopLatitude ?: row.destinationLatitude ?: return@mapNotNull null
        val lon = row.practiceStopLongitude ?: row.destinationLongitude ?: return@mapNotNull null
        LiveDestination(name, lat, lon)
    }
    val places = listOfNotNull(state.course?.plan?.stop, state.destination) + state.recentDestinations + saved
    return places.filter { it.valid && it.name != "출발지로 돌아오기" }
        .fold(emptyList<LiveDestination>()) { found, point -> if (found.any { it.samePlace(point) }) found else found + point }.take(6)
}

@Composable
internal fun NativeRoutePreview(state: NavigationState) {
    state.estimate?.let { estimate ->
        ContentPanel("기본 내비에서 확인한 경로") {
            Text("예상 ${estimate.minutes}분 · ${String.format(Locale.getDefault(), "%.1f", estimate.meters / 1000.0)} km", style = MaterialTheme.typography.titleMedium)
            Text("현재 경로의 남은 예상 값이에요. 실제 기록 시간·거리와 구분해요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
            state.turns.take(2).forEach { turn ->
                Text(turn.description.ifBlank { "내비에서 상세 안내 확인" }, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
internal fun NativePracticeScreen(
    request: PracticeRequest,
    navigation: NavigationState,
    candidates: List<LiveDestination>,
    preferredStop: LiveDestination?,
    preferredReturn: Boolean,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
    onOpenNavigation: () -> Unit,
    onDuration: (Int) -> Unit,
    onRelaxLaneLimit: () -> Unit,
    onRequest: (NativePracticePlan) -> Unit,
    onStart: (NativePracticePlan) -> Unit,
) {
    var selectedKey by rememberSaveable(preferredStop?.latitude, preferredStop?.longitude) {
        mutableStateOf(preferredStop?.let { "${it.latitude},${it.longitude}" })
    }
    var roundTrip by rememberSaveable { mutableStateOf(preferredReturn) }
    var option by rememberSaveable { mutableStateOf("COMFORTABLE") }
    var confirmation by remember { mutableStateOf<NativePracticePlan?>(null) }
    var nowMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); nowMs = SystemClock.elapsedRealtime() } }
    val selected = candidates.firstOrNull { "${it.latitude},${it.longitude}" == selectedKey } ?: candidates.firstOrNull()
    val origin = navigation.freshOrigin(nowMs)
    val plan = selected?.let { NativePracticePlan(request.goal, request.maxMinutes, it, if (roundTrip) origin else null, option) }

    PageTitle("실제 연습 경로", request.goal.title)
    Text("목표는 연습·복습의 기준이에요. 도로별 난이도와 회전·차선 변경 횟수는 아직 검토하지 않았어요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
    if (!navigation.connected) {
        ContentPanel("기본 내비 목적지를 불러와요") {
            Text(navigation.message, style = MaterialTheme.typography.bodyLarge)
            PrimaryAction("목적지 불러오기", onConnect, enabled = !navigation.connecting)
        }
        return
    }
    if (request.minimizeLaneChanges) {
        ContentPanel("차선 변경 횟수 확인이 필요해요") {
            Text("변경 횟수 제한은 현재 예시 코스에서만 비교할 수 있어요. 실제 경로를 준비하려면 이 조건을 해제해 주세요.", style = MaterialTheme.typography.bodyLarge)
            SecondaryAction("변경 횟수 제한 해제", onRelaxLaneLimit)
        }
    }
    if (candidates.isEmpty()) ContentPanel("먼저 연습할 목적지를 골라요") {
        Text("기본 내비에서 목적지를 정한 후 돌아와 다시 불러와 주세요.", style = MaterialTheme.typography.bodyLarge)
        PrimaryAction("기본 내비 열기", onOpenNavigation)
    } else Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
        candidates.forEach { point ->
            ChoiceCard(point.name, point.address.ifBlank { "내비·이전 기록에서 가져온 목적지 · 경로 계산 전" }, point == selected,
                { selectedKey = "${point.latitude},${point.longitude}" })
        }
    }
    SecondaryAction("현재 경로 다시 확인", onRefresh)
    SecondaryAction("다른 목적지 찾기", onOpenNavigation)
    Text("최대 연습 시간", style = MaterialTheme.typography.titleMedium)
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
        listOf(15, 30, 45, 60).forEach { minutes -> SelectionChip("${minutes}분", request.maxMinutes == minutes) { onDuration(minutes) } }
    }
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
        SelectionChip("목적지까지", !roundTrip) { roundTrip = false }
        SelectionChip("출발지로 돌아오기", roundTrip) { roundTrip = true }
    }
    if (roundTrip) Text(if (origin == null) "출발지 정보가 오래됐어요. 현재 경로를 다시 확인해 주세요." else "선택한 목적지를 경유해 지금 출발지로 돌아와요.",
        style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(ThriveSpace.Md)) {
        SelectionChip("편안한 경로", option == "COMFORTABLE") { option = "COMFORTABLE" }
        SelectionChip("추천 경로", option == "RECOMMENDED") { option = "RECOMMENDED" }
    }
    Text("‘편안한 경로’는 기본 내비의 경로 옵션이에요. 초보에게 적합하다는 검토 결과는 아니에요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
    val calculating = navigation.course?.phase == CoursePhase.Calculating
    PrimaryAction("내비에서 경로 계산", { confirmation = plan },
        enabled = plan?.valid == true && !request.minimizeLaneChanges && !navigation.querying && !calculating && (!roundTrip || origin != null))
    navigation.course?.let { course ->
        val sameSettings = course.plan.goal == request.goal && course.plan.maxMinutes == request.maxMinutes &&
            course.plan.option == option && course.plan.roundTrip == roundTrip && selected?.samePlace(course.plan.stop) == true
        ContentPanel("연습 경로 계산 결과") {
            Text(course.message, style = MaterialTheme.typography.bodyLarge)
            course.estimate?.let { estimate ->
                Text("예상 ${estimate.minutes}분 · ${String.format(Locale.getDefault(), "%.1f", estimate.meters / 1000.0)} km", style = MaterialTheme.typography.titleMedium)
            }
            if (!sameSettings) {
                Text("조건이 바뀌었어요. 현재 조건으로 다시 계산해 주세요.", style = MaterialTheme.typography.bodyMedium)
            } else if (navigation.confirmedCourse(course.plan, nowMs) != null) {
                Text("목적지·이동 위치·내비 안내 사건을 이 기기에 저장하고 다른 앱에서도 기록을 이어가요. 미수신 구간은 미측정으로 남고, 저장한 기록은 나중에 삭제할 수 있어요.", style = MaterialTheme.typography.bodyMedium)
                PrimaryAction("동의하고 연습 기록 시작", { onStart(course.plan) })
            } else if (course.phase == CoursePhase.Ready) {
                SecondaryAction("출발 전 경로 다시 확인", onRefresh)
            }
        }
    }
    confirmation?.let { approved ->
        ThriveDialog(onDismiss = { confirmation = null },
            title = "기본 내비 경로를 변경할까요?",
            text = "${approved.stop.name} ${if (approved.roundTrip) "경유 후 출발지로 돌아오는" else "까지의"} 경로를 계산해요. 현재 내비 경로가 변경돼요. 계산 결과를 확인한 다음 기록을 시작할 수 있어요.",
            confirmLabel = "경로 변경하고 계산", onConfirm = { confirmation = null; onRequest(approved) })
    }
}
