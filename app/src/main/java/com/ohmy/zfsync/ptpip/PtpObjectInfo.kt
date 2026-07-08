package com.ohmy.zfsync.ptpip

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
