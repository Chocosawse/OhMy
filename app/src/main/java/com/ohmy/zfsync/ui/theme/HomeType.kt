package com.ohmy.zfsync.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The home screen's type styles, in the app's three faces. Where one of [AppTypography]'s styles
 * is within 2sp of the size wanted it's used (with only the weight or leading changed), so the
 * home screen stays on the app's type scale. Colors are left to the caller (see HomeColors).
 */
@Immutable
data class HomeType(
    /** Today's date over the title: mono, uppercase (by the caller), widely tracked. */
    val kicker: TextStyle,
    /** The screen's title, "Nikon Zf". */
    val title: TextStyle,
    /** The status row's text; its label is [status] at medium weight. */
    val status: TextStyle,
    /** The Photos and Albums tabs. */
    val tab: TextStyle,
    /** A section of photos' heading ("Morning", "Friday, November 20"). */
    val section: TextStyle,
    /** The connection card's heading. */
    val cardTitle: TextStyle,
    val body: TextStyle,
    val fieldLabel: TextStyle,
    val fieldValue: TextStyle,
    val button: TextStyle,
    /** A pending tile's file number. */
    val tileNumber: TextStyle,
    /** A pending tile's "QUEUED" or percentage chip. */
    val tileChip: TextStyle,
)

private fun mono(size: Int, letterSpacing: Float = 0f, lineHeight: Int = size + 4) = TextStyle(
    fontFamily = MonoFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.em,
)

private fun homeType(typography: Typography) = HomeType(
    kicker = mono(size = 11, letterSpacing = 0.14f),
    title = TextStyle(
        fontFamily = NewsreaderDisplay,
        fontWeight = FontWeight.Medium,
        fontSize = 48.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.01).em,
    ),
    status = mono(size = 12, letterSpacing = 0.04f),
    // 22sp: titleLarge exactly.
    tab = typography.titleLarge,
    // 30sp wanted: headlineMedium's 28sp.
    section = typography.headlineMedium.copy(fontWeight = FontWeight.Medium, lineHeight = 1.05.em),
    // 26sp wanted: headlineSmall's 24sp, a step under the section headings as in the mock.
    cardTitle = typography.headlineSmall.copy(fontWeight = FontWeight.Medium, lineHeight = 1.1.em),
    // 14sp at 1.45: bodyMedium's 14sp at 20sp (1.43).
    body = typography.bodyMedium,
    // 12sp: bodySmall exactly.
    fieldLabel = typography.bodySmall,
    fieldValue = mono(size = 15, lineHeight = 20),
    // 15sp medium wanted: labelLarge's 14sp medium.
    button = typography.labelLarge,
    tileNumber = mono(size = 11),
    tileChip = mono(size = 10, letterSpacing = 0.06f, lineHeight = 12),
)

/** The home screen's styles, on the app's type scale. */
val HomeTypography: HomeType = homeType(AppTypography)
