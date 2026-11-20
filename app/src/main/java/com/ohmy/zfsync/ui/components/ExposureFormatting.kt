package com.ohmy.zfsync.ui.components

import com.ohmy.zfsync.core.model.ExposureInfo
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToLong

/**
 * One line like "NIKKOR Z 40mm f/2 SE • 40 mm • ƒ/2 • 1/250 s": lens name, focal length,
 * aperture, shutter speed. Missing parts are left out; null when nothing is known.
 */
fun ExposureInfo.toDisplayString(): String? {
    val parts = listOfNotNull(
        lensName,
        focalLengthMm?.let { "${it.trimmed()} mm" },
        aperture?.let { "ƒ/${it.trimmed()}" },
        exposureTimeSeconds?.let(::formatShutterSpeed),
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
}

/** "NIKON Z f • NIKKOR Z 40mm f/2 SE": whichever of camera and lens are known, or null. */
fun ExposureInfo.cameraAndLens(): String? =
    listOfNotNull(cameraName, lensName).takeIf { it.isNotEmpty() }?.joinToString(" • ")

/** "90mm", as the viewer's exposure readout shows it (no space, unlike the list's "90 mm"). */
fun formatFocalLength(millimetres: Double): String = "${millimetres.trimmed()}mm"

/** "f/3.2". */
fun formatAperture(fNumber: Double): String = "f/${fNumber.trimmed()}"

private val CaptureDateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val CaptureTimeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)

/** "Nov 19, 2026 • 6:15:45 PM", in the device's locale. */
fun formatCaptureTime(dateTime: LocalDateTime): String =
    "${dateTime.format(CaptureDateFormat)} • ${dateTime.format(CaptureTimeFormat)}"

/** Sub-second speeds as a fraction the way cameras show them (1/250 s, 1/2.5 s), longer ones in seconds. */
fun formatShutterSpeed(seconds: Double): String {
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
