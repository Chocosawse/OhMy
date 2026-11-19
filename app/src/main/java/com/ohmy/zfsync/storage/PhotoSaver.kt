package com.ohmy.zfsync.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.ohmy.zfsync.core.model.ExposureInfo
import com.ohmy.zfsync.core.model.SyncedPhoto
import java.io.IOException
import java.io.OutputStream

private val ALBUM_RELATIVE_PATH = "${Environment.DIRECTORY_PICTURES}/OhMy/"

/** Saves downloaded JPEGs into the shared Pictures/OhMy album via MediaStore (scoped storage). */
class PhotoSaver(private val context: Context) {

    /**
     * Every finished photo in the album, oldest first. Without a media-read permission, scoped
     * storage only returns rows this install of the app created -- which is every photo it synced,
     * but not ones from before an uninstall/reinstall or copied in by another app.
     */
    fun loadAll(): List<SyncedPhoto> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND ${MediaStore.Images.Media.IS_PENDING} = 0"
        val photos = mutableListOf<SyncedPhoto>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf(ALBUM_RELATIVE_PATH),
            "${MediaStore.Images.Media.DATE_ADDED} ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idCol))
                photos += SyncedPhoto(
                    filename = cursor.getString(nameCol),
                    savedAtMillis = cursor.getLong(dateCol) * 1000,
                    uri = uri,
                    exposure = readExposure(uri),
                )
            }
        }
        return photos
    }

    /**
     * [uri]'s exposure settings from its EXIF, or null if it has none or can't be read. Zero
     * values count as missing: that's what manual (non-CPU) lenses record for focal length and
     * aperture.
     */
    fun readExposure(uri: Uri): ExposureInfo? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            fun positive(tag: String) = exif.getAttributeDouble(tag, 0.0).takeIf { it > 0.0 }
            ExposureInfo(
                focalLengthMm = positive(ExifInterface.TAG_FOCAL_LENGTH),
                aperture = positive(ExifInterface.TAG_F_NUMBER),
                exposureTimeSeconds = positive(ExifInterface.TAG_EXPOSURE_TIME),
                lensName = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim()?.takeIf { it.isNotEmpty() },
            ).takeIf { it != ExposureInfo() }
        }
    }.getOrNull()

    fun createPendingImageUri(filename: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, ALBUM_RELATIVE_PATH)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore rejected insert for $filename")
    }

    fun openOutputStream(uri: Uri): OutputStream =
        context.contentResolver.openOutputStream(uri) ?: throw IOException("Could not open output stream for $uri")

    fun markComplete(uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
        context.contentResolver.update(uri, values, null, null)
    }

    fun deleteIncomplete(uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    /** Deletes a photo this app previously saved. Own-authored MediaStore rows can be deleted
     *  directly on API 29+, with no extra user confirmation required. */
    fun delete(uri: Uri) {
        context.contentResolver.delete(uri, null, null)
    }
}
