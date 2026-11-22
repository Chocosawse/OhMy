package com.ohmy.zfsync.ui.components

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ohmy.zfsync.R
import com.ohmy.zfsync.core.model.PendingPhoto
import com.ohmy.zfsync.core.model.SyncedPhoto
import com.ohmy.zfsync.core.model.takenAt
import com.ohmy.zfsync.ui.home.HomeEntry
import com.ohmy.zfsync.ui.home.SectionTitle
import com.ohmy.zfsync.ui.home.fileNumber
import com.ohmy.zfsync.ui.home.fileTitle
import com.ohmy.zfsync.ui.home.gridCells
import com.ohmy.zfsync.ui.theme.HomeTypography
import com.ohmy.zfsync.ui.theme.LocalHomeColors
import com.ohmy.zfsync.ui.theme.ScreenHorizontalPadding
import com.ohmy.zfsync.ui.theme.animationsEnabled
import com.ohmy.zfsync.ui.theme.fadeSpec
import com.ohmy.zfsync.ui.theme.overshootSpec
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The gap between cells, across and down. */
private val GridGap = 4.dp

/** A cell is at least this wide; the grid fits as many as it can, but never fewer than [MIN_COLUMNS]. */
private val MinCellWidth = 160.dp
private const val MIN_COLUMNS = 3

/** How long a cell's image fades in the first time it loads (not from the memory cache). */
private const val CELL_FADE_MILLIS = 150

/** How much the scrim darkens a selected photo, in ink. */
private const val SELECTED_SCRIM_ALPHA = 0.2f

/** How many columns the grid has across [contentWidthPx]: as many [MinCellWidth] ones as fit, at least [MIN_COLUMNS]. */
private fun Density.gridColumns(contentWidthPx: Int): Int = maxOf(MIN_COLUMNS, contentWidthPx / MinCellWidth.roundToPx())

/** [gridColumns] columns, sharing the content width evenly. */
private object HomeGridCells : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val count = gridColumns(availableSize)
        val cells = availableSize - spacing * (count - 1)
        val width = cells / count
        val remainder = cells % count
        return List(count) { width + if (it < remainder) 1 else 0 }
    }
}

/**
 * The home screen's grid layout: [entries] as square photos only, under the [header], with
 * section headings across the full width. Tap opens a photo; long-press selects, and dragging on
 * selects the rectangle of photos between the one pressed and the finger. In select mode a tap
 * toggles. Pending shots hold their place as tiles with their progress, and can't be
 * opened or selected.
 */
