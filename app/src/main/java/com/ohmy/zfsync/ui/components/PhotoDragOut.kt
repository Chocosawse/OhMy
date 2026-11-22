package com.ohmy.zfsync.ui.components

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.util.Size
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.ohmy.zfsync.core.model.SyncedPhoto
import com.ohmy.zfsync.ui.theme.LocalHomeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The drag shadow's photo: a rounded square this big, under the finger. */
private val ShadowSize = 96.dp
private val ShadowCorner = 12.dp

/** The count badge on the shadow, when more than one photo is on the move. */
private val BadgeSize = 28.dp

/**
 * Dragging the selected photos out to another app, for when this one shares the screen with it
 * (split screen, a freeform window). Returns what dragToSelect's onDragOut wants: given the photo
 * a long-press landed on, starts a system drag of the whole selection and says so, or returns
 * false to leave the long-press to selecting (full screen, or the photo isn't selected).
 *
 * The other app gets the photos as content URIs it may read (the grant lasts as long as it holds
 * the drop), so it can copy or attach them like a drop from any gallery.
 */
@Composable
internal fun rememberPhotoDragOut(photos: List<SyncedPhoto>, selection: HomeSelection): (Uri) -> Boolean {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val colors = LocalHomeColors.current
    val latestPhotos by rememberUpdatedState(photos)
    val latestColors by rememberUpdatedState(colors)
    return remember(view, context, density) {
        drag@{ pressed: Uri ->
            if (context.findActivity()?.isInMultiWindowMode != true) return@drag false
            val selected = selection.selected
            if (pressed !in selected) return@drag false
            // In the order they're shown, the pressed one first (its thumbnail is the shadow).
            val uris = listOf(pressed) + latestPhotos.map { it.uri }.filter { it in selected && it != pressed }
            val sizePx = with(density) { ShadowSize.roundToPx() }
            val cornerPx = with(density) { ShadowCorner.toPx() }
            val badgePx = with(density) { BadgeSize.toPx() }
            val shadowColors = latestColors
            scope.launch {
                // MediaStore's own thumbnail, small and usually cached, read off the main thread.
                val thumbnail = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.loadThumbnail(pressed, Size(sizePx, sizePx), null) }.getOrNull()
                }
                val clip = ClipData(
                    ClipDescription("Photos", arrayOf("image/*")),
                    ClipData.Item(uris.first()),
                ).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
                val shadow = PhotoDragShadow(
                    view = view,
                    thumbnail = thumbnail,
                    count = uris.size,
                    sizePx = sizePx,
                    cornerPx = cornerPx,
                    badgePx = badgePx,
                    tile = shadowColors.tile,
                    badge = shadowColors.accentFill,
                    onBadge = shadowColors.onAccent,
                )
                view.startDragAndDrop(clip, shadow, null, View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ)
            }
            true
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** What follows the finger: the pressed photo as a rounded square, with how many are coming in a badge. */
private class PhotoDragShadow(
    view: View,
    private val thumbnail: Bitmap?,
    private val count: Int,
    private val sizePx: Int,
    private val cornerPx: Float,
    private val badgePx: Float,
    tile: Color,
    badge: Color,
    onBadge: Color,
) : View.DragShadowBuilder(view) {
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tile.toArgb() }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = badge.toArgb() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = onBadge.toArgb()
        textAlign = Paint.Align.CENTER
        textSize = badgePx * 0.5f
        typeface = Typeface.DEFAULT_BOLD
    }

    /** Room around the photo for the badge, which sits over its top right corner. */
    private val margin = badgePx / 2f
    private val total = (sizePx + margin * 2).toInt()

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(total, total)
        outShadowTouchPoint.set(total / 2, total / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        val photo = RectF(margin, margin, margin + sizePx, margin + sizePx)
        canvas.drawRoundRect(photo, cornerPx, cornerPx, tilePaint)
        if (thumbnail != null) {
            // Center-cropped into the square, like the grid's cells.
            val scale = maxOf(sizePx / thumbnail.width.toFloat(), sizePx / thumbnail.height.toFloat())
            val shader = BitmapShader(thumbnail, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(
                    Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(
                            margin + (sizePx - thumbnail.width * scale) / 2f,
                            margin + (sizePx - thumbnail.height * scale) / 2f,
                        )
                    },
                )
            }
            canvas.drawRoundRect(photo, cornerPx, cornerPx, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
        }
        if (count > 1) {
            val cx = photo.right
            val cy = photo.top
            canvas.drawCircle(cx, cy, badgePx / 2f, badgePaint)
            val baseline = cy - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(if (count > 99) "99+" else count.toString(), cx, baseline, textPaint)
        }
    }
}
