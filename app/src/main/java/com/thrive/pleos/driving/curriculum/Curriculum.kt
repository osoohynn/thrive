package com.thrive.pleos.driving.curriculum

import com.thrive.pleos.driving.storage.SavedDrive

internal enum class CurriculumTrack(val title: String, val chip: String) {
    Beginner("초보자", "초보자 · 연습면허"), Returning("장롱면허", "장롱면허");
    companion object { fun from(value: String?) = entries.firstOrNull { it.name == value } }
}
internal enum class PracticeKind { Stationary, Road, Destination, Parking, Night, Rain }
internal data class PracticeTopic(val id: String, val title: String, val kind: PracticeKind, val points: List<String>)
internal data class PracticeGroup(val title: String, val topics: List<PracticeTopic>)
internal data class PracticeCategory(val title: String, val context: String, val groups: List<PracticeGroup>) {
    val topics get() = groups.flatMap { it.topics }
}
internal data class Curriculum(val track: CurriculumTrack, val categories: List<PracticeCategory>) {
    val topics get() = categories.flatMap { it.topics }
    fun category(topic: PracticeTopic) = categories.first { c -> c.topics.any { it.id == topic.id } }
    fun group(topic: PracticeTopic) = categories.flatMap { it.groups }.first { g -> g.topics.any { it.id == topic.id } }
}
private fun group(prefix: String, title: String, kind: PracticeKind, points: List<String>, vararg titles: String) =
    PracticeGroup(title, titles.mapIndexed { i, text -> PracticeTopic("$prefix${i + 1}", text, kind, points) })

