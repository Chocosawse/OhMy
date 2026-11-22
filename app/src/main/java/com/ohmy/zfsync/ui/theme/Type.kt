package com.ohmy.zfsync.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ohmy.zfsync.R

/*
 * Editorial, zine-like type: a high-contrast serif (Newsreader) for headings, section headers and
 * photo names, over a neutral grotesque (Inter) for reading text, labels and controls, and a mono
 * (JetBrains Mono, [MonoFontFamily]) for data readouts.
 *
 * Both are variable fonts, subset to Latin (licenses in licenses/fonts). Newsreader also has an
 * optical-size axis, which Android doesn't set from the text size, so each serif size range gets
 * its own family pinned to a matching optical size: finer contrast and tighter spacing for big
 * headings, sturdier shapes for smaller titles.
 */

@OptIn(ExperimentalTextApi::class)
private fun newsreader(opticalSize: Float) = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium).map { weight ->
        Font(
            resId = R.font.newsreader,
            weight = weight,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight.weight),
                FontVariation.Setting("opsz", opticalSize),
            ),
        )
    },
)

internal val NewsreaderDisplay = newsreader(opticalSize = 72f)
internal val NewsreaderHeadline = newsreader(opticalSize = 36f)
internal val NewsreaderTitle = newsreader(opticalSize = 20f)

@OptIn(ExperimentalTextApi::class)
internal val Inter = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold).flatMap { weight ->
        listOf(
            Font(
                resId = R.font.inter,
                weight = weight,
                style = FontStyle.Normal,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            ),
            Font(
                resId = R.font.inter_italic,
                weight = weight,
                style = FontStyle.Italic,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            ),
        )
    },
)

/**
 * JetBrains Mono, for data readouts (file names, exposure, timestamps), where fixed-width digits
 * keep values from jittering as they change. Variable, so real bold rather than a synthetic one.
 */
@OptIn(ExperimentalTextApi::class)
val MonoFontFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.Bold).map { weight ->
        Font(
            resId = R.font.jetbrains_mono,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

/**
 * DSEG7 Classic (OFL, shipped unmodified: its license reserves the name for the original), a
 * seven-segment face for the photo's date imprint, like a film camera's LED date back. Digits,
 * spaces and the colon only; no apostrophe.
 */
val DateImprintFontFamily = FontFamily(Font(R.font.dseg7_classic_bold, FontWeight.Bold))

private fun serif(family: FontFamily, size: Int, lineHeight: Int, letterSpacing: Float) = TextStyle(
    fontFamily = family,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

private fun sans(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal, letterSpacing: Float = 0f) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

/**
 * Material's type scale, re-set. Sizes keep Material's roles: display/headline/titleLarge/
 * titleMedium are serif (titleMedium is the section header size), the rest Inter. Inter is set
 * without Material's added tracking, which is tuned for Roboto.
 */
val AppTypography = Typography(
    displayLarge = serif(NewsreaderDisplay, size = 57, lineHeight = 64, letterSpacing = -1f),
    displayMedium = serif(NewsreaderDisplay, size = 45, lineHeight = 52, letterSpacing = -0.75f),
    displaySmall = serif(NewsreaderDisplay, size = 38, lineHeight = 44, letterSpacing = -0.5f),
    headlineLarge = serif(NewsreaderHeadline, size = 34, lineHeight = 40, letterSpacing = -0.5f),
    headlineMedium = serif(NewsreaderHeadline, size = 28, lineHeight = 34, letterSpacing = -0.25f),
    headlineSmall = serif(NewsreaderHeadline, size = 24, lineHeight = 30, letterSpacing = -0.25f),
    titleLarge = serif(NewsreaderTitle, size = 22, lineHeight = 28, letterSpacing = 0f),
    titleMedium = serif(NewsreaderTitle, size = 18, lineHeight = 24, letterSpacing = 0f),
    titleSmall = sans(size = 14, lineHeight = 20, weight = FontWeight.Medium),
    bodyLarge = sans(size = 16, lineHeight = 24),
    bodyMedium = sans(size = 14, lineHeight = 20),
    bodySmall = sans(size = 12, lineHeight = 16),
    labelLarge = sans(size = 14, lineHeight = 20, weight = FontWeight.Medium),
    labelMedium = sans(size = 12, lineHeight = 16, weight = FontWeight.Medium, letterSpacing = 0.2f),
    labelSmall = sans(size = 11, lineHeight = 16, weight = FontWeight.Medium, letterSpacing = 0.3f),
)
