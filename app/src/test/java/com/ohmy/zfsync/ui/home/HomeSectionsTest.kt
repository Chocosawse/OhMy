package com.ohmy.zfsync.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale

class HomeSectionsTest {

    private val today = LocalDate.of(2026, 11, 21)

    private fun at(hour: Int, minute: Int, date: LocalDate = today): LocalDateTime = date.atTime(hour, minute)

    @Test
    fun `today's parts of the day start and end on the minute`() {
        val boundaries = mapOf(
            LocalTime.of(0, 0) to DayPart.LateNight,
            LocalTime.of(4, 59) to DayPart.LateNight,
            LocalTime.of(5, 0) to DayPart.Morning,
            LocalTime.of(11, 59) to DayPart.Morning,
            LocalTime.of(12, 0) to DayPart.Afternoon,
            LocalTime.of(16, 59) to DayPart.Afternoon,
            LocalTime.of(17, 0) to DayPart.Evening,
            LocalTime.of(20, 59) to DayPart.Evening,
            LocalTime.of(21, 0) to DayPart.Night,
            LocalTime.of(23, 59) to DayPart.Night,
        )
        for ((time, part) in boundaries) {
            assertEquals("$time", part, DayPart.of(time))
            assertEquals("$time", SectionTitle.Today(part), sectionTitleOf(today.atTime(time), today))
        }
        // The last second of a part is still in it.
        assertEquals(DayPart.LateNight, DayPart.of(LocalTime.of(4, 59, 59)))
    }

    @Test
    fun `earlier days get a section each, today's empty parts none`() {
        val shots = listOf(
            at(20, 15),
            at(8, 40),
            at(8, 5),
            at(23, 50, today.minusDays(1)),
            at(9, 0, today.minusDays(1)),
            at(14, 0, today.minusDays(3)),
        )
        val sections = sectionsOf(shots, today) { it }

        assertEquals(
            listOf(
                SectionTitle.Today(DayPart.Evening),
                SectionTitle.Today(DayPart.Morning),
                SectionTitle.Day(today.minusDays(1)),
                SectionTitle.Day(today.minusDays(3)),
            ),
            sections.map { it.title },
        )
        assertEquals(listOf(2, 2), sections.subList(1, 3).map { it.items.size })
    }

    @Test
    fun `no photos, no sections`() {
        assertEquals(emptyList<Section<LocalDateTime>>(), sectionsOf(emptyList<LocalDateTime>(), today) { it })
    }

    @Test
    fun `day titles name the weekday and date, with the year only when it isn't this one`() {
        assertEquals("Friday, November 20", dayTitle(LocalDate.of(2026, 11, 20), today, Locale.US))
        assertEquals("Thursday, January 1", dayTitle(LocalDate.of(2026, 1, 1), today, Locale.US))
        assertEquals("Saturday, December 20, 2025", dayTitle(LocalDate.of(2025, 12, 20), today, Locale.US))
        // On New Year's Day, yesterday is last year.
        assertEquals("Wednesday, December 31, 2025", dayTitle(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1), Locale.US))
    }

    @Test
    fun `at midnight today's photos become yesterday's`() {
        val shots = listOf(at(22, 30), at(9, 15))
        val before = sectionsOf(shots, today) { it }.map { it.title }
        val after = sectionsOf(shots, today.plusDays(1)) { it }.map { it.title }

        assertEquals(listOf(SectionTitle.Today(DayPart.Night), SectionTitle.Today(DayPart.Morning)), before)
        assertEquals(listOf(SectionTitle.Day(today)), after)
    }

    @Test
    fun `a shot just after midnight starts today's late night`() {
        val tomorrow = today.plusDays(1)
        val shots = listOf(at(0, 1, tomorrow), at(23, 59))
        assertEquals(
            listOf(SectionTitle.Today(DayPart.LateNight), SectionTitle.Day(today)),
            sectionsOf(shots, tomorrow) { it }.map { it.title },
        )
    }

    @Test
    fun `the wait for midnight`() {
        assertEquals(60_000L, millisUntilNextMidnight(at(23, 59)))
        assertEquals(24 * 3_600_000L, millisUntilNextMidnight(at(0, 0)))
        assertEquals(1L, millisUntilNextMidnight(today.atTime(LocalTime.of(23, 59, 59, 999_000_000))))
    }

    @Test
    fun `the kicker is the weekday and date in capitals`() {
        assertEquals("SATURDAY · NOVEMBER 21", kickerText(today, Locale.US))
    }
}
