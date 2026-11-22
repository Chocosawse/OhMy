package com.ohmy.zfsync.ptpip

import com.ohmy.zfsync.core.model.RawFormats
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Parsed result of a GetObjectInfo (0x1008) data phase. */
data class PtpObjectInfo(
    val storageId: Long,
    val objectFormat: Int,
    val compressedSize: Long,
    val filename: String,
    val captureDate: String,
) {
    val isJpeg: Boolean
        get() = objectFormat == PtpObjectFormatCode.EXIF_JPEG ||
            filename.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg")

    /** A camera RAW (NEF and the like). By name: cameras report RAWs under vendor or undefined format codes. */
    val isRaw: Boolean
        get() = RawFormats.isRaw(filename)

    /** When it was taken, by the camera's clock; null if the camera left it out. */
    val capturedAt: LocalDateTime? get() = parsePtpDateTime(captureDate)

    /** The name to save it under: the camera's, or one made up from the handle if it gave none. */
    fun fileName(handle: Long): String = filename.ifBlank { "IMG_$handle.jpg" }

    companion object {
        fun parse(data: ByteArray): PtpObjectInfo {
            val r = PtpByteReader(data)
            val storageId = r.readU32()
            val objectFormat = r.readU16()
            r.readU16() // ProtectionStatus
            val compressedSize = r.readU32()
            r.readU16() // ThumbFormat
            r.readU32() // ThumbCompressedSize
            r.readU32() // ThumbPixWidth
            r.readU32() // ThumbPixHeight
            r.readU32() // ImagePixWidth
            r.readU32() // ImagePixHeight
            r.readU32() // ImageBitDepth
            r.readU32() // ParentObject
            r.readU16() // AssociationType
            r.readU32() // AssociationDesc
            r.readU32() // SequenceNumber
            val filename = r.readPtpString()
            val captureDate = r.readPtpString()
            return PtpObjectInfo(storageId, objectFormat, compressedSize, filename, captureDate)
        }
    }
}

private val PtpDateTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

/**
 * PTP's date-time string, "YYYYMMDDThhmmss" with optional tenths and a time zone after it; the
 * Zf sends none, so it's read as local time. Null for an empty or malformed value.
 */
internal fun parsePtpDateTime(value: String): LocalDateTime? =
    if (value.length < 15) null else runCatching { LocalDateTime.parse(value.substring(0, 15), PtpDateTime) }.getOrNull()
