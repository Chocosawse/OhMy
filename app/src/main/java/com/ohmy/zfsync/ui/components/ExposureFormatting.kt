package com.ohmy.zfsync.ui.components

import com.ohmy.zfsync.core.model.ExposureInfo
import kotlin.math.roundToLong

/**
 * One line like "40 mm • ƒ/2 • 1/250 s". Without a focal length (e.g. a manual lens) it leads
 * with the lens name instead. Missing parts are left out; null when nothing is known.
 */
fun ExposureInfo.toDisplayString(): String? {
    val parts = listOfNotNull(
        focalLengthMm?.let { "${it.trimmed()} mm" } ?: lensName,
        aperture?.let { "ƒ/${it.trimmed()}" },
        exposureTimeSeconds?.let(::formatShutterSpeed),
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
}

/** Sub-second speeds as a fraction the way cameras show them (1/250 s, 1/2.5 s), longer ones in seconds. */
private fun formatShutterSpeed(seconds: Double): String {
    if (seconds >= 1.0) return "${seconds.trimmed()} s"
    val denominator = 1.0 / seconds
    val shown = if (denominator >= 10) denominator.roundToLong().toString() else denominator.trimmed()
    return "1/$shown s"
}

/** At most one decimal, without a trailing ".0": 2.0 -> "2", 5.6 -> "5.6", 1.799 -> "1.8". */
private fun Double.trimmed(): String {
    val tenths = (this * 10).roundToLong()
    return if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
}
