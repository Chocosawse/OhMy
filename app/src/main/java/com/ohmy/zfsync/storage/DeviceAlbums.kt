package com.ohmy.zfsync.storage

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.ohmy.zfsync.core.model.Album
import com.ohmy.zfsync.core.model.ExposureInfo
import com.ohmy.zfsync.core.model.SyncedPhoto
import com.ohmy.zfsync.core.model.pairRawFiles

/**
 * The device's photo albums (MediaStore buckets), for browsing beyond the app's own. What's
 * visible depends on the photos permission: everything with full access, only the photos the user
 * picked with partial access (Android 14+), and only the app's own album without either.
 */
class DeviceAlbums(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val photoSaver = PhotoSaver(context.applicationContext)

    /** Every album with at least one photo, the most recently added to first. */
    fun loadAlbums(): List<Album> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        // Newest first, so each bucket's first row is its cover and buckets come out in order.
        val albums = LinkedHashMap<String, Album>()
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Images.Media.IS_PENDING} = 0",
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val bucket = cursor.getString(bucketCol) ?: continue
                val existing = albums[bucket]
                albums[bucket] = existing?.copy(photoCount = existing.photoCount + 1) ?: Album(
                    id = bucket,
                    name = cursor.getString(nameCol) ?: "",
                    coverUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idCol)),
                    photoCount = 1,
                )
            }
        }
        return albums.values.toList()
    }

    /**
     * An album's photos, oldest first, one item per shot (RAWs paired with their JPEGs). Without
     * EXIF, which for a big album would be thousands of file reads: see [readExposure].
     */
    fun loadAlbum(albumId: String): List<SyncedPhoto> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val files = mutableListOf<SyncedPhoto>()
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Images.Media.BUCKET_ID} = ? AND ${MediaStore.Images.Media.IS_PENDING} = 0",
            arrayOf(albumId),
            "${MediaStore.Images.Media.DATE_ADDED} ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                files += SyncedPhoto(
                    filename = cursor.getString(nameCol) ?: "",
                    savedAtMillis = cursor.getLong(dateCol) * 1000,
                    uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idCol)),
                )
            }
        }
        return pairRawFiles(files)
    }

    /**
     * A photo another app asked to view, ready for the viewer: in its album (newest first, at its
     * place) when it's in the device's photo library and the app can see that album, otherwise on
     * its own. [ExternalPhoto.inLibrary] says whether it can be deleted (through the system).
     */
    fun openExternal(uri: Uri): ExternalPhoto {
        val inLibrary = uri.authority == MediaStore.AUTHORITY
        if (inLibrary) {
            val bucket = runCatching {
                resolver.query(uri, arrayOf(MediaStore.Images.Media.BUCKET_ID), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()
            if (bucket != null) {
                val album = runCatching { loadAlbum(bucket) }.getOrDefault(emptyList()).asReversed()
                // The same item can come as different URI forms (external vs external_primary), so by ID.
                val id = uri.lastPathSegment?.toLongOrNull()
                val index = album.indexOfFirst {
                    it.uri.lastPathSegment?.toLongOrNull() == id || it.rawUri?.lastPathSegment?.toLongOrNull() == id
                }
                if (id != null && index >= 0) return ExternalPhoto(album, index, inLibrary = true)
            }
        }
        val name = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
        return ExternalPhoto(listOf(SyncedPhoto(filename = name, savedAtMillis = System.currentTimeMillis(), uri = uri)), 0, inLibrary)
    }

    /** One photo's EXIF, read when it's actually looked at. */
    fun readExposure(uri: Uri): ExposureInfo? = photoSaver.readExposure(uri)

    /**
     * Asks the user (a system dialog) to delete a photo another app owns, with its RAW: the only
     * way an app may, from Android 11. Null before that, where it can't be done this way.
     */
    fun deleteRequest(photo: SyncedPhoto): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val uris = listOfNotNull(photo.uri, photo.rawUri).distinct()
        return MediaStore.createDeleteRequest(resolver, uris).intentSender
    }
}

/** What [DeviceAlbums.openExternal] found: the photos to show, newest first, and which to start on. */
data class ExternalPhoto(val photos: List<SyncedPhoto>, val initialIndex: Int, val inLibrary: Boolean)
