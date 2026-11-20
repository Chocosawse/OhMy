package com.ohmy.zfsync.core.model

import android.net.Uri

/** A photo album on the device (a MediaStore bucket, i.e. a folder of images). */
data class Album(
    val id: String,
    val name: String,
    /** Its newest photo. */
    val coverUri: Uri,
    val photoCount: Int,
)
