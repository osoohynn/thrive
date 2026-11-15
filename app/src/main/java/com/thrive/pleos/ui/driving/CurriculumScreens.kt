package com.thrive.pleos.ui.driving

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thrive.pleos.R
import com.thrive.pleos.driving.curriculum.*
import com.thrive.pleos.driving.storage.SavedDrive
import com.thrive.pleos.ui.theme.*

@Composable
internal fun CurriculumFlow(local: DrivingLocalState, model: DrivingViewModel, initialTopicId: String?, initialMinutes: Int, changeStage: Boolean,
                            parked: Boolean, scrollState: ScrollState, onReminder: (String) -> Unit, onStarted: () -> Unit, onNative: () -> Unit) {
    var step by rememberSaveable { mutableStateOf(if (local.preferences.curriculumTrack == null || changeStage) "stage" else "browser") }
    var draftTrack by rememberSaveable { mutableStateOf(CurriculumTrack.from(local.preferences.curriculumTrack) ?: CurriculumTrack.Beginner) }
    val track = CurriculumTrack.from(local.preferences.curriculumTrack) ?: draftTrack
    val catalog = curriculum(track)
    val initial = practiceTopic(initialTopicId)?.takeIf { topicTrack(it) == track }
    var categoryIndex by rememberSaveable(track) { mutableIntStateOf(initial?.let { t -> catalog.categories.indexOfFirst { it.topics.any { row -> row.id == t.id } } } ?: 0) }
    var groupIndex by rememberSaveable(track) { mutableIntStateOf(initial?.let { t -> catalog.categories[categoryIndex].groups.indexOfFirst { it.topics.any { row -> row.id == t.id } } } ?: 0) }
    var topicId by rememberSaveable(track) { mutableStateOf(initial?.id) }
    var maxMinutes by rememberSaveable { mutableIntStateOf(initialMinutes) }
    var courseId by rememberSaveable { mutableStateOf<String?>(null) }
    var destination by rememberSaveable { mutableStateOf("") }
    // Confirmation belongs to this preparation; rotation and returning from another page require confirmation again.
    var companion by remember(topicId, courseId, step) { mutableStateOf(false) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) { kotlinx.coroutines.delay(30_000); value = System.currentTimeMillis() }
    }
    val topic = practiceTopic(topicId)
    val courses = topic?.let { practiceCourses(it, maxMinutes, destination) }.orEmpty()
    val course = courses.firstOrNull { it.id == courseId }
    fun back() { step = when (step) { "brief" -> if (topic?.kind == PracticeKind.Stationary) "browser" else "courses"; "courses" -> if (topic?.kind == PracticeKind.Destination) "destination" else "browser"; else -> "browser" } }
    BackHandler(step != "browser" && step != "stage") { back() }
    LaunchedEffect(step, track) { scrollState.scrollTo(0) }
    val outerBack = LocalPageBack.current
    CompositionLocalProvider(LocalPageBack provides if (step != "browser" && step != "stage") ({ back() }) else outerBack,
        LocalPracticeStep provides null) {
        when (step) {
            "stage" -> CurriculumStage(draftTrack, { draftTrack = it }, local.busy) {
                model.setCurriculumTrack(draftTrack) { step = "browser"; topicId = null; categoryIndex = 0; groupIndex = 0 }
            }
            "browser" -> {
                PageTitle("연습 항목 선택")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CurriculumTrack.entries.forEach { option -> SelectionChip(option.chip, option == track) {
                        if (option != track) model.setCurriculumTrack(option) { topicId = null; categoryIndex = 0; groupIndex = 0 }
                    } }
                    Text("완료 ${catalog.topics.count { it.id in completedTopics(local.records) }}/${catalog.topics.size}", style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(vertical = 16.dp), color = ThriveColors.Brand)
                }
                CurriculumBrowser(catalog, categoryIndex, groupIndex, topicId, completedTopics(local.records),
                    { categoryIndex = it; groupIndex = 0; topicId = null; courseId = null },
                    { groupIndex = it; topicId = null; courseId = null }, { topicId = it; courseId = null })
                ContentPanel("선택한 연습") {
                    if (topic == null) Text("소분류에서 연습할 항목을 선택해 주세요.", style = MaterialTheme.typography.bodyLarge)
                    else {
                        Text("${catalog.category(topic).title} › ${catalog.group(topic).title}", style = MaterialTheme.typography.bodySmall, color = ThriveColors.TextSecondary)
                        Text(topic.title, style = MaterialTheme.typography.titleSmall)
                    }
                    if (topic?.kind != PracticeKind.Destination) FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(15, 30, 45).forEach { minutes -> SelectionChip("${minutes}분", maxMinutes == minutes) { maxMinutes = minutes; courseId = null } }
                    }
                    val conditional = topic?.kind in setOf(PracticeKind.Night, PracticeKind.Rain)
                    val allowed = !conditional || local.conditions?.permits(topic!!.kind, now) == true
                    if (conditional && topic != null) {
                        StatusBadge("조건부 연습 · 시간대·날씨 확인")
                        Text(when {
                            local.conditionBusy -> "시간대·날씨 확인 중…"
                            local.conditions == null -> local.conditionMessage
                            allowed -> "현재 위치 기준 ${if (topic.kind == PracticeKind.Night) "야간" else "강우"} 예보가 확인됐어요."
                            else -> "현재 ${if (topic.kind == PracticeKind.Night) "야간" else "강우"} 조건이 확인되지 않았어요."
                        }, style = MaterialTheme.typography.bodyLarge)
                        Text("조건 확인 시 현재 좌표를 Open-Meteo에 전송해요. 예보는 실제 도로 상태와 다를 수 있어요.", style = MaterialTheme.typography.bodySmall, color = ThriveColors.TextSecondary)
                        SecondaryAction("현재 위치로 조건 확인", model::refreshConditions)
                        Text("알림은 저장한 지역을 최대 7일간 확인해 한 번 보내요. 실제 알림 시각은 시스템에 따라 달라져요.", style = MaterialTheme.typography.bodySmall, color = ThriveColors.TextSecondary)
                        SecondaryAction(if (topic.id in local.reminders) "조건 알림 해제" else if (topic.kind == PracticeKind.Night) "야간 연습 알림 받기" else "비 오는 날 알림 받기", {
                            if (topic.id in local.reminders) model.setPracticeReminder(topic.id, false) else onReminder(topic.id)
                        })
                    }
                    PrimaryAction(when (topic?.kind) {
                        PracticeKind.Stationary -> "정차 연습 준비하기"
                        PracticeKind.Destination -> "목적지 선택하기"
                        PracticeKind.Parking -> "연습 장소 찾기"
                        else -> "연습 코스 찾기"
                    }, {
                        courseId = null
                        step = when (topic?.kind) { PracticeKind.Stationary -> "brief"; PracticeKind.Destination -> "destination"; else -> "courses" }
                    }, enabled = topic != null && allowed && !local.busy)
                }
                SecondaryAction("기본 내비로 실제 연습", onNative)
            }
            "destination" -> {
                topic?.let { SelectedTopic(it) }
                var query by rememberSaveable { mutableStateOf("") }
                DestinationScreen(query, { query = it }) { destination = it; courseId = null; step = "courses" }
            }
            "courses" -> if (topic != null) {
                PageTitle(if (topic.kind == PracticeKind.Parking) "연습 장소 선택" else "연습 코스 선택", badge = "예시")
                SelectedTopic(topic)
                ResponsivePair(first = { modifier -> CourseMap(if (course?.direct == true) DemoRoute.Direct else DemoRoute.Gentle, modifier, height = 520.dp) },
                    second = { modifier -> Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (courses.isEmpty()) ContentPanel("조건에 맞는 코스 없음") {
                            Text("연습 시간을 늘리거나 다른 항목을 선택해 주세요.", style = MaterialTheme.typography.bodyLarge)
                        }
                        courses.forEachIndexed { index, item -> ChoiceCard(item.title, "${item.minutes}분 · ${item.km} km · ${item.attributes} · 예시",
                            item.id == courseId, { courseId = item.id }, mark = if (index == 0) "추천" else "대안") }
                    } }, firstWeight = 760f, secondWeight = 696f)
                Text(if (topic.kind == PracticeKind.Parking) "예시 장소 · 실제 주차 가능 여부·혼잡도·요금 미연동" else "예시 속성 비교 · 실제 도로 검토·실시간 교통·경로 생성 미연동",
                    style = MaterialTheme.typography.bodySmall, color = ThriveColors.TextSecondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryAction("항목 다시 고르기", { step = "browser" })
                    PrimaryAction(if (topic.kind == PracticeKind.Parking) "이 장소로 준비하기" else "이 코스로 준비하기", { step = "brief" }, enabled = course != null)
                }
            }
            "brief" -> if (topic != null) {
                PageTitle(if (topic.kind == PracticeKind.Stationary) "정차 연습 준비" else "출발 준비", badge = if (topic.kind == PracticeKind.Stationary) "P단 정차" else "시연")
                SelectedTopic(topic)
                ResponsivePair(first = { modifier ->
                    if (course != null) Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CourseMap(if (course.direct) DemoRoute.Direct else DemoRoute.Gentle, height = 400.dp)
                        Text("${course.title} · ${course.minutes}분 · ${course.km} km · 예시", style = MaterialTheme.typography.bodyLarge)
                    } else ContentPanel("차량 상태", modifier) {
                        Text("P단 주차 상태에서 장치 위치와 조작법을 확인해요.", style = MaterialTheme.typography.bodyLarge)
                        Text(if (parked) "P단 확인됨" else "P단 확인 필요", color = ThriveColors.Brand, style = MaterialTheme.typography.titleSmall)
                    }
                }, second = { modifier -> PracticePoints(topic, modifier) }, firstWeight = 640f, secondWeight = 816f)
                ContentPanel("음성 학습 안내") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ThriveToggle(local.preferences.voice, model::setVoice, "음성 학습 안내")
                        Text(if (local.preferences.voice) "안내 켜짐" else "안내 꺼짐", style = MaterialTheme.typography.labelLarge)
                    }
                    SecondaryAction("연습 포인트 듣기", { model.playCurriculumPoint(topic.id) })
                    if (topic.kind == PracticeKind.Parking) Text("주차 중에는 안내를 멈추고, 차를 완전히 세운 뒤에만 화면을 조작해 주세요.", style = MaterialTheme.typography.bodyMedium)
                }
                if (requiresCompanion(topic)) ContentPanel("연습면허 동승 확인") {
                    Text("해당 차량을 운전할 수 있는 면허를 받은 지 2년이 지나고 면허 정지 중이 아닌 동승자가 지도해야 해요.", style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ThriveToggle(companion, { companion = it }, "면허 2년 이상 동승자가 함께 탔어요")
                        Text("면허 2년 이상 동승자가 함께 탔어요", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    }
                }
                val conditionMet = topic.kind !in setOf(PracticeKind.Night, PracticeKind.Rain) || local.conditions?.permits(topic.kind, now) == true
                if (!conditionMet) SecondaryAction("시간대·날씨 다시 확인", { model.refreshConditions() })
                PrimaryAction(if (topic.kind == PracticeKind.Stationary) "정차 연습 시작하기" else "함께 출발하기", {
                    model.startCurriculum(topic.id, course?.id, maxMinutes, destination, companion, onStarted)
                }, enabled = !local.busy && (!requiresCompanion(topic) || companion) && conditionMet &&
                    (if (topic.kind == PracticeKind.Stationary) parked else course != null))
            }
        }
    }
}

