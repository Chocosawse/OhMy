package com.ohmy.zfsync.ui.components

import com.ohmy.zfsync.core.model.ExposureInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExposureFormattingTest {

    @Test
    fun `formats all four parts in order`() {
        assertEquals(
            "NIKKOR Z 40mm f/2 SE • 40 mm • ƒ/2 • 1/250 s",
            ExposureInfo(40.0, 2.0, 1.0 / 250, lensName = "NIKKOR Z 40mm f/2 SE").toDisplayString(),
        )
    }

    @Test
    fun `leaves out a missing lens name`() {
        assertEquals("40 mm • ƒ/2 • 1/250 s", ExposureInfo(40.0, 2.0, 1.0 / 250).toDisplayString())
    }

    @Test
    fun `keeps one decimal where it matters and drops a trailing zero`() {
        assertEquals("18.5 mm • ƒ/5.6 • 1/60 s", ExposureInfo(18.5, 5.6, 1.0 / 60).toDisplayString())
        assertEquals("50 mm • ƒ/1.8", ExposureInfo(50.0, 1.799).toDisplayString())
    }

    @Test
    fun `short sub-second speeds keep a decimal in the fraction`() {
        assertEquals("1/2.5 s", ExposureInfo(exposureTimeSeconds = 0.4).toDisplayString())
        assertEquals("1/3 s", ExposureInfo(exposureTimeSeconds = 1.0 / 3).toDisplayString())
    }

    @Test
    fun `speeds of a second or more are shown in seconds`() {
        assertEquals("1 s", ExposureInfo(exposureTimeSeconds = 1.0).toDisplayString())
        assertEquals("2.5 s", ExposureInfo(exposureTimeSeconds = 2.5).toDisplayString())
    }

    @Test
    fun `manual lens shows its name and whatever else was recorded`() {
        assertEquals(
            "Voigtländer Nokton 40mm F1.2 • 1/500 s",
            ExposureInfo(exposureTimeSeconds = 1.0 / 500, lensName = "Voigtländer Nokton 40mm F1.2").toDisplayString(),
        )
    }

    @Test
    fun `is null when nothing is known`() {
        assertNull(ExposureInfo().toDisplayString())
    }
}
