package com.ohmy.zfsync.core.model

import com.ohmy.zfsync.core.testing.FakeData
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric because SyncedPhoto holds an android.net.Uri. SDK 34 keeps it runnable on JDK 17;
// without a pin Robolectric would try compileSdk 37, which it doesn't support.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotosNewestFirstTest {

    @Test
    fun `this session's photos come first, then earlier ones, each newest first`() {
        val ordered = FakeData.States.connected.photosNewestFirst()

        assertEquals(FakeData.sessionPhotos.reversed() + FakeData.earlierPhotos.reversed(), ordered)
    }

    @Test
    fun `without a session the whole album is newest first`() {
        assertEquals(FakeData.photos.reversed(), FakeData.States.disconnected.photosNewestFirst())
    }
}
