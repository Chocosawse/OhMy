package com.ohmy.zfsync.ui.home

import com.ohmy.zfsync.core.model.ConnectionState
import com.ohmy.zfsync.core.model.SyncUiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** What the home screen's status row says. */
sealed interface HomeStatus {
    data class Connected(
        val ssid: String?,
        /** Downloading or about to; with nothing [left] it's up to date. */
        val syncing: Boolean,
        /** Shots still to arrive; 0 while syncing means the count isn't known yet. */
        val left: Int,
    ) : HomeStatus

    data class Connecting(val ssid: String?) : HomeStatus

    /** Set up before, but not connected now: off, out of range, or the connection failed. */
    data object CameraNotFound : HomeStatus

    /** No camera ever connected. */
    data object NotSetUp : HomeStatus
}

fun homeStatus(state: SyncUiState, hasSavedCamera: Boolean): HomeStatus = when (val connection = state.connectionState) {
    is ConnectionState.Connected -> HomeStatus.Connected(
        ssid = state.cameraSsid,
        syncing = state.pending.isNotEmpty() || state.lookingUp > 0,
        left = state.pending.size,
    )
    ConnectionState.Connecting -> HomeStatus.Connecting(state.cameraSsid)
    is ConnectionState.Error -> HomeStatus.CameraNotFound
    ConnectionState.Disconnected -> if (hasSavedCamera) HomeStatus.CameraNotFound else HomeStatus.NotSetUp
}

/** Whether the connection card should open on its own: there's no camera to sync with. */
val HomeStatus.needsSetup: Boolean get() = this == HomeStatus.CameraNotFound || this == HomeStatus.NotSetUp

/**
 * How far the current batch of downloads has got, 0 to 1, or null when its size isn't known yet
 * (new shots found, their details still being read). Full when there's nothing to download,
 * connected or not.
 */
fun syncProgress(state: SyncUiState): Float? {
    if (state.connectionState !is ConnectionState.Connected) return 1f
    if (state.pending.isEmpty()) return if (state.lookingUp > 0) null else 1f
    val total = state.pending.size + state.finishedInBatch
    return state.finishedInBatch.toFloat() / total
}

/** When the last sync was: its time ("8:20 PM") if that was today, otherwise its date ("NOV 20"). */
fun lastSyncText(millis: Long, today: LocalDate, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
    val at = Instant.ofEpochMilli(millis).atZone(zone)
    val formatter = if (at.toLocalDate() == today) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    } else {
        DateTimeFormatter.ofPattern("MMM d", locale)
    }
    return formatter.format(at).uppercase(locale)
}

/** A file's number, for a pending tile: "DSC_3961.JPG" gives "3961"; a name without one, itself without its extension. */
fun fileNumber(filename: String): String {
    val base = filename.substringBeforeLast('.')
    return Regex("""\d+""").findAll(base).lastOrNull()?.value ?: base
}

/** A file's name as spoken and shown in descriptions: without its extension. */
fun fileTitle(filename: String): String = filename.substringBeforeLast('.')