@Composable
internal fun HomePhotoGrid(
    entries: List<HomeEntry>,
    gridState: LazyGridState,
    selection: HomeSelection,
    onPhotoClick: (index: Int) -> Unit,
    header: @Composable () -> Unit,
    sectionTitle: @Composable (SectionTitle) -> String,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    photoTransition: PhotoTransitionState? = null,
    contentAlpha: () -> Float = { 1f },
) {
    val handover = if (animationsEnabled()) PENDING_HANDOVER_MILLIS else 0
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val startPaddingPx = with(density) { ScreenHorizontalPadding.roundToPx() }
    // Each photo's line and column, for drag-selecting a rectangle of them.
    var gridWidthPx by remember { mutableIntStateOf(0) }
    val columns = with(density) { gridColumns(gridWidthPx - 2 * startPaddingPx) }
    val cells = remember(entries, columns) {
        gridCells(entries, columns) { it !is HomeEntry.Photo && it !is HomeEntry.Pending }
            .zip(entries)
            .mapNotNull { (cell, entry) -> if (cell != null && entry is HomeEntry.Photo) entry.photo.uri to cell else null }
            .toMap()
    }
    val latestCells by rememberUpdatedState(cells)
    // The drag gesture outlives recompositions, so it reads the latest entries through this.
    val latestEntries by rememberUpdatedState(entries)

    if (photoTransition != null) {
        RevealOnRequest(photoTransition, entries) { index, uri ->
            val info = gridState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.key == uri }
            val fullyVisible = item != null && item.offset.y >= info.viewportStartOffset &&
                item.offset.y + item.size.height <= info.viewportEndOffset - info.afterContentPadding
            if (!fullyVisible) gridState.scrollToItem(index)
        }
    }

    LazyVerticalGrid(
        columns = HomeGridCells,
        state = gridState,
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { gridWidthPx = it.width }
            .verticalFadingEdges(gridState)
            // Long-press a photo to select it, then drag on to select (or deselect) the rectangle
            // of photos between it and the finger.
            .dragToSelect(
                gridState = gridState,
                startPaddingPx = startPaddingPx,
                selectableKeys = { latestEntries.mapNotNull { (it as? HomeEntry.Photo)?.photo?.uri } },
                cellOf = { latestCells },
                selection = { selection.selected },
                onSelectionChange = selection::set,
                haptics = haptics,
            ),
        contentPadding = PaddingValues(start = ScreenHorizontalPadding, end = ScreenHorizontalPadding, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(GridGap),
        verticalArrangement = Arrangement.spacedBy(GridGap),
    ) {
        items(
            entries,
            key = { it.key },
            contentType = { it.contentType },
            span = { entry ->
                if (entry is HomeEntry.Photo || entry is HomeEntry.Pending) GridItemSpan(1) else GridItemSpan(maxLineSpan)
            },
        ) { entry ->
            val fadeIn: FiniteAnimationSpec<Float> = tween(handover)
            val itemModifier = Modifier
                .animateItem(
                    fadeInSpec = if (entry is HomeEntry.Photo || entry is HomeEntry.Pending) fadeIn else fadeSpec(),
                    placementSpec = overshootSpec(),
                    fadeOutSpec = if (entry is HomeEntry.Pending) tween(handover) else fadeSpec(),
                )
                // Everything but the header fades as the layout switches.
                .then(if (entry == HomeEntry.Header) Modifier else Modifier.graphicsLayer { alpha = contentAlpha() })
            when (entry) {
                // The grid's content padding already insets it; it spans the content width.
                HomeEntry.Header -> Box(itemModifier) { header() }
                is HomeEntry.SectionHeader -> HomeSectionHeader(
                    title = sectionTitle(entry.title),
                    first = entry.first,
                    spacing = GridGap,
                    modifier = itemModifier,
                )
                HomeEntry.Empty -> Box(itemModifier) { EmptyText(stringResource(R.string.photos_section_all_empty), horizontalPadding = 0.dp) }
                is HomeEntry.Pending -> PendingCell(entry.pending, itemModifier)
                is HomeEntry.Photo -> PhotoCell(
                    photo = entry.photo,
                    isSelected = entry.photo.uri in selection.selected,
                    onClick = { if (selection.active) selection.toggle(entry.photo.uri) else onPhotoClick(entry.index) },
                    onLongPress = { selection.toggle(entry.photo.uri) },
                    photoTransition = photoTransition,
                    modifier = itemModifier,
                )
            }
        }
    }
}

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@Composable
private fun PhotoCell(
    photo: SyncedPhoto,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    photoTransition: PhotoTransitionState?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LocalHomeColors.current
    val fadeMillis = if (animationsEnabled()) CELL_FADE_MILLIS else 0
    // "DSC_3958, 8:20 PM": the section heading above it gives the day.
    val description = remember(photo) { "${fileTitle(photo.filename)}, ${TimeFormatter.format(photo.takenAt())}" }
    // This cell's photo is on its way into (or back from) the viewer: derived, so a transition
    // recomposes only the cell it's about.
    val inFlight by remember(photoTransition, photo.uri) {
        derivedStateOf { photoTransition != null && photoTransition.activeUri == photo.uri }
    }
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    if (photoTransition != null) {
        DisposableEffect(photoTransition, photo.uri) {
            onDispose { photoTransition.onThumbnailDisposed(photo.uri, coordinates[0]) }
        }
    }
    Box(
        modifier = modifier
            .then(if (inFlight) Modifier.expandAndFade { photoTransition!!.progress.value } else Modifier)
            .fillMaxWidth()
            .aspectRatio(1f)
            // The tile color while the image decodes.
            .background(colors.tile)
            // Before the click handling, so its own semantics are replaced by these. The grid's
            // dragToSelect handles the long-press gesture itself (one here would fire too and
            // toggle twice); this exposes the same action to accessibility.
            .clearAndSetSemantics {
                contentDescription = description
                selected = isSelected
                onClick { onClick(); true }
                onLongClick { onLongPress(); true }
            }
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            // Decoded at the cell's size. Coil fades in only images it had to load; one from the
            // memory cache (scrolling back, a recycled cell) shows at once.
            model = remember(photo.uri) {
                ImageRequest.Builder(context).data(photo.uri).crossfade(fadeMillis).build()
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (photoTransition == null) {
                        Modifier
                    } else {
                        Modifier
                            .onPlaced {
                                if (coordinates[0] !== it) {
                                    coordinates[0] = it
                                    photoTransition.onThumbnailPlaced(photo.uri, it)
                                }
                            }
                            // The flying copy stands in for it while the photo is on the move.
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
        if (isSelected) {
            Box(Modifier.fillMaxSize().background(colors.ink.copy(alpha = SELECTED_SCRIM_ALPHA)))
            SelectedBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
    }
}

/** A shot still on its way: its file number on the tile, a chip saying how far it's got, and its progress bar. */
@Composable
private fun PendingCell(pending: PendingPhoto, modifier: Modifier = Modifier) {
    val colors = LocalHomeColors.current
    val description = pendingDescription(pending)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(colors.tile)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(
            fileNumber(pending.filename),
            style = HomeTypography.tileNumber,
            color = colors.tileText,
            modifier = Modifier.align(Alignment.Center),
        )
        Text(
            if (pending.inProgress) {
                stringResource(R.string.pending_percent, pending.percent)
            } else {
                stringResource(R.string.pending_queued_chip)
            },
            style = HomeTypography.tileChip,
            color = colors.ink,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 11.dp)
                .background(colors.background, RoundedCornerShape(percent = 50))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
        PendingBar(pending, Modifier.align(Alignment.BottomCenter))
    }
}
