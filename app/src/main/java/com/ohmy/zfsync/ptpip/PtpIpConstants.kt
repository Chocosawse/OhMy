package com.ohmy.zfsync.ptpip

/** PTP/IP packet types, transported as a 4-byte little-endian length + 4-byte type + payload. */
object PtpIpPacketType {
    const val INIT_COMMAND_REQUEST = 1
    const val INIT_COMMAND_ACK = 2
    const val INIT_EVENT_REQUEST = 3
    const val INIT_EVENT_ACK = 4
    const val INIT_FAIL = 5
    const val OPERATION_REQUEST = 6
    const val OPERATION_RESPONSE = 7
    const val EVENT = 8
    const val START_DATA_PACKET = 9
    const val DATA_PACKET = 10
    const val CANCEL_TRANSACTION = 11
    const val END_DATA_PACKET = 12
    const val PROBE_REQUEST = 13
    const val PROBE_RESPONSE = 14
}

/** Standard PTP (ISO 15740) operation codes used by this client. */
object PtpOperationCode {
    const val GET_DEVICE_INFO = 0x1001
    const val OPEN_SESSION = 0x1002
    const val CLOSE_SESSION = 0x1003
    const val GET_STORAGE_IDS = 0x1004
    const val GET_STORAGE_INFO = 0x1005
    const val GET_OBJECT_HANDLES = 0x1007
    const val GET_OBJECT_INFO = 0x1008
    const val GET_OBJECT = 0x1009
}

/** Standard PTP response codes relevant to this client. */
object PtpResponseCode {
    const val OK = 0x2001
    const val SESSION_ALREADY_OPEN = 0x201E
}

/** Standard PTP event codes relevant to this client. */
object PtpEventCode {
    const val OBJECT_ADDED = 0x4002
    const val STORE_FULL = 0x400A
    const val DEVICE_PROP_CHANGED = 0x4006
    const val CAPTURE_COMPLETE = 0x400D
}

/** PTP object format codes used to distinguish JPEG stills from everything else (RAW, directories, ...). */
object PtpObjectFormatCode {
    const val ASSOCIATION = 0x3001
    const val EXIF_JPEG = 0x3801
}

const val PTP_IP_PORT = 15740
