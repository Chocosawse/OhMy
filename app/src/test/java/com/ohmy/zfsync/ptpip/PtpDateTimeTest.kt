package com.ohmy.zfsync.ptpip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class PtpDateTimeTest {

    @Test
    fun `reads the camera's capture time`() {
        assertEquals(LocalDateTime.of(2026, 11, 21, 20, 20, 5), parsePtpDateTime("20261121T202005"))
    }

    @Test
    fun `ignores tenths and a time zone after it`() {
        assertEquals(LocalDateTime.of(2026, 11, 21, 20, 20, 5), parsePtpDateTime("20261121T202005.3+0100"))
    }

    @Test
    fun `empty or malformed is no time`() {
        assertNull(parsePtpDateTime(""))
        assertNull(parsePtpDateTime("2026-11-21 20:20"))
        assertNull(parsePtpDateTime("20261321T202005"))
    }
}
