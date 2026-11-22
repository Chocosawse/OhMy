package com.ohmy.zfsync.ui.components

import android.net.Uri
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ohmy.zfsync.R
import com.ohmy.zfsync.core.model.PendingPhoto
import com.ohmy.zfsync.core.model.SyncedPhoto
import com.ohmy.zfsync.ui.home.HomeEntry
import com.ohmy.zfsync.ui.home.SectionTitle
import com.ohmy.zfsync.ui.home.fileTitle
import com.ohmy.zfsync.ui.theme.HomeTypography
import com.ohmy.zfsync.ui.theme.LocalHomeColors
import com.ohmy.zfsync.ui.theme.ScreenHorizontalPadding
import com.ohmy.zfsync.ui.theme.animationsEnabled
import com.ohmy.zfsync.ui.theme.fadeSpec
import com.ohmy.zfsync.ui.theme.onSurfaceTertiary
import com.ohmy.zfsync.ui.theme.overshootSpec
import com.ohmy.zfsync.ui.theme.pressExpand
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.filterNotNull

/** Rows span the screen, so they expand far less than small controls (PRESS_EXPAND_SCALE). */
private const val ROW_PRESS_EXPAND_SCALE = 1.01f

/** Each row's thumbnail; the rows' text sits alongside it. */
private val ThumbnailSize = 80.dp

/** How long a list thumbnail takes to fade in as it loads. */
private const val THUMBNAIL_FADE_MILLIS = 250

/** A row's top and bottom padding, and the gap between its thumbnail and text. */
private val RowVerticalPadding = 6.dp
private val ThumbnailTextGap = 16.dp

/**
 * The home screen's list layout: [entries] as rows with details, under the [header]. Tap opens a
 * photo (or, in select mode, toggles it); long-press (and drag) selects. [onPhotoClick]'s index is
 * the photo's place in the viewer's order. A pending shot's row holds its place, with its download
 * progress, until the photo replaces it.
 */
