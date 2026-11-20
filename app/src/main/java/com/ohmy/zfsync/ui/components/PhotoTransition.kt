package com.ohmy.zfsync.ui.components

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/*
 * The photo's move between its row in the photo list and the full-screen viewer. Hand-rolled
 * rather than Compose's shared elements, which need both screens inside one AnimatedContent: that
 * disposes the list while the viewer is open, losing its scroll position, so there'd be no row to
 * return to. Here the list stays composed under the viewer, and this state links the two: rows
 * report where their thumbnails are, and the viewer flies a copy of the photo between that and
 * its own layout.
 */

/** Where a photo is drawn: the whole image's rect, and the rect it's seen through (a crop when smaller). */
@Immutable
data class PhotoGeometry(val image: Rect, val clip: Rect) {
    fun lerp(to: PhotoGeometry, fraction: Float) =
        PhotoGeometry(lerp(image, to.image, fraction), lerp(clip, to.clip, fraction))
}

/** A photo of [aspectRatio] (width / height) center-cropped into [square], as the list's thumbnails are. */
fun croppedGeometry(square: Rect, aspectRatio: Float): PhotoGeometry {
    val image = if (aspectRatio >= square.width / square.height) {
        Size(square.height * aspectRatio, square.height)
    } else {
        Size(square.width, square.width / aspectRatio)
    }
    return PhotoGeometry(Rect(square.center - Offset(image.width / 2f, image.height / 2f), image), square)
}

/**
 * A photo of [aspectRatio] laid out as the viewer does: fitted and centred on the [screen], then
 * zoomed by [scale] about the screen's centre and moved by [offset]. Seen through the screen.
 */
fun viewerGeometry(screen: Size, aspectRatio: Float, scale: Float = 1f, offset: Offset = Offset.Zero): PhotoGeometry {
    val fitted = if (aspectRatio >= screen.width / screen.height) {
        Size(screen.width, screen.width / aspectRatio)
    } else {
        Size(screen.height * aspectRatio, screen.height)
    }
    val size = Size(fitted.width * scale, fitted.height * scale)
    val center = Offset(screen.width / 2f, screen.height / 2f) + offset
    val image = Rect(center - Offset(size.width / 2f, size.height / 2f), size)
    return PhotoGeometry(image, image.intersect(Rect(Offset.Zero, screen)))
}

/** How long a closing viewer waits for the list to bring the photo's row into view before going without it. */
private const val REVEAL_TIMEOUT_MILLIS = 300L

@Stable
class PhotoTransitionState(initiallyOpen: Boolean) {
    /** 0 = the photo sits in its list row, 1 = it fills the viewer. Animated both ways. */
    val progress = Animatable(if (initiallyOpen) 1f else 0f)

    /**
     * The viewer is on its way out: from the moment it's asked to close (before it even starts
     * shrinking, while the list scrolls the row into view) until it's gone. The screen under the
     * viewer is only hidden while the viewer is fully open and not closing.
     */
    var closing by mutableStateOf(false)

    /** The photo on the move. Its list row hides its own thumbnail (the flying copy stands in) and expands and fades with [progress]. */
    var activeUri by mutableStateOf<Uri?>(null)

    // Each on-screen thumbnail's layout coordinates: handed over once when it's placed (the same
    // object for the node's life), and only turned into a position when a transition starts, so
    // scrolling the list costs nothing here. A plain map: nothing observes it.
    private val thumbnails = HashMap<Uri, LayoutCoordinates>()

    fun onThumbnailPlaced(uri: Uri, coordinates: LayoutCoordinates) {
        thumbnails[uri] = coordinates
    }

    fun onThumbnailDisposed(uri: Uri, coordinates: LayoutCoordinates?) {
        if (coordinates == null || thumbnails[uri] === coordinates) thumbnails.remove(uri)
    }

    /**
     * Where [uri]'s thumbnail is on screen, if its row is laid out. Unclipped (unlike boundsInRoot),
     * so a row partly scrolled out still gives the thumbnail's full square.
     */
    fun thumbnailBounds(uri: Uri): Rect? =
        thumbnails[uri]?.takeIf { it.isAttached }?.let { Rect(it.positionInRoot(), it.size.toSize()) }

    /** A photo the list should scroll into view (if it isn't), so the viewer can close onto its row. */
    var revealRequest by mutableStateOf<Uri?>(null)
        private set
    private var revealed by mutableStateOf<Uri?>(null)

    /** Asks the list to bring [uri]'s row into view, and waits until it has, or a short while if it can't. */
    suspend fun reveal(uri: Uri) {
        revealed = null
        revealRequest = uri
        withTimeoutOrNull(REVEAL_TIMEOUT_MILLIS) { snapshotFlow { revealed }.first { it == uri } }
        // Cleared, so asking for the same photo again next time is still a change the list sees.
        revealRequest = null
    }



    /** The list's answer to [revealRequest]: the row is in view and has reported its position. */
    fun onRevealed(uri: Uri) {
        revealed = uri
    }
}