@Composable
private fun CurriculumStage(track: CurriculumTrack, onTrack: (CurriculumTrack) -> Unit, busy: Boolean, onContinue: () -> Unit) {
    PageTitle("연습 단계 선택")
    ResponsivePair(first = { modifier -> ChoiceCard("초보자", "대분류 3개 · 중분류 6개 · 소분류 17개", track == CurriculumTrack.Beginner,
        { onTrack(CurriculumTrack.Beginner) }, modifier, "단계 1 · 장내시험 합격, 연습면허 발급") },
        second = { modifier -> ChoiceCard("장롱면허", "대분류 3개 · 중분류 6개 · 소분류 14개", track == CurriculumTrack.Returning,
            { onTrack(CurriculumTrack.Returning) }, modifier, "단계 2 · 면허 취득 후 운전 공백") })
    if (track == CurriculumTrack.Beginner) ContentPanel("연습면허로 도로에 나갈 때") {
        Text("해당 차량을 운전할 수 있는 면허를 받은 지 2년이 지난 동승자의 지도가 필요해요. 출발 준비에서 다시 확인해요.", style = MaterialTheme.typography.bodyLarge)
    }
    Text("단계는 내 운전에서 변경할 수 있어요. 진행 기록은 단계별로 저장해요.", style = MaterialTheme.typography.bodyMedium, color = ThriveColors.TextSecondary)
    PrimaryAction("이 단계로 시작하기", onContinue, enabled = !busy)
}

