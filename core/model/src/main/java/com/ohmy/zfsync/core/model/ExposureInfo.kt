package com.ohmy.zfsync.core.model

/**
 * Exposure settings from a photo's EXIF metadata. Any part can be missing: manual (non-CPU)
 * lenses report no focal length or aperture, often only a lens name set in-camera, and some files
 * carry no EXIF at all.
 */
data class ExposureInfo(
    val focalLengthMm: Double? = null,
    val aperture: Double? = null,
    val exposureTimeSeconds: Double? = null,
    val lensName: String? = null,
)
