package com.ohmy.zfsync.core.model

import java.time.LocalDateTime

/**
 * How a photo was taken, from its EXIF metadata. Any part can be missing: manual (non-CPU) lenses
 * report no focal length or aperture, often only a lens name set in-camera, and some files carry
 * no EXIF at all.
 */
data class ExposureInfo(
    val focalLengthMm: Double? = null,
    val aperture: Double? = null,
    val exposureTimeSeconds: Double? = null,
    val lensName: String? = null,
    val iso: Int? = null,
    /** The camera body, e.g. "NIKON Z f". */
    val cameraName: String? = null,
    /** When the shutter fired, by the camera's own clock: EXIF records no time zone. */
    val capturedAt: LocalDateTime? = null,
)