@Composable
private fun CurriculumBrowser(catalog: Curriculum, categoryIndex: Int, groupIndex: Int, selectedId: String?, completed: Set<String>,
                              onCategory: (Int) -> Unit, onGroup: (Int) -> Unit, onTopic: (String) -> Unit) {
    val category = catalog.categories[categoryIndex.coerceIn(catalog.categories.indices)]
    val group = category.groups[groupIndex.coerceIn(category.groups.indices)]
    fun progress(topics: List<PracticeTopic>): String {
        val count = topics.count { it.id in completed }
        return if (count == topics.size) "✓ 완료" else if (count > 0) "$count/${topics.size}" else ""
    }
    val categories: @Composable (Modifier) -> Unit = { modifier -> Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BrowserHeading("대분류 · 단계")
        catalog.categories.forEachIndexed { index, item -> CurriculumRow(item.title, "대분류 ${index + 1} · ${item.context}", index == categoryIndex, progress(item.topics), false, { onCategory(index) }) }
    } }
    val groups: @Composable (Modifier) -> Unit = { modifier -> Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BrowserHeading("중분류 · 연습 묶음")
        category.groups.forEachIndexed { index, item -> CurriculumRow(item.title, "중분류 ${index + 1} · 소분류 ${item.topics.size}개", index == groupIndex, progress(item.topics), false, { onGroup(index) }) }
    } }
    val topics: @Composable (Modifier) -> Unit = { modifier -> Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BrowserHeading("소분류 · 오늘 익힐 항목")
        group.topics.forEachIndexed { index, item -> CurriculumRow(item.title, "소분류 ${index + 1}" + when (item.kind) {
            PracticeKind.Stationary -> " · P단 정차"; PracticeKind.Night -> " · 야간에 추천"; PracticeKind.Rain -> " · 비 오는 날 추천"; else -> ""
        }, item.id == selectedId, if (item.id in completed) "✓ 완료" else "", true, { onTopic(item.id) }) }
    } }
    ThriveGlassPanel {
        BoxWithConstraints(Modifier.padding(24.dp)) {
            if (maxWidth >= 1200.dp * LocalDensity.current.fontScale) Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                categories(Modifier.weight(400f)); VerticalDivider(color = ThriveColors.Border)
                groups(Modifier.weight(440f)); VerticalDivider(color = ThriveColors.Border)
                topics(Modifier.weight(576f))
            } else Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                categories(Modifier.fillMaxWidth()); HorizontalDivider(color = ThriveColors.Border)
                groups(Modifier.fillMaxWidth()); HorizontalDivider(color = ThriveColors.Border)
                topics(Modifier.fillMaxWidth())
            }
        }
    }
}
@Composable
private fun BrowserHeading(title: String) { Text(title, style = MaterialTheme.typography.labelLarge, color = ThriveColors.TextSecondary) }
@Composable
private fun CurriculumRow(title: String, meta: String, selected: Boolean, badge: String, leaf: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).background(if (selected) ThriveColors.Mint.copy(alpha = .22f) else Color.White.copy(alpha = .7f), shape)
        .border(if (selected || focused) 3.dp else 1.dp, if (selected || focused) ThriveColors.Brand else ThriveColors.Border, shape)
        .onFocusChanged { focused = it.isFocused }.selectable(selected, role = Role.RadioButton, onClick = onClick)
        .padding(start = 24.dp, end = 20.dp, top = 18.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(meta, color = if (selected) ThriveColors.Brand else ThriveColors.TextSecondary, fontSize = 20.sp, lineHeight = 24.sp)
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 26.sp, lineHeight = 34.sp))
            if (badge.isNotEmpty()) Text(badge, color = ThriveColors.Brand, style = MaterialTheme.typography.labelSmall)
        }
        if (!leaf) Text("›", color = ThriveColors.Brand, fontSize = 40.sp)
        else if (selected) Image(painterResource(R.drawable.curriculum_radio), null, Modifier.size(36.dp))
        else Box(Modifier.size(36.dp).border(2.dp, ThriveColors.ControlBorder, RoundedCornerShape(18.dp)))
    }
}
@Composable
private fun SelectedTopic(topic: PracticeTopic) { ContentPanel("선택한 연습") { Text(topic.title, style = MaterialTheme.typography.titleSmall) } }
@Composable
private fun PracticePoints(topic: PracticeTopic, modifier: Modifier = Modifier) {
    ContentPanel(if (topic.kind == PracticeKind.Parking) "도착 후 주차 연습 포인트" else "오늘의 연습 포인트", modifier) {
        topic.points.forEachIndexed { i, point -> Text("0${i + 1}  $point", style = MaterialTheme.typography.bodyLarge) }
    }
}
@Composable
internal fun CurriculumDriving(record: SavedDrive, elapsedMs: Long, onFinish: () -> Unit, model: DrivingViewModel, parked: Boolean) {
    val topic = checkNotNull(practiceTopic(record.curriculumItemId))
    val stationary = topic.kind == PracticeKind.Stationary
    val point = ((elapsedMs / 15_000).toInt() % 3).coerceIn(0, 2)
    LaunchedEffect(record.id, point) {
        if (!stationary && topic.kind != PracticeKind.Parking) model.playCurriculumPoint(topic.id, point)
    }
    PageTitle(topic.title, badge = if (stationary) "정차 연습" else "시연 · 실제 길 안내 아님")
    if (stationary) {
        PracticePoints(topic)
        if (!parked) ContentPanel("P단 확인 필요") { Text("정차 연습은 P단 주차 상태에서 진행해 주세요.", style = MaterialTheme.typography.bodyLarge) }
        Text("연습 시간 ${elapsedMs / 1000}초", style = MaterialTheme.typography.headlineSmall)
    } else ResponsivePair(first = { modifier -> CourseMap(DemoRoute.valueOf(record.route), modifier, driving = true, height = 650.dp) },
        second = { modifier -> ContentPanel("다음 안내 · 예시", modifier) {
            Text(if (topic.id == "b213") "200 m 앞 · 유턴 구역" else if (topic.kind == PracticeKind.Parking) "주차장 진입 · 예시" else "선택한 코스 주행 시연", style = MaterialTheme.typography.headlineSmall)
            StatusBadge("연습 포인트 ${point + 1}/3")
            Text(topic.points[point], style = MaterialTheme.typography.bodyLarge)
            Text("기록 중 ${elapsedMs / 1000}초 · ${record.destination}", style = MaterialTheme.typography.bodyMedium)
            // No parking phase is inferred from a timer; parking guidance stays silent.
            if (topic.kind != PracticeKind.Parking) SecondaryAction("현재 포인트 듣기", { model.playCurriculumPoint(topic.id, point) })
        } }, firstWeight = 840f, secondWeight = 616f)
    PrimaryAction(if (stationary) "정차 연습 종료하기" else "시연 기록 종료하기", onFinish)
}
@Composable
internal fun CurriculumReport(record: SavedDrive, records: List<SavedDrive>, busy: Boolean, onFeeling: (String) -> Unit,
                              onPrepare: (PracticeTopic, Int) -> Unit, onRepeat: (PracticeTopic, Int) -> Unit) {
    val topic = checkNotNull(practiceTopic(record.curriculumItemId))
    val catalog = curriculum(topicTrack(topic)); val completed = completedTopics(records)
    PageTitle("연습 리포트", badge = record.badgeLabel)
    ResponsivePair(first = { modifier -> ContentPanel("이번에 연습한 항목", modifier) {
        Text(topic.title, style = MaterialTheme.typography.titleSmall)
        Text("기록 시간 ${record.elapsedMs / 1000}초", style = MaterialTheme.typography.headlineSmall)
        Text(record.destination, style = MaterialTheme.typography.bodyLarge)
        if (record.source == "DEMO") Text("예시 경로 · 거리 미측정", style = MaterialTheme.typography.bodyMedium)
    } }, second = { modifier -> ContentPanel("커리큘럼 진행", modifier) {
        val category = catalog.category(topic)
        listOf(category.title to category.topics, "${catalog.track.title} 전체" to catalog.topics).forEach { (title, items) ->
            val count = items.count { it.id in completed }
            Text("$title · $count/${items.size}", style = MaterialTheme.typography.bodyLarge)
            LinearProgressIndicator(progress = { count.toFloat() / items.size }, modifier = Modifier.fillMaxWidth().height(10.dp), color = ThriveColors.Brand)
        }
    } })
    Text("주행 느낌", style = MaterialTheme.typography.titleMedium)
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        drivingFeelings.forEach { value -> SelectionChip(value, record.feeling == value) { onFeeling(value) } }
        SaveStatus("feeling:${record.id}")
    }
    val next = if (record.feeling in drivingFeelings) nextTopic(topic.id, record.feeling) else null
    val budget = record.practiceMaxMinutes ?: 30
    ContentPanel("다음 연습 제안") {
        if (record.feeling.isBlank()) Text("주행 느낌을 선택해 주세요.", style = MaterialTheme.typography.bodyLarge)
        else if (next == null) Text("이 단계의 마지막 항목이에요.", style = MaterialTheme.typography.bodyLarge)
        else {
            Text(next.title, style = MaterialTheme.typography.titleSmall)
            if (record.feeling == "부담됐어요") Text("같은 항목 · 최대 ${shorterBudget(budget)}분", style = MaterialTheme.typography.bodyLarge)
            PrimaryAction("이 항목으로 준비하기", { onPrepare(next, if (record.feeling == "부담됐어요") shorterBudget(budget) else budget) }, enabled = !busy)
        }
        SecondaryAction("같은 항목 한 번 더", { onRepeat(topic, budget) })
        Text("선택한 느낌 기준 · 운전 능력·안전성 평가 아님", style = MaterialTheme.typography.bodySmall, color = ThriveColors.TextSecondary)
    }
}
