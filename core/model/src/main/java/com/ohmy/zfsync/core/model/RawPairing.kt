package com.ohmy.zfsync.core.model

import android.net.Uri
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

/**
 * The album's files (each a JPEG or a RAW, in order) as the app shows them, the same as adding
 * each with [withFile] -- but in one pass, looking matches up by name, so a device album of
 * thousands of photos pairs in linear time rather than quadratic.
 */
fun pairRawFiles(files: List<SyncedPhoto>): List<SyncedPhoto> {
    val album = ArrayList<SyncedPhoto>(files.size)
    // Indices into [album] of the items whose name (lowercased, without extension) is the key.
    val byBaseName = HashMap<String, MutableList<Int>>()
    val seen = HashSet<Uri>()
    for (file in files) {
        if (!seen.add(file.uri)) continue
        val candidates = byBaseName.getOrPut(file.filename.baseName().lowercase()) { mutableListOf() }
        val isRaw = RawFormats.isRaw(file.filename)
        val match = candidates.firstOrNull { index ->
            val other = album[index]
            abs(other.savedAtMillis - file.savedAtMillis) <= PAIRING_WINDOW_MILLIS &&
                if (isRaw) other.rawUri == null else other.isRawOnly()
        }
        when {
            match == null -> {
                candidates += album.size
                album += if (isRaw) file.copy(rawUri = file.uri) else file
            }
            isRaw -> album[match] = album[match].copy(rawUri = file.uri)
            else -> album[match] = file.copy(rawUri = album[match].uri)
        }
    }
    return album
}
