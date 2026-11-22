package com.ohmy.zfsync.ui.components

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * The home screen's photo selection, shared by its list and grid. Select mode is on while
 * anything is selected, or after the Select button turns it on with nothing selected yet; a
 * long-press turns it on by selecting, and deselecting the last photo then leaves it.
 */
@Stable
class HomeSelection(selected: Set<Uri> = emptySet(), startedEmpty: Boolean = false) {
    var selected by mutableStateOf(selected)
        private set

    /** Turned on by the Select button, so the mode stays with nothing selected. */
    private var startedEmpty by mutableStateOf(startedEmpty)

    val active: Boolean get() = startedEmpty || selected.isNotEmpty()

    /** The Select button: select mode, nothing selected yet. */
    fun start() {
        startedEmpty = true
    }

    fun toggle(uri: Uri) {
        selected = if (uri in selected) selected - uri else selected + uri
    }

    fun set(uris: Set<Uri>) {
        selected = uris
    }

    /** Leaves select mode. */
    fun clear() {
        selected = emptySet()
        startedEmpty = false
    }

    /** Drops anything no longer in the album (deleted elsewhere, say). */
    fun retainAll(present: Set<Uri>) {
        if (!present.containsAll(selected)) selected = selected.filterTo(HashSet()) { it in present }
    }

    companion object {
        val Saver = listSaver<HomeSelection, Any>(
            save = { listOf(it.startedEmpty) + it.selected.toList() },
            restore = { saved -> HomeSelection(saved.drop(1).map { it as Uri }.toSet(), saved.first() as Boolean) },
        )
    }
}

@Composable
fun rememberHomeSelection(): HomeSelection = rememberSaveable(saver = HomeSelection.Saver) { HomeSelection() }
