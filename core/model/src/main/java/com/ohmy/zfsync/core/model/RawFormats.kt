package com.ohmy.zfsync.core.model

/** Camera RAW files, recognised by extension: what's a RAW, and its MIME type for MediaStore and other apps. */
object RawFormats {
    private val mimeTypes = mapOf(
        "nef" to "image/x-nikon-nef",
        "nrw" to "image/x-nikon-nrw",
        "dng" to "image/x-adobe-dng",
        "cr2" to "image/x-canon-cr2",
        "cr3" to "image/x-canon-cr3",
        "arw" to "image/x-sony-arw",
        "raf" to "image/x-fuji-raf",
        "orf" to "image/x-olympus-orf",
        "rw2" to "image/x-panasonic-rw2",
        "pef" to "image/x-pentax-pef",
        "srw" to "image/x-samsung-srw",
    )

    /** The RAW MIME type for [filename], or null if it isn't a RAW file. */
    fun mimeType(filename: String): String? = mimeTypes[filename.substringAfterLast('.', "").lowercase()]

    fun isRaw(filename: String): Boolean = mimeType(filename) != null
}
