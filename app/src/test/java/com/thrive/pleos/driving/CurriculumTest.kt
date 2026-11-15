package com.thrive.pleos.driving

import com.thrive.pleos.driving.curriculum.*
import com.thrive.pleos.driving.storage.SavedDrive
import org.junit.Assert.*
import org.junit.Test

class CurriculumTest {
    @Test fun catalogHasAllFigmaItemsAndStableParents() {
        val all = listOf(beginnerCurriculum, returningCurriculum)
        assertEquals(listOf(17, 14), all.map { it.topics.size })
        assertEquals(31, all.flatMap { it.topics }.map { it.id }.toSet().size)
        all.forEach { catalog ->
            assertEquals(3, catalog.categories.size)
            assertEquals(6, catalog.categories.sumOf { it.groups.size })
            catalog.topics.forEach { raw ->
                val topic = checkNotNull(practiceTopic(raw.id))
                assertEquals(catalog.track, topicTrack(topic))
                assertTrue(catalog.category(topic).topics.any { it.id == topic.id })
                assertTrue(catalog.group(topic).topics.any { it.id == topic.id })
                assertEquals(3, topic.points.size)
            }
        }
    }
    @Test fun stationaryAndCompanionRequirementsStaySeparate() {
        val stationary = beginnerCurriculum.topics.filter { it.kind == PracticeKind.Stationary }
        assertEquals(3, stationary.size)
        stationary.forEach { assertFalse(requiresCompanion(it)); assertTrue(practiceCourses(it, 30).isEmpty()) }
        beginnerCurriculum.topics.filter { it.kind != PracticeKind.Stationary }.forEach { assertTrue(requiresCompanion(it)) }
        returningCurriculum.topics.forEach { assertFalse(requiresCompanion(it)) }
    }
    @Test fun examplesRespectTopicTimeAndDestination() {
        val uturn = checkNotNull(practiceTopic("b213"))
        assertTrue(practiceCourses(uturn, 15).isEmpty())
        assertEquals(listOf(18, 26), practiceCourses(uturn, 30).map { it.minutes })
        assertEquals(listOf("parking-flat", "parking-spiral"), practiceCourses(checkNotNull(practiceTopic("r313")), 15).map { it.id })
        val destination = checkNotNull(practiceTopic("r121"))
        assertTrue(practiceCourses(destination, 15).isEmpty())
        assertEquals(2, practiceCourses(destination, 15, "성수 오피스").size)
        for (topic in beginnerCurriculum.topics + returningCurriculum.topics) {
            if (topic.kind != PracticeKind.Destination) assertTrue(practiceCourses(topic, 15).all { it.minutes <= 15 })
        }
    }
    private fun record(id: String, topic: String?, status: String = "Completed") = SavedDrive(id = id, mode = "Practice", goal = "Turns", route = "Gentle", destination = "예시", plannedMinutes = 30, startedAtEpochMs = 0,
        curriculumItemId = topic, status = status, activeSlot = if (status == "Completed") null else 1)
    @Test fun completionIsUniqueAndDoesNotCountActiveOrLegacyRows() {
        val records = listOf(record("1", "b213"), record("2", "b213"), record("3", "r121"), record("4", null), record("5", "b221", "Recording"), record("6", "b222", "Interrupted"), record("7", "unknown"))
        assertEquals(setOf("b213", "r121"), completedTopics(records))
        assertEquals(setOf("r121"), completedTopics(records.filter { it.id != "1" && it.id != "2" }))
    }
    @Test fun reportMovesAcrossGroupsOrRepeatsAtShorterBudget() {
        assertEquals("b221", nextTopic("b213", "여유 있었어요")?.id)
        assertEquals("b213", nextTopic("b213", "부담됐어요")?.id)
        assertEquals(listOf(15, 15, 30), listOf(15, 30, 45).map(::shorterBudget))
        assertNull(nextTopic("b323", "보통이에요"))
        assertNull(nextTopic("r322", "여유 있었어요"))
    }
    @Test fun conditionsRequireFreshActualForecastAndCorrectKind() {
        val now = 2_000_000L
        val dryDay = PracticeConditions(now, false, 0.0)
        assertFalse(dryDay.permits(PracticeKind.Night, now))
        assertFalse(dryDay.permits(PracticeKind.Rain, now))
        assertTrue(PracticeConditions(now, true, 0.3).permits(PracticeKind.Rain, now))
        assertTrue(PracticeConditions(now, true, 0.0).permits(PracticeKind.Night, now))
        assertFalse(PracticeConditions(now - 1_800_001, true, 1.0).permits(PracticeKind.Rain, now))
        assertFalse(PracticeConditions(now + 1, true, 1.0).permits(PracticeKind.Night, now))
    }
}
