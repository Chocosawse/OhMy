package com.ohmy.zfsync.storage

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
