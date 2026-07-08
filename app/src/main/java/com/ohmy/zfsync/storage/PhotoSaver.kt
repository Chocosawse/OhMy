package com.ohmy.zfsync.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.IOException
import java.io.OutputStream

/** Saves downloaded JPEGs into the shared Pictures/OhMy album via MediaStore (scoped storage). */
class PhotoSaver(private val context: Context) {

    fun createPendingImageUri(filename: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/OhMy")
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
}
