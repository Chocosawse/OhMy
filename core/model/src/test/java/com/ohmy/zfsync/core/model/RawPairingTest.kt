package com.ohmy.zfsync.core.model

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RawPairingTest {

    private fun file(name: String, id: Int, savedAt: Long = 0L) =
        SyncedPhoto(filename = name, savedAtMillis = savedAt, uri = Uri.parse("content://media/$id"))

    @Test
    fun `a RAW joins its JPEG, whichever arrives first`() {
        val jpeg = file("DSC_0001.JPG", 1)
        val raw = file("DSC_0001.NEF", 2)
        val expected = listOf(jpeg.copy(rawUri = raw.uri))
        assertEquals(expected, pairRawFiles(listOf(jpeg, raw)))
        assertEquals(expected, pairRawFiles(listOf(raw, jpeg)))
    }

    @Test
    fun `a RAW without a JPEG is an item of its own`() {
        val raw = file("DSC_0002.NEF", 2)
        assertEquals(listOf(raw.copy(rawUri = raw.uri)), pairRawFiles(listOf(raw)))
    }

    @Test
    fun `different shots stay apart`() {
        val jpeg = file("DSC_0001.JPG", 1)
        val raw = file("DSC_0002.NEF", 2)
        assertEquals(listOf(jpeg, raw.copy(rawUri = raw.uri)), pairRawFiles(listOf(jpeg, raw)))
    }

    @Test
    fun `the same name long apart is a different shot (file numbers wrap around)`() {
        val jpeg = file("DSC_0001.JPG", 1, savedAt = 0L)
        val raw = file("DSC_0001.NEF", 2, savedAt = 400L * 24 * 60 * 60 * 1000)
        assertEquals(2, pairRawFiles(listOf(jpeg, raw)).size)
    }

    @Test
    fun `adding a file already in the album changes nothing`() {
        val album = pairRawFiles(listOf(file("DSC_0001.JPG", 1), file("DSC_0001.NEF", 2)))
        assertEquals(album, album.withFile(file("DSC_0001.NEF", 2)))
    }
}
