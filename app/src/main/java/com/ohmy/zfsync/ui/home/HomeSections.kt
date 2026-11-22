package com.ohmy.zfsync.ui.home

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Today's photos are grouped by the part of the day they were taken in. */
enum class DayPart(val startHour: Int) {
    /** 00:00 to 04:59. */
    LateNight(0),

    /** 05:00 to 11:59. */
    Morning(5),

    /** 12:00 to 16:59. */
    Afternoon(12),

    /** 17:00 to 20:59. */
    Evening(17),

    /** 21:00 to 23:59. */
    Night(21),
    ;

    companion object {
        fun of(time: LocalTime): DayPart = entries.last { time.hour >= it.startHour }
    }
}

/** What a section of the album is headed: a part of today, or a whole earlier (or, by a fast camera clock, later) day. */
sealed interface SectionTitle {
    data class Today(val part: DayPart) : SectionTitle

    data class Day(val date: LocalDate) : SectionTitle
}

data class Section<T>(val title: SectionTitle, val items: List<T>)

/** The section a shot taken at [takenAt] belongs in, as of [today]. */
fun sectionTitleOf(takenAt: LocalDateTime, today: LocalDate): SectionTitle =
    if (takenAt.toLocalDate() == today) SectionTitle.Today(DayPart.of(takenAt.toLocalTime())) else SectionTitle.Day(takenAt.toLocalDate())

/**
 * Splits [items], already newest first, into sections: today's by part of the day, older ones by
 * day. Only sections with something in them; an item's section depends on [today], so the
 * grouping changes at midnight.
 */
fun <T> sectionsOf(items: List<T>, today: LocalDate, takenAt: (T) -> LocalDateTime): List<Section<T>> {
    val sections = mutableListOf<Section<T>>()
    var title: SectionTitle? = null
    var current = mutableListOf<T>()
    for (item in items) {
        val itemTitle = sectionTitleOf(takenAt(item), today)
        if (itemTitle != title) {
            title?.let { sections += Section(it, current) }
            title = itemTitle
            current = mutableListOf()
        }
        current += item
    }
    title?.let { sections += Section(it, current) }
    return sections
}

/** "Friday, November 20", with ", 2025" only when it isn't [today]'s year. */
fun dayTitle(date: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String {
    val pattern = if (date.year == today.year) "EEEE, MMMM d" else "EEEE, MMMM d, yyyy"
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}

/** The header kicker: "SATURDAY · NOVEMBER 21". */
fun kickerText(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("EEEE '·' MMMM d", locale).format(date).uppercase(locale)

/** How long from [now] until the next midnight, when today's sections and the kicker change. */
fun millisUntilNextMidnight(now: LocalDateTime): Long =
    Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis()