internal val beginnerCurriculum = Curriculum(CurriculumTrack.Beginner, listOf(
    PracticeCategory("차량 기본 조작 및 기초 감각 익히기", "안전지대·공터", listOf(
        group("b11", "차량 장치 조작 및 시동·출발", PracticeKind.Stationary,
            listOf("주차 상태에서 차량 설명서와 장치 위치를 확인해요.", "브레이크를 밟고 계기판의 기어·경고등을 확인해요.", "미러와 시야를 맞추고 사용한 장치를 원래 상태로 돌려요."),
            "시동 켜기/끄기 및 계기판(경고등, 기어 상태) 확인하기",
            "방향지시등, 와이퍼, 비상등, 전조등 조작 및 위치 익히기",
            "사이드미러 및 룸미러 시야 맞추기"),
        group("b12", "페달 감각 및 저속 주행", PracticeKind.Road,
            listOf("동승자와 함께 주변이 비어 있는 연습 공간을 확인해요.", "페달을 천천히 조작하며 속도와 정지 지점을 확인해요.", "기어와 주차 장치의 작동 상태를 계기판에서 확인해요."),
            "악셀과 브레이크 부드럽게 밟기 (급정거·울컥임 방지)",
            "시속 20~30km 유지하며 정지선 맞추어 정지하기",
            "기어 변속(P-R-N-D) 및 오토홀드/사이드브레이크(전자식 파킹) 연동 감각 익히기"),
    )),
    PracticeCategory("기초 주행 및 핸들링", "저밀도 도로", listOf(
        group("b21", "차선 유지 및 직진·곡선 주행", PracticeKind.Road,
            listOf("진행할 차선과 도로 표지·신호를 먼저 확인해요.", "코너 진입 전에 감속하고 진행 방향의 보행자를 확인해요.", "먼 곳을 보며 핸들을 부드럽게 돌리고 차선에 맞춰 풀어요."),
            "차선 중앙 유지하며 흔들리지 않고 직진하기",
            "완만한 우회전·좌회전 코너링 핸들링 감각 익히기",
            "유턴 구역 진입 및 핸들 감기·풀기 타이밍 연습하기"),
        group("b22", "속도 제어 및 방어 운전 기초", PracticeKind.Road,
            listOf("제한 속도 표지판과 어린이 보호구역을 확인해요.", "교차로에 들어가기 전에 미리 속도를 줄여요.", "앞차와 간격을 유지하며 정지선 앞에서 멈춰요."),
            "어린이 보호구역 및 규정 속도 표지판 인식하며 주행하기",
            "교차로 진입 전 미리 속도 줄이기 (엔진브레이크 및 풋브레이크 감각)"),
    )),
    PracticeCategory("실전 도로 적응 및 시험 코스 대비", "실제 도로", listOf(
        group("b31", "차선 변경 및 합류 연습", PracticeKind.Road,
            listOf("미러와 사각지대, 변경 가능한 차선인지 먼저 확인해요.", "미리 방향지시등을 켜고 주변 차량과의 간격을 살펴요.", "충분한 공간이 있을 때 차선 안으로 부드럽게 이동해요."),
            "백미러·사각지대 확인 후 3초 전 방향지시등 켜기",
            "인접 차선 차량과의 거리감 파악하며 부드럽게 진입하기",
            "점선 구간에서의 안전한 차선 변경 완료하기"),
        group("b32", "교차로 및 신호등 대처", PracticeKind.Road,
            listOf("신호와 표지판, 교차로 안의 여유 공간을 확인해요.", "보행자와 맞은편 차량을 살피고 필요한 곳에서 일시정지해요.", "황색 신호에 무리하게 진입하거나 교차로 안을 막지 않아요."),
            "비보호 좌회전 신호 체계 이해하고 진입하기",
            "우회전 시 횡단보도 일시정지 및 보행자 확인 후 통과하기",
            "황색 신호 딜레마 존 대처 및 교차로 꼬리물기 예방 주행"),
    )),
))
internal val returningCurriculum = Curriculum(CurriculumTrack.Returning, listOf(
    PracticeCategory("심리적 불안 해소 및 생활 경로 적응", "가벼운 동행 모드", listOf(
        group("r11", "익숙한 동네 및 저밀도 생활 경로", PracticeKind.Road,
            listOf("익숙한 목적지와 돌아올 길을 먼저 확인해요.", "표지판과 주변 차량을 살피며 도로 흐름에 맞춰 이동해요.", "목적지에 도착하면 안전한 곳에 정차해요."),
            "주말 오전 차량이 적은 시간대 집 주변 1~2km 주행하기",
            "집 앞 편의점 및 자주 가는 근처 산책로 코스 다녀오기"),
        group("r12", "출퇴근/정기 방문 목적지 사전 답사", PracticeKind.Destination,
            listOf("실제로 방문할 목적지와 진입 방향을 확인해요.", "차선 변경이 필요한 구간과 낯선 진입로를 미리 살펴요.", "도착지 주차장 입구와 돌아올 경로를 확인해요."),
            "주말에 실제 출근 경로(회사 방향) 미리 가보기 (코스 비교 활용)",
            "출퇴근 시간대 정체 구간 및 낯선 진입로 미리 살펴보기"),
    )),
    PracticeCategory("도심 복잡 환경 및 돌발 상황 대처", "도심·간선도로", listOf(
        group("r21", "고밀도 도심 및 이면도로 주행", PracticeKind.Road,
            listOf("보행자·자전거·주정차 차량이 있는 곳에서는 미리 감속해요.", "좁은 구간에서는 맞은편 차량과 통과할 공간을 확인해요.", "대형 차량의 사각지대에 오래 머물지 않아요."),
            "불법 주정차가 많은 좁은 골목길 맞은편 차량 교행하기",
            "오토바이, 보행자, 자전거가 많은 번화가 교차로 통과하기",
            "대형 버스/트럭 옆 차선 주행 시 사각지대 주의하며 달리기"),
        group("r22", "간선도로 및 자동차전용도로 진입", PracticeKind.Road,
            listOf("도로 제한 속도와 진입·진출 방향을 확인해요.", "가속 차선에서 주변 흐름과 합류할 간격을 살펴요.", "무리하게 끼어들지 않고 앞차와 거리를 유지해요."),
            "도심 간선도로(시속 60~70km) 흐름에 맞춰 속도 올리기",
            "진출입로(가감속 차선)를 통한 자동차전용도로 합류 연습하기"),
    )),
    PracticeCategory("주차 마스터 및 자립 운전", "주차장·야간·빗길", listOf(
        group("r31", "주차장 진입 및 공간 감각", PracticeKind.Parking,
            listOf("빈 구역과 주변 확인 · 칸 앞뒤 공간과 보행자를 먼저 살펴요.", "사이드미러로 각도 확인 · 후방 카메라와 양쪽 미러를 함께 살펴요.", "천천히 탈출하기 · 앞뒤 공간과 지나가는 차량을 확인해요."),
            "마트·백화점 나선형 주차장 진입 및 요금소(하이패스/발권) 통과하기",
            "전면 주차 및 후방 카메라만 의존하지 않고 사이드미러로 각도 맞추기",
            "가장 까다로운 평행 주차 및 T자(후방) 주차 공식 적용해 탈출하기"),
        PracticeGroup("야간 및 악천후 주행 심화", listOf(
            PracticeTopic("r321", "가로등이 적은 야간 국도 및 도심 주행 시야 확보 연습", PracticeKind.Night,
                listOf("전조등과 미러 상태를 출발 전에 확인해요.", "보이는 거리 안에서 멈출 수 있도록 속도를 줄여요.", "맞은편 차량과 보행자를 살피고 눈부심을 피하며 이동해요.")),
            PracticeTopic("r322", "비 오는 날(와이퍼 단계별 조작 및 수막현상 주의) 주행 적응하기", PracticeKind.Rain,
                listOf("와이퍼와 전조등, 타이어 상태를 출발 전에 확인해요.", "빗길에서는 속도를 낮추고 앞차와의 간격을 넓혀요.", "급제동·급조향을 피하고 고인 물 구간을 주의해요.")),
        )),
    )),
))
internal fun curriculum(track: CurriculumTrack) = if (track == CurriculumTrack.Beginner) beginnerCurriculum else returningCurriculum
internal fun practiceTopic(id: String?) = (beginnerCurriculum.topics + returningCurriculum.topics).firstOrNull { it.id == id }?.let {
    if (it.id == "b213") it.copy(points = listOf("유턴 표지·신호 먼저 확인 · 허용 구역과 신호를 확인해요.",
        "진입 전 충분히 감속 · 왼쪽 방향지시등을 켜고 맞은편 차량과 보행자를 확인해요.", "핸들 감기 → 풀기 · 진행할 차선에 맞춰 부드럽게 풀어요.")) else it
}
internal fun topicTrack(topic: PracticeTopic) = if (topic.id.startsWith("b")) CurriculumTrack.Beginner else CurriculumTrack.Returning
internal fun completedTopics(records: List<SavedDrive>) = records.filter { it.status == "Completed" }
    .mapNotNull { it.curriculumItemId }.filter { practiceTopic(it) != null }.toSet()
