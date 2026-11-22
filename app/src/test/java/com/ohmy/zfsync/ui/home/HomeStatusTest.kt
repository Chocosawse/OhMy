package com.ohmy.zfsync.ui.home

import com.ohmy.zfsync.core.model.ConnectionState
import com.ohmy.zfsync.core.model.PendingPhoto
import com.ohmy.zfsync.core.model.SyncUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class HomeStatusTest {

    private val connected = SyncUiState(connectionState = ConnectionState.Connected("192.168.1.1"), cameraSsid = "Zf_1")
    private val queued = PendingPhoto(handle = 1, filename = "DSC_0001.JPG", capturedAt = null, totalBytes = 100)
    private val halfway = queued.copy(handle = 2, filename = "DSC_0002.JPG", receivedBytes = 64)

    @Test
    fun `connected is syncing while shots are pending or being looked up`() {
        assertEquals(HomeStatus.Connected("Zf_1", syncing = false, left = 0), homeStatus(connected, hasSavedCamera = true))
        assertEquals(
            HomeStatus.Connected("Zf_1", syncing = true, left = 2),
            homeStatus(connected.copy(pending = listOf(queued, halfway)), hasSavedCamera = true),
        )
        assertEquals(HomeStatus.Connected("Zf_1", syncing = true, left = 0), homeStatus(connected.copy(lookingUp = 1), hasSavedCamera = true))
    }

    @Test
    fun `not connected is not set up only without a saved camera`() {
        assertEquals(HomeStatus.NotSetUp, homeStatus(SyncUiState(), hasSavedCamera = false))
        assertEquals(HomeStatus.CameraNotFound, homeStatus(SyncUiState(), hasSavedCamera = true))
        assertEquals(HomeStatus.CameraNotFound, homeStatus(SyncUiState(connectionState = ConnectionState.Error("x")), hasSavedCamera = false))
        assertEquals(
            HomeStatus.Connecting("Zf_1"),
            homeStatus(SyncUiState(connectionState = ConnectionState.Connecting, cameraSsid = "Zf_1"), hasSavedCamera = false),
        )
    }

    @Test
    fun `progress is the batch's finished share, full when idle or disconnected, unknown while looking up`() {
        assertEquals(1f, syncProgress(SyncUiState()))
        assertEquals(1f, syncProgress(connected))
        assertNull(syncProgress(connected.copy(lookingUp = 3)))
        assertEquals(0.5f, syncProgress(connected.copy(pending = listOf(queued, halfway), finishedInBatch = 2))!!, 0.0001f)
        assertEquals(0f, syncProgress(connected.copy(pending = listOf(queued)))!!, 0.0001f)
    }

    @Test
    fun `a pending shot's percentage`() {
        assertEquals(0, queued.percent)
        assertEquals(64, halfway.percent)
        assertEquals(false, queued.inProgress)
        assertEquals(true, halfway.inProgress)
        assertEquals(0, queued.copy(totalBytes = 0).percent)
    }

    @Test
    fun `last sync is a time today and a date before`() {
        val today = LocalDate.of(2026, 11, 21)
        val evening = today.atTime(20, 20).toInstant(ZoneOffset.UTC).toEpochMilli()
        val yesterday = today.minusDays(1).atTime(20, 20).toInstant(ZoneOffset.UTC).toEpochMilli()

        // Newer JDKs put a narrow no-break space before AM/PM.
        assertEquals("8:20 PM", lastSyncText(evening, today, ZoneOffset.UTC, Locale.US).replace(' ', ' '))
        assertEquals("NOV 20", lastSyncText(yesterday, today, ZoneOffset.UTC, Locale.US))
    }

    @Test
    fun `file numbers and titles`() {
        assertEquals("3961", fileNumber("DSC_3961.JPG"))
        assertEquals("IMG", fileNumber("IMG.JPG"))
        assertEquals("DSC_3961", fileTitle("DSC_3961.JPG"))
    }
}
