package com.ohmy.zfsync.ptpip

import android.net.Network
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

private const val TAG = "OhMyPtpIp"

data class PtpEvent(val code: Int, val transactionId: Long, val parameters: LongArray)

data class OperationResult(
    val responseCode: Int,
    val transactionId: Long,
    val parameters: List<Long>,
    val data: ByteArray?,
)

private enum class DataPhase(val wireValue: Long) {
    NONE(0L),
    HOST_TO_DEVICE(1L),
    DEVICE_TO_HOST(2L),
}

class PtpIpProtocolException(message: String) : Exception(message)

/**
 * Minimal PTP/IP (ISO 15740 over TCP) client covering the read-only operations needed to
 * discover and download images from a camera acting as a Wi-Fi access point: OpenSession,
 * GetStorageIDs, GetObjectHandles, GetObjectInfo, GetObject, plus the event channel that
 * reports ObjectAdded as new photos are captured.
 */
class PtpIpClient private constructor(
    private val commandSocket: Socket,
    private val host: String,
    private val network: Network?,
) {
    private val commandIn = commandSocket.getInputStream()
    private val commandOut = commandSocket.getOutputStream()
    private lateinit var eventSocket: Socket
    private lateinit var eventIn: java.io.InputStream
    private var transactionId = 0L
    @Volatile private var eventLoopJob: Job? = null

    private fun nextTransactionId(): Long {
        transactionId += 1
        return transactionId
    }

    suspend fun handshake(friendlyName: String = "OhMy Zf Sync") = withContext(Dispatchers.IO) {
        val guid = randomGuidBytes()

        val initRequest = PtpByteWriter().writeBytes(guid).writeNullTerminatedUtf16(friendlyName).toByteArray()
        writePtpIpPacket(commandOut, PtpIpPacketType.INIT_COMMAND_REQUEST, initRequest)
        val ack = readPtpIpPacket(commandIn)
        if (ack.type == PtpIpPacketType.INIT_FAIL) {
            val reason = PtpByteReader(ack.payload).readU32()
            throw PtpIpProtocolException("Camera rejected Init Command Request (reason=0x${reason.toString(16)})")
        }
        if (ack.type != PtpIpPacketType.INIT_COMMAND_ACK) {
            throw PtpIpProtocolException("Expected Init Command Ack, got packet type ${ack.type}")
        }
        val connectionNumber = PtpByteReader(ack.payload).readU32()
        Log.d(TAG, "command handshake ok, connectionNumber=$connectionNumber")

        // The event connection is only opened now, after the camera hands back a connection
        // number: opening it earlier (in parallel with the command socket) leaves a second TCP
        // connection sitting idle with no recognized init packet while the command handshake is
        // still in flight, which some camera Wi-Fi stacks respond to by resetting it.
        val newEventSocket = openSocket(host, network)
        eventSocket = newEventSocket
        eventIn = newEventSocket.getInputStream()
        val eventOut = newEventSocket.getOutputStream()
        val eventRequest = PtpByteWriter().writeU32(connectionNumber).toByteArray()
        writePtpIpPacket(eventOut, PtpIpPacketType.INIT_EVENT_REQUEST, eventRequest)
        val eventAck = readPtpIpPacket(eventIn)
        if (eventAck.type != PtpIpPacketType.INIT_EVENT_ACK) {
            throw PtpIpProtocolException("Expected Init Event Ack, got packet type ${eventAck.type}")
        }
        Log.d(TAG, "event handshake ok")
    }

    /** Starts a background loop reading Event (0x4xxx) packets from the event socket and dispatching them. */
    fun startEventLoop(scope: CoroutineScope, onEvent: (PtpEvent) -> Unit): Job {
        val job = scope.launch(Dispatchers.IO) {
            while (true) {
                val packet = try {
                    readPtpIpPacket(eventIn)
                } catch (t: Throwable) {
                    break
                }
                if (packet.type != PtpIpPacketType.EVENT) continue
                val r = PtpByteReader(packet.payload)
                val code = r.readU16()
                val transId = r.readU32()
                val params = ArrayList<Long>()
                while (r.remaining() >= 4) params.add(r.readU32())
                onEvent(PtpEvent(code, transId, params.toLongArray()))
            }
        }
        eventLoopJob = job
        return job
    }

    suspend fun openSession(sessionId: Long = 1L): OperationResult = withContext(Dispatchers.IO) {
        executeOperation(
            PtpOperationCode.OPEN_SESSION,
            DataPhase.NONE,
            listOf(sessionId),
            acceptableCodes = setOf(PtpResponseCode.OK, PtpResponseCode.SESSION_ALREADY_OPEN),
        )
    }

    suspend fun getStorageIds(): LongArray = withContext(Dispatchers.IO) {
        val result = executeOperation(PtpOperationCode.GET_STORAGE_IDS, DataPhase.DEVICE_TO_HOST, emptyList())
        PtpByteReader(result.data ?: ByteArray(0)).readU32Array()
    }

    /** Flat listing of every object handle visible on the given storage (0xFFFFFFFF = all storages). */
    suspend fun getObjectHandles(storageId: Long = 0xFFFFFFFFL): LongArray = withContext(Dispatchers.IO) {
        val result = executeOperation(
            PtpOperationCode.GET_OBJECT_HANDLES,
            DataPhase.DEVICE_TO_HOST,
            listOf(storageId, 0L, 0xFFFFFFFFL),
        )
        PtpByteReader(result.data ?: ByteArray(0)).readU32Array()
    }

    suspend fun getObjectInfo(handle: Long): PtpObjectInfo = withContext(Dispatchers.IO) {
        val result = executeOperation(PtpOperationCode.GET_OBJECT_INFO, DataPhase.DEVICE_TO_HOST, listOf(handle))
        PtpObjectInfo.parse(result.data ?: ByteArray(0))
    }

    /** Streams the full object bytes to [sink] as they arrive, without buffering the whole file in memory. */
    suspend fun getObject(handle: Long, sink: OutputStream): Long = withContext(Dispatchers.IO) {
        var total = 0L
        executeOperation(
            PtpOperationCode.GET_OBJECT,
            DataPhase.DEVICE_TO_HOST,
            listOf(handle),
            onDataChunk = { chunk ->
                sink.write(chunk)
                total += chunk.size
            },
        )
        total
    }

    fun close() {
        eventLoopJob?.cancel()
        runCatching { commandSocket.close() }
        if (::eventSocket.isInitialized) runCatching { eventSocket.close() }
    }

    private fun executeOperation(
        opcode: Int,
        dataPhase: DataPhase,
        params: List<Long>,
        onDataChunk: ((ByteArray) -> Unit)? = null,
        acceptableCodes: Set<Int> = setOf(PtpResponseCode.OK),
    ): OperationResult {
        val transId = nextTransactionId()
        val requestWriter = PtpByteWriter()
            .writeU32(dataPhase.wireValue)
            .writeU16(opcode)
            .writeU32(transId)
        for (p in params) requestWriter.writeU32(p)
        writePtpIpPacket(commandOut, PtpIpPacketType.OPERATION_REQUEST, requestWriter.toByteArray())

        val collected = if (onDataChunk == null) java.io.ByteArrayOutputStream() else null
        val chunkHandler: (ByteArray) -> Unit = onDataChunk ?: { chunk -> collected!!.write(chunk) }
        if (dataPhase == DataPhase.DEVICE_TO_HOST) {
            readDataPhase(chunkHandler)
        }

        val responsePacket = readPtpIpPacket(commandIn)
        if (responsePacket.type != PtpIpPacketType.OPERATION_RESPONSE) {
            throw PtpIpProtocolException("Expected Operation Response, got packet type ${responsePacket.type}")
        }
        val r = PtpByteReader(responsePacket.payload)
        val code = r.readU16()
        val respTransId = r.readU32()
        val respParams = ArrayList<Long>()
        while (r.remaining() >= 4) respParams.add(r.readU32())

        if (code !in acceptableCodes) {
            throw PtpIpProtocolException("Operation 0x${opcode.toString(16)} failed with response code 0x${code.toString(16)}")
        }
        return OperationResult(code, respTransId, respParams, collected?.toByteArray())
    }

    private fun readDataPhase(onChunk: (ByteArray) -> Unit) {
        while (true) {
            val packet = readPtpIpPacket(commandIn)
            when (packet.type) {
                PtpIpPacketType.START_DATA_PACKET -> {
                    // Payload: transaction id (4) + total data length (8). We don't need to
                    // pre-size a buffer since chunks are appended as they arrive.
                }
                PtpIpPacketType.DATA_PACKET -> {
                    val r = PtpByteReader(packet.payload)
                    r.readU32() // transaction id
                    onChunk(r.readRemaining())
                }
                PtpIpPacketType.END_DATA_PACKET -> {
                    val r = PtpByteReader(packet.payload)
                    r.readU32() // transaction id
                    onChunk(r.readRemaining())
                    return
                }
                else -> throw PtpIpProtocolException("Unexpected packet type ${packet.type} during data phase")
            }
        }
    }

    companion object {
        suspend fun connect(host: String, network: Network? = null): PtpIpClient = withContext(Dispatchers.IO) {
            val commandSocket = openSocket(host, network)
            PtpIpClient(commandSocket, host, network)
        }

        private fun openSocket(host: String, network: Network?): Socket {
            Log.d(TAG, "connecting TCP socket to $host:$PTP_IP_PORT (network=$network)")
            val socket = Socket()
            network?.bindSocket(socket)
            socket.connect(InetSocketAddress(host, PTP_IP_PORT), 10_000)
            socket.soTimeout = 20_000
            Log.d(TAG, "TCP connected, localPort=${socket.localPort} remote=${socket.remoteSocketAddress}")
            return socket
        }

        private fun randomGuidBytes(): ByteArray {
            val uuid = UUID.randomUUID()
            val bytes = ByteArray(16)
            val buffer = java.nio.ByteBuffer.wrap(bytes)
            buffer.putLong(uuid.mostSignificantBits)
            buffer.putLong(uuid.leastSignificantBits)
            return bytes
        }
    }
}
