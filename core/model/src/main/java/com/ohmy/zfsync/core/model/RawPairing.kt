package com.ohmy.zfsync.core.model

import kotlin.math.abs

/**
 * How far apart a JPEG and RAW can have been saved and still be the same shot. Cameras number
 * files in a cycle (DSC_0001 again after DSC_9999), so a name match alone isn't enough; a
 * RAW+JPEG pair is downloaded within one sync, well inside this.
 */
private const val PAIRING_WINDOW_MILLIS = 30 * 60 * 1000L

private fun String.baseName() = substringBeforeLast('.')

private fun SyncedPhoto.isRawOnly() = rawUri == uri

/**
 * Adds a just-saved file to the album as the app shows it: one item per shot. A RAW joins its
 * shot's JPEG (as [SyncedPhoto.rawUri]); a JPEG takes over a RAW-only item of the same shot; with
 * no match, either becomes an item of its own (a RAW-only one has [SyncedPhoto.rawUri] == uri).
 * Files already in the album are ignored.
 */
fun List<SyncedPhoto>.withFile(file: SyncedPhoto): List<SyncedPhoto> {
    if (any { it.uri == file.uri || it.rawUri == file.uri }) return this
    val base = file.filename.baseName()
    fun sameShot(other: SyncedPhoto) =
        other.filename.baseName().equals(base, ignoreCase = true) &&
            abs(other.savedAtMillis - file.savedAtMillis) <= PAIRING_WINDOW_MILLIS
    return if (RawFormats.isRaw(file.filename)) {
        val jpeg = indexOfFirst { it.rawUri == null && sameShot(it) }
        if (jpeg >= 0) {
            toMutableList().also { it[jpeg] = it[jpeg].copy(rawUri = file.uri) }
        } else {
            this + file.copy(rawUri = file.uri)
        }
    } else {
        val rawOnly = indexOfFirst { it.isRawOnly() && sameShot(it) }
        if (rawOnly >= 0) {
            toMutableList().also { it[rawOnly] = file.copy(rawUri = it[rawOnly].uri) }
        } else {
            this + file
        }
    }
}

/** The album's files (each a JPEG or a RAW, in order) as the app shows them: see [withFile]. */
fun pairRawFiles(files: List<SyncedPhoto>): List<SyncedPhoto> =
    files.fold(emptyList()) { album, file -> album.withFile(file) }
