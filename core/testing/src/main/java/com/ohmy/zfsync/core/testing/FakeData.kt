package com.ohmy.zfsync.core.testing

import android.net.Uri
import com.ohmy.zfsync.core.model.ConnectionState
import com.ohmy.zfsync.core.model.DownloadStatus
import com.ohmy.zfsync.core.model.ExposureInfo
import com.ohmy.zfsync.core.model.SavedCameraCredentials
import com.ohmy.zfsync.core.model.SyncUiState
import com.ohmy.zfsync.core.model.SyncedPhoto

/**
 * Canned app data shared by Compose previews, unit tests and snapshot tests, and later by a fake
 * build flavor. Everything is fixed (no clock, random or device reads) so snapshots stay stable.
 * Timestamps are absolute instants, so rendered dates still follow the test's time zone: pin it
 * in snapshot tests.
 *
 * Photo URIs are MediaStore-shaped but point at nothing, so image loaders show their
 * placeholder/empty state. Uri needs an Android runtime: Robolectric in unit tests.
 */
object FakeData {

    const val CAMERA_IP = "192.168.1.1"

    val credentials = SavedCameraCredentials(ssid = "Zf_4502181", password = "ohmy1234")

    /** A fixed instant: 2026-11-16 21:30 UTC. */
    private const val BASE_TIME_MILLIS = 1_794_864_600_000L

    private const val LENS_40 = "NIKKOR Z 40mm f/2 SE"

    /**
     * One per photo in [photos]. Covers full EXIF, a lens that recorded no name, a manual (non-CPU)
     * lens with only its in-camera name, and no EXIF at all.
     */
    private val exposures: List<ExposureInfo?> = listOf(
        ExposureInfo(focalLengthMm = 40.0, aperture = 2.0, exposureTimeSeconds = 1.0 / 250, lensName = LENS_40),
        ExposureInfo(focalLengthMm = 40.0, aperture = 2.8, exposureTimeSeconds = 1.0 / 125, lensName = LENS_40),
        // No lens name recorded.
        ExposureInfo(focalLengthMm = 40.0, aperture = 4.0, exposureTimeSeconds = 1.0 / 60),
        // Manual (non-CPU) lens: no focal length or aperture recorded, just the name set in-camera.
        ExposureInfo(exposureTimeSeconds = 1.0 / 500, lensName = "Voigtländer Nokton 40mm F1.2"),
        ExposureInfo(focalLengthMm = 24.0, aperture = 5.6, exposureTimeSeconds = 1.0 / 1000, lensName = "NIKKOR Z 24-70mm f/4 S"),
        ExposureInfo(focalLengthMm = 85.0, aperture = 1.8, exposureTimeSeconds = 1.0 / 2000, lensName = "NIKKOR Z 85mm f/1.8 S"),
        ExposureInfo(focalLengthMm = 50.0, aperture = 8.0, exposureTimeSeconds = 0.5, lensName = "NIKKOR Z 50mm f/1.8 S"),
        null,
        ExposureInfo(focalLengthMm = 40.0, aperture = 2.0, exposureTimeSeconds = 2.0, lensName = LENS_40),
        ExposureInfo(focalLengthMm = 105.0, aperture = 2.8, exposureTimeSeconds = 1.0 / 320, lensName = "NIKKOR Z MC 105mm f/2.8 VR S"),
        ExposureInfo(focalLengthMm = 40.0, aperture = 2.8, exposureTimeSeconds = 1.0 / 250, lensName = LENS_40),
        ExposureInfo(focalLengthMm = 40.0, aperture = 2.0, exposureTimeSeconds = 1.0 / 8, lensName = LENS_40),
    )

    /** The whole album, oldest first (the order [SyncUiState.photos] uses). */
    val photos: List<SyncedPhoto> = exposures.mapIndexed { i, exposure ->
        val number = 3730 + i
        SyncedPhoto(
            filename = "DSC_$number.JPG",
            // Roughly a shot every 1.5 minutes, as in a real outing.
            savedAtMillis = BASE_TIME_MILLIS + i * 90_000L,
            uri = Uri.parse("content://media/external/images/media/${10_000 + number}"),
            exposure = exposure,
        )
    }

    /** The newest three: what this session downloaded in the states that have a session. */
    val sessionPhotos: List<SyncedPhoto> = photos.takeLast(3)

    /** Everything from before this session. */
    val earlierPhotos: List<SyncedPhoto> = photos.dropLast(3)

    val downloadingTwo: DownloadStatus = DownloadStatus.Downloading(count = 2)

    /** Representative [SyncUiState]s, one per thing a screen has to handle. */
    object States {
        /** Disconnected, with an album from earlier sessions: the home screen. */
        val disconnected = SyncUiState(photos = photos)

        /** Fresh install: disconnected, nothing synced yet. */
        val firstLaunch = SyncUiState()

        val connecting = SyncUiState(connectionState = ConnectionState.Connecting, photos = photos)

        val connectionError = SyncUiState(
            connectionState = ConnectionState.Error("Failed while joining Wi-Fi: Could not join Wi-Fi network \"Zf_4502181\""),
            photos = photos,
        )

        /** Connected, three new shots this session on top of the earlier album. */
        val connected = SyncUiState(
            connectionState = ConnectionState.Connected(CAMERA_IP),
            photos = photos,
            sessionPhotoUris = sessionPhotos.mapTo(HashSet()) { it.uri },
        )

        /** Connected, nothing new yet this session. */
        val connectedNoNewShots = SyncUiState(connectionState = ConnectionState.Connected(CAMERA_IP), photos = photos)

        /** Connected and running a manual sync, with a download error to show. */
        val connectedSyncingWithError = connected.copy(
            isSyncing = true,
            lastError = "Failed to download 3742: Read timed out",
        )

        /** Connected, auto-sync off, empty album. */
        val connectedEmptyAutoSyncOff = SyncUiState(
            connectionState = ConnectionState.Connected(CAMERA_IP),
            autoSyncEnabled = false,
        )
    }
}
