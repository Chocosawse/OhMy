package com.ohmy.zfsync.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class ExifReadingTest {

    @Test
    fun `a model that already names the brand is used as is`() {
        assertEquals("NIKON Z f", cameraName(make = "NIKON CORPORATION", model = "NIKON Z f"))
        assertEquals("Canon EOS R6", cameraName(make = "Canon", model = "Canon EOS R6"))
    }

    @Test
    fun `a model without the brand gets the make's first word in front`() {
        assertEquals("FUJIFILM X-T5", cameraName(make = "FUJIFILM", model = "X-T5"))
        assertEquals("SONY ILCE-7M4", cameraName(make = "SONY", model = "ILCE-7M4"))
    }

    @Test
    fun `missing parts fall back to what there is`() {
        assertEquals("NIKON", cameraName(make = "NIKON CORPORATION", model = null))
        assertEquals("Z f", cameraName(make = null, model = " Z f "))
        assertNull(cameraName(make = " ", model = ""))
    }

    @Test
    fun `parses EXIF's date format`() {
        assertEquals(LocalDateTime.of(2026, 11, 19, 18, 15, 45), parseExifDateTime("2026:11:19 18:15:45"))
    }

    @Test
    fun `a blank or malformed date is null`() {
        assertNull(parseExifDateTime("    :  :     :  :  "))
        assertNull(parseExifDateTime("not a date"))
    }
}
