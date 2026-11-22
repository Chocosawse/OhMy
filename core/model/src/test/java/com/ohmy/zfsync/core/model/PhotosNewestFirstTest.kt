package com.ohmy.zfsync.core.model

import com.ohmy.zfsync.core.testing.FakeData
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

// Robolectric because SyncedPhoto holds an android.net.Uri. SDK 34 keeps it runnable on JDK 17;
// without a pin Robolectric would try compileSdk 37, which it doesn't support.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotosNewestFirstTest {

    /** FakeData's save times line up with its camera clock in this zone. */
    private val zone = ZoneId.of("America/Los_Angeles")

    @Test
    fun `photos are newest first by when they were taken, this session's or not`() {
        assertEquals(FakeData.photos.reversed(), FakeData.States.connected.photosNewestFirst(zone))
        assertEquals(FakeData.photos.reversed(), FakeData.States.disconnected.photosNewestFirst(zone))
    }

    @Test
    fun `capture time wins over save order`() {
        val (older, newer) = FakeData.photos.take(2)
        // Saved in the opposite order to how they were taken (e.g. a RAW-heavy shot downloading late).
        val state = SyncUiState(photos = listOf(newer.copy(savedAtMillis = 1), older.copy(savedAtMillis = 2)))
        assertEquals(listOf(newer.filename, older.filename), state.photosNewestFirst(zone).map { it.filename })
    }

    @Test
    fun `a photo without an EXIF time sorts by when it was saved`() {
        // FakeData's eighth photo has no EXIF; its save time puts it between its neighbours.
        val ordered = FakeData.States.disconnected.photosNewestFirst(zone).map { it.filename }
        val index = ordered.indexOf(FakeData.photos[7].filename)
        assertEquals(FakeData.photos[8].filename, ordered[index - 1])
        assertEquals(FakeData.photos[6].filename, ordered[index + 1])
    }
}
