package com.ohmy.zfsync.ptpip

import android.util.Log
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

private const val TAG = "OhMyPtpIp"
private const val LOG_HEX_LIMIT = 64

private fun ByteArray.toHexPreview(): String {
    val hex = take(LOG_HEX_LIMIT).joinToString(" ") { "%02x".format(it) }
    return if (size > LOG_HEX_LIMIT) "$hex ... (${size} bytes total)" else "$hex (${size} bytes)"
}

/** A single framed PTP/IP packet: 4-byte little-endian total length, 4-byte type, then payload. */
data class PtpIpPacket(val type: Int, val payload: ByteArray)

private fun readFully(input: InputStream, count: Int): ByteArray {
    val buffer = ByteArray(count)
    var offset = 0
    while (offset < count) {
        val read = try {
            input.read(buffer, offset, count - offset)
        } catch (t: Throwable) {
            Log.e(TAG, "read() threw after $offset of $count bytes: $t")
            throw t
        }
        if (read < 0) {
            Log.e(TAG, "socket EOF after $offset of $count bytes (nothing more to read)")
            throw EOFException("PTP/IP socket closed after $offset of $count bytes")
        }
        offset += read
    }
    return buffer
}

fun readPtpIpPacket(input: InputStream): PtpIpPacket {
    val lengthBytes = readFully(input, 4)
    val length = PtpByteReader(lengthBytes).readU32()
    if (length < 8) throw IllegalStateException("Invalid PTP/IP packet length $length")
    val typeBytes = readFully(input, 4)
    val type = PtpByteReader(typeBytes).readU32().toInt()
    val payloadLength = (length - 8).toInt()
    val payload = if (payloadLength > 0) readFully(input, payloadLength) else ByteArray(0)
    Log.d(TAG, "<< recv type=$type length=$length payload=${payload.toHexPreview()}")
    return PtpIpPacket(type, payload)
}

fun writePtpIpPacket(output: OutputStream, type: Int, payload: ByteArray) {
    val totalLength = 8 + payload.size
    val header = PtpByteWriter().writeU32(totalLength.toLong()).writeU32(type.toLong()).toByteArray()
    Log.d(TAG, ">> send type=$type length=$totalLength payload=${payload.toHexPreview()}")
    output.write(header)
    if (payload.isNotEmpty()) output.write(payload)
    output.flush()
    Log.d(TAG, ">> send type=$type flushed")
}