internal fun nextTopic(id: String, feeling: String): PracticeTopic? {
    val topic = practiceTopic(id) ?: return null
    if (feeling == "부담됐어요") return topic
    val topics = curriculum(topicTrack(topic)).topics
    return topics.getOrNull(topics.indexOfFirst { it.id == id } + 1)?.let { practiceTopic(it.id) }
}
internal fun shorterBudget(minutes: Int) = listOf(15, 30, 45).lastOrNull { it < minutes } ?: 15
internal fun requiresCompanion(topic: PracticeTopic) = topicTrack(topic) == CurriculumTrack.Beginner && topic.kind != PracticeKind.Stationary

internal data class PracticeCourse(val id: String, val title: String, val minutes: Int, val km: String, val attributes: String, val direct: Boolean = false)
internal fun practiceCourses(topic: PracticeTopic, maxMinutes: Int, destination: String = ""): List<PracticeCourse> {
    val courses = when {
        topic.kind == PracticeKind.Stationary -> emptyList()
        topic.kind == PracticeKind.Parking -> listOf(
            PracticeCourse("parking-flat", "서울숲 공영주차장", 12, "3.1", "평행 주차 구역 · 평면 주차장 · 평일 오전 10~11시 예시"),
            PracticeCourse("parking-spiral", "성수 대형마트 주차장", 15, "3.8", "T자 주차 · 발권 요금소 · 나선형 진입로", true))
        topic.id == "b213" -> listOf(
            PracticeCourse("uturn-quiet", "유턴 구역이 있는 한적한 길", 18, "4.2", "유턴 구역 2곳 · 저밀도 도로"),
            PracticeCourse("uturn-wide", "넓은 유턴 차로 코스", 26, "7.1", "유턴 구역 1곳 · 넓은 차로", true))
        topic.kind == PracticeKind.Destination -> if (destination.isBlank()) emptyList() else listOf(
            PracticeCourse("destination-gentle", "$destination · 변경 적은 코스", 24, "6.8", "차선 변경 1회 · 목적지 진입로 확인"),
            PracticeCourse("destination-direct", "$destination · 시간 짧은 코스", 19, "5.9", "차선 변경 3회 · 목적지 진입로 확인", true))
        else -> listOf(
            PracticeCourse("${topic.id}-short", "${if (topic.id.startsWith("r11")) "집 주변 생활 경로" else "짧게 익히는 코스"}", 12, "1.8", curriculum(topicTrack(topic)).categories.first { c -> c.topics.any { it.id == topic.id } }.context),
            PracticeCourse("${topic.id}-repeat", "반복 연습 코스", 24, "6.8", "${curriculum(topicTrack(topic)).groupsTitle(topic.id)} · 반복 구간", true))
    }
    return courses.filter { topic.kind == PracticeKind.Destination || it.minutes <= maxMinutes }
}
private fun Curriculum.groupsTitle(id: String) = categories.flatMap { it.groups }.first { it.topics.any { t -> t.id == id } }.title