@Composable
internal fun HomePhotoList(
    entries: List<HomeEntry>,
    listState: LazyListState,
    selection: HomeSelection,
    onPhotoClick: (index: Int) -> Unit,
    header: @Composable () -> Unit,
    sectionTitle: @Composable (SectionTitle) -> String,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    photoTransition: PhotoTransitionState? = null,
    contentAlpha: () -> Float = { 1f },
) {
    val haptics = LocalHapticFeedback.current
    // The drag gesture outlives recompositions, so it reads the latest entries through this.
    val latestEntries by rememberUpdatedState(entries)
    val handover = if (animationsEnabled()) PENDING_HANDOVER_MILLIS else 0

    if (photoTransition != null) {
        RevealOnRequest(photoTransition, entries) { index, uri ->
            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == uri }
            val info = listState.layoutInfo
            val fullyVisible = item != null && item.offset >= info.viewportStartOffset &&
                item.offset + item.size <= info.viewportEndOffset - info.afterContentPadding
            // One row above it in view too, so it isn't right under the pinned status row.
            if (!fullyVisible) listState.scrollToItem((index - 1).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            // Rows fade out at the bottom edge rather than being cut off.
            .verticalFadingEdges(listState)
            .dragToSelect(
                listState = listState,
                selectableKeys = { latestEntries.mapNotNull { (it as? HomeEntry.Photo)?.photo?.uri } },
                selection = { selection.selected },
                onSelectionChange = selection::set,
                haptics = haptics,
            ),
        // The list runs behind the navigation bar (and keyboard), so its content is padded for
        // them: the last row can scroll clear of both, plus the pill when it's showing.
        contentPadding = PaddingValues(bottom = bottomPadding),
    ) {
        items(entries, key = { it.key }, contentType = { it.contentType }) { entry ->
            // Deleted rows fade out while the ones below slide up into their place; arrivals fade
            // in and push the rest along. A pending row and its photo crossfade in place.
            val fadeIn: FiniteAnimationSpec<Float> = tween(handover)
            Column(
                modifier = Modifier
                    .animateItem(
                        fadeInSpec = if (entry is HomeEntry.Photo || entry is HomeEntry.Pending) fadeIn else fadeSpec(),
                        placementSpec = overshootSpec(),
                        fadeOutSpec = if (entry is HomeEntry.Pending) tween(handover) else fadeSpec(),
                    )
                    // Everything but the header fades as the layout switches.
                    .then(if (entry == HomeEntry.Header) Modifier else Modifier.graphicsLayer { alpha = contentAlpha() }),
            ) {
                when (entry) {
                    HomeEntry.Header -> Box(Modifier.padding(horizontal = ScreenHorizontalPadding)) { header() }
                    is HomeEntry.SectionHeader -> HomeSectionHeader(
                        title = sectionTitle(entry.title),
                        first = entry.first,
                        modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
                    )
                    HomeEntry.Empty -> EmptyText(stringResource(R.string.photos_section_all_empty))
                    is HomeEntry.Pending -> {
                        PendingRow(entry.pending)
                        RowDivider()
                    }
                    is HomeEntry.Photo -> {
                        val photo = entry.photo
                        PhotoRow(
                            photo = photo,
                            isSelected = photo.uri in selection.selected,
                            onClick = { if (selection.active) selection.toggle(photo.uri) else onPhotoClick(entry.index) },
                            onLongPress = { selection.toggle(photo.uri) },
                            photoTransition = photoTransition,
                        )
                        RowDivider()
                    }
                }
            }
        }
    }
}

/** How long a pending shot and its arrived photo take to crossfade. */
internal const val PENDING_HANDOVER_MILLIS = 200

/**
 * Answers a closing viewer's request for its photo: [scrollIntoView] brings the photo (at its
 * index in [entries]) into view if it isn't, then, once the list or grid has laid out (and
 * reported where its thumbnails are), the viewer is told.
 */
@Composable
internal fun RevealOnRequest(
    photoTransition: PhotoTransitionState,
    entries: List<HomeEntry>,
    scrollIntoView: suspend (index: Int, uri: Uri) -> Unit,
) {
    val latestEntries by rememberUpdatedState(entries)
    val latestScroll by rememberUpdatedState(scrollIntoView)
    LaunchedEffect(photoTransition) {
        snapshotFlow { photoTransition.revealRequest }.filterNotNull().collect { uri ->
            val index = latestEntries.indexOfFirst { it.key == uri }
            if (index >= 0) latestScroll(index, uri)
            // Either way, let the content be placed before answering: it isn't placed at all
            // while the viewer is open, and only is again now that it's closing. A frame's layout
            // runs after its frame callbacks, hence two.
            repeat(2) { withFrameNanos { } }
            photoTransition.onRevealed(uri)
        }
    }
}

/**
 * A section's heading: serif, in ink, marked as a heading. More space above the later ones,
 * which follow photos, than the first, which follows the library row.
 */
@Composable
internal fun HomeSectionHeader(title: String, first: Boolean, modifier: Modifier = Modifier, spacing: Dp = 0.dp) {
    Text(
        text = title,
        style = HomeTypography.section,
        color = LocalHomeColors.current.ink,
        modifier = modifier
            .fillMaxWidth()
            .semantics { heading() }
            // [spacing] is what the container already puts between items, taken off here.
            .padding(top = (if (first) 22.dp else 28.dp) - spacing, bottom = 12.dp - spacing),
    )
}

/** A pending shot's 3dp progress bar along a tile's bottom: empty while queued, filling as it downloads. */
@Composable
internal fun PendingBar(pending: PendingPhoto, modifier: Modifier = Modifier) {
    val colors = LocalHomeColors.current
    val fraction by animateFloatAsState(
        targetValue = pending.percent / 100f,
        animationSpec = tween(if (animationsEnabled()) PROGRESS_MILLIS else 0, easing = EaseOut),
        label = "pendingProgress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(colors.tileTrack)
            .drawBehind { drawRect(colors.accent, size = Size(size.width * fraction, size.height)) },
    )
}

/** How long a progress fill takes to reach a new value. */
internal const val PROGRESS_MILLIS = 300

/** "DSC_3961, waiting to sync" or "DSC_3960, syncing, 64 percent". */
@Composable
internal fun pendingDescription(pending: PendingPhoto): String =
    if (pending.inProgress) {
        stringResource(R.string.pending_syncing_description, fileTitle(pending.filename), pending.percent)
    } else {
        stringResource(R.string.pending_queued_description, fileTitle(pending.filename))
    }

/** A shot still on its way: a tile-colored thumbnail with its progress bar, where its photo will be. Not openable or selectable. */
@Composable
private fun PendingRow(pending: PendingPhoto) {
    val colors = LocalHomeColors.current
    val description = pendingDescription(pending)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = ScreenHorizontalPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ThumbnailTextGap),
    ) {
        Box(modifier = Modifier.size(ThumbnailSize).background(colors.tile)) {
            PendingBar(pending, Modifier.align(Alignment.BottomCenter))
        }
        Column {
            Text(pending.filename, style = MaterialTheme.typography.titleLarge)
            Text(
                if (pending.inProgress) {
                    stringResource(R.string.pending_syncing, pending.percent)
                } else {
                    stringResource(R.string.pending_queued)
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
            )
        }
    }
}

/** How much a selected photo's thumbnail shrinks, in the list and the grid, so the selection reads at a glance. */
private const val SELECTED_THUMBNAIL_SCALE = 0.85f

/** A thumbnail's scale: shrinking as its photo is selected and growing back as it's deselected. */
@Composable
internal fun animateSelectedScale(selected: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (selected) SELECTED_THUMBNAIL_SCALE else 1f,
    animationSpec = if (animationsEnabled()) overshootSpec() else snap(),
    label = "selectedThumbnailScale",
)

