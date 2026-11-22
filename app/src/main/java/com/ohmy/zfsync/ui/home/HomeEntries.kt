package com.ohmy.zfsync.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ohmy.zfsync.core.model.PendingPhoto
import com.ohmy.zfsync.core.model.SyncedPhoto
import com.ohmy.zfsync.core.model.takenAt
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One item of the home screen's scrolling content, in order. The list and the grid lay out the
 * same entries (with the same keys), so switching layouts can keep the same photo at the top.
 */
@Immutable
sealed interface HomeEntry {
    val key: Any
    val contentType: String

    /** Everything above the photos: header, status, connection card, library row. Always first. */
    data object Header : HomeEntry {
        override val key get() = "header"
        override val contentType get() = "header"
    }

    data class SectionHeader(val title: SectionTitle, val first: Boolean) : HomeEntry {
        override val key get() = "section-$title"
        override val contentType get() = "section"
    }

    data object Empty : HomeEntry {
        override val key get() = "empty"
        override val contentType get() = "empty"
    }

    /** [index] is the photo's position in the viewer's order (photosNewestFirst). */
    data class Photo(val photo: SyncedPhoto, val index: Int) : HomeEntry {
        override val key: Any get() = photo.uri
        override val contentType get() = "photo"
    }

    data class Pending(val pending: PendingPhoto) : HomeEntry {
        override val key get() = "pending-${pending.handle}"
        override val contentType get() = "pending"
    }
}

/**
 * The header, then [ordered] (newest first, the viewer's order) with the shots still on their
 * way sorted in among them, in sections as of [today].
 */
fun homeEntries(ordered: List<SyncedPhoto>, pending: List<PendingPhoto>, today: LocalDate, now: LocalDateTime): List<HomeEntry> {
    val items: List<HomeEntry> = pending.map { HomeEntry.Pending(it) } + ordered.mapIndexed { index, photo -> HomeEntry.Photo(photo, index) }
    fun takenAt(entry: HomeEntry): LocalDateTime = when (entry) {
        is HomeEntry.Photo -> entry.photo.takenAt()
        // A shot the camera gave no time for has only just been taken.
        is HomeEntry.Pending -> entry.pending.capturedAt ?: now
        else -> now
    }
    // Stable: photos keep their order (already newest first), and pending shots go before photos taken at the same moment.
    val sorted = items.sortedByDescending(::takenAt)
    return buildList {
        add(HomeEntry.Header)
        if (sorted.isEmpty()) add(HomeEntry.Empty)
        sectionsOf(sorted, today, ::takenAt).forEachIndexed { index, section ->
            add(HomeEntry.SectionHeader(section.title, first = index == 0))
            addAll(section.items)
        }
    }
}

/** Today's date, changing at midnight (so today's sections and the header's date move on with it). */
@Composable
fun rememberToday(): LocalDate {
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            // A little past midnight, so "now" is safely on the new day; and at least once a
            // minute, since a delay can run late while the phone sleeps (and the time zone can change).
            delay(minOf(millisUntilNextMidnight(LocalDateTime.now()) + 500, 60_000L))
            today = LocalDate.now()
        }
    }
    return today
}
