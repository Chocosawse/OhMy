package com.ohmy.zfsync.ptpip

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/** A single framed PTP/IP packet: 4-byte little-endian total length, 4-byte type, then payload. */
data class PtpIpPacket(val type: Int, val payload: ByteArray)

private fun readFully(input: InputStream, count: Int): ByteArray {
    val buffer = ByteArray(count)
    var offset = 0
    while (offset < count) {
        val read = input.read(buffer, offset, count - offset)
        if (read < 0) throw EOFException("PTP/IP socket closed after $offset of $count bytes")
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
    return PtpIpPacket(type, payload)
}

fun writePtpIpPacket(output: OutputStream, type: Int, payload: ByteArray) {
    val totalLength = 8 + payload.size
    val header = PtpByteWriter().writeU32(totalLength.toLong()).writeU32(type.toLong()).toByteArray()
    output.write(header)
    if (payload.isNotEmpty()) output.write(payload)
    output.flush()
}