/** Scales the content by [scale], read in the draw layer so animating it doesn't recompose. */
internal fun Modifier.scaled(scale: State<Float>): Modifier = graphicsLayer {
    scaleX = scale.value
    scaleY = scale.value
}

/** The check on a selected photo, in the list and the grid. */
@Composable
internal fun SelectedBadge(modifier: Modifier = Modifier) {
    Icon(
        Icons.Default.CheckCircle,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .size(20.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape),
    )
}

/** How much a row grows as it fades out behind its photo flying into the viewer. */
private const val ROW_TRANSITION_EXPAND = 0.05f

/**
 * Scales the row up by [ROW_TRANSITION_EXPAND] and fades it out as [progress] goes 0 to 1. The
 * scale is a draw transform rather than a layer one, so the row's (and its thumbnail's) reported
 * position stays where it's laid out, which is where the photo flies back to.
 */
internal fun Modifier.expandAndFade(progress: () -> Float): Modifier = this
    .graphicsLayer { alpha = 1f - progress() }
    .drawWithContent {
        val factor = 1f + ROW_TRANSITION_EXPAND * progress()
        scale(factor) { this@drawWithContent.drawContent() }
    }

@Composable
internal fun EmptyText(text: String, horizontalPadding: Dp = ScreenHorizontalPadding) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // Lines up with the screen's own text, which the list (spanning the full width) doesn't inherit.
        modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 8.dp),
    )
}

/** Between rows: a hairline inset to the screen margins, like a rule in a printed list. */
@Composable
private fun RowDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = ScreenHorizontalPadding))
}

@Composable
private fun PhotoRow(
    photo: SyncedPhoto,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    photoTransition: PhotoTransitionState?,
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    // This row's photo is on its way into (or back from) the viewer. Derived, so a transition
    // starting recomposes only the row it's about, not every visible one.
    val inFlight by remember(photoTransition, photo.uri) {
        derivedStateOf { photoTransition != null && photoTransition.activeUri == photo.uri }
    }
    val thumbnailCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val selectedScale = animateSelectedScale(isSelected)
    if (photoTransition != null) {
        DisposableEffect(photoTransition, photo.uri) {
            onDispose { photoTransition.onThumbnailDisposed(photo.uri, thumbnailCoordinates[0]) }
        }
    }
    Row(
        modifier = Modifier
            .then(if (inFlight) Modifier.expandAndFade { photoTransition!!.progress.value } else Modifier)
            // Outermost, so the highlight grows with the content.
            .pressExpand(interactionSource, pressedScale = ROW_PRESS_EXPAND_SCALE)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
            // The list's dragToSelect handles the actual long-press gesture (a row-level one
            // would fire too and double-toggle); this exposes the same action to accessibility.
            .semantics { onLongClick { onLongPress(); true } }
            // Inside the background and click area, so the selection highlight still spans the row.
            .padding(horizontal = ScreenHorizontalPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ThumbnailTextGap),
    ) {
        Box {
            AsyncImage(
                // Fades in as it loads; one already in memory (scrolling back) shows at once.
                // The request keys the memory cache by URI alone, as before.
                model = remember(photo.uri) {
                    ImageRequest.Builder(context).data(photo.uri).crossfade(THUMBNAIL_FADE_MILLIS).build()
                },
                contentDescription = photo.filename,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(ThumbnailSize)
                    // Shrinks while selected; the badge, outside it, keeps its size and corner.
                    .scaled(selectedScale)
                    // The tile color until the image arrives, as in the grid.
                    .background(LocalHomeColors.current.tile)
                    .then(
                        if (photoTransition == null) {
                            Modifier
                        } else {
                            Modifier
                                .onPlaced {
                                    // The same coordinates object for the node's life, so this is a
                                    // cheap identity check on every later placement (e.g. scrolling).
                                    if (thumbnailCoordinates[0] !== it) {
                                        thumbnailCoordinates[0] = it
                                        photoTransition.onThumbnailPlaced(photo.uri, it)
                                    }
                                }
                                // The flying copy stands in for it while the photo is on the move.
                                // A layer only on the row in flight, not on every thumbnail.
                                .then(
                                    if (inFlight) {
                                        Modifier.graphicsLayer { alpha = if (photoTransition.progress.value > 0f) 0f else 1f }
                                    } else {
                                        Modifier
                                    },
                                )
                        },
                    ),
            )
            if (isSelected) SelectedBadge(Modifier.align(Alignment.TopEnd).padding(2.dp))
        }
        Column {
            Row {
                Text(photo.filename, style = MaterialTheme.typography.titleLarge, modifier = Modifier.alignByBaseline())
                if (photo.hasSeparateRaw) {
                    // The shot's RAW is here too, behind this one item.
                    Text(
                        stringResource(R.string.photo_raw_tag),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.alignByBaseline().padding(start = 8.dp),
                    )
                }
            }
            Text(
                DateFormat.getDateTimeInstance().format(Date(photo.savedAtMillis)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            photo.exposure?.toDisplayString()?.let { exposure ->
                Text(
                    exposure,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
