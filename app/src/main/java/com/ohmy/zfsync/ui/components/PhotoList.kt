package com.ohmy.zfsync.ui.components

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ohmy.zfsync.R
import com.ohmy.zfsync.core.model.SyncUiState
import com.ohmy.zfsync.core.model.SyncedPhoto
import com.ohmy.zfsync.core.model.photosNewestFirst
import com.ohmy.zfsync.ui.theme.ScreenHorizontalPadding
import com.ohmy.zfsync.ui.theme.onSurfaceTertiary
import com.ohmy.zfsync.ui.theme.pressExpand
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.filterNotNull

private val SelectionSaver = listSaver<Set<Uri>, Uri>(save = { it.toList() }, restore = { it.toSet() })

/** Gap between the floating pill and the bottom inset (navigation bar or keyboard). */
private val PillBottomMargin = 24.dp

/** Leaves room at the bottom of the list so the floating pill never covers the last row. */
private val PillClearance = 88.dp + PillBottomMargin

/** Rows span the screen, so they expand far less than small controls (PRESS_EXPAND_SCALE). */
private const val ROW_PRESS_EXPAND_SCALE = 1.01f

/** Each row's thumbnail; the rows' text sits alongside it. */
private val ThumbnailSize = 72.dp

/** A row's top and bottom padding, and the gap between its thumbnail and text. */
private val RowVerticalPadding = 12.dp
private val ThumbnailTextGap = 16.dp

/**
 * The album as a selectable list: tap opens a photo, long-press (and drag) selects, and a
 * floating pill acts on the selection. Rows are in [photosNewestFirst] order, which is also the
 * viewer's page order, so [onPhotoClick]'s index can be handed straight to the viewer.
 *
 * With [showSessionSections], photos downloaded this session (plus [inFlightCount] placeholder
 * rows for downloads still running) are split out under their own header; without it, the whole
 * album is one section.
 */
@Composable
fun PhotoList(
    state: SyncUiState,
    showSessionSections: Boolean,
    onPhotoClick: (index: Int) -> Unit,
    onDeletePhotos: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier,
    inFlightCount: Int = 0,
    photoTransition: PhotoTransitionState? = null,
) {
    // Select mode is simply "something is selected": long-press selects the first photo, and
    // deselecting the last one (or Cancel / system back) leaves it.
    var selected by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf(emptySet<Uri>()) }
    val selectMode = selected.isNotEmpty()
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val ordered = state.photosNewestFirst()
    val entries = listEntries(state, ordered, showSessionSections, inFlightCount)

    // A selected photo can vanish underneath us (deleted in another app, picked up on resume).
    LaunchedEffect(state.photos) {
        val present = state.photos.mapTo(HashSet()) { it.uri }
        if (!present.containsAll(selected)) selected = selected.filterTo(HashSet()) { it in present }
    }

    BackHandler(enabled = selectMode) { selected = emptySet() }

    fun toggle(photo: SyncedPhoto) {
        selected = if (photo.uri in selected) selected - photo.uri else selected + photo.uri
    }

    val listState = rememberLazyListState()
    val bottomInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    // The drag gesture outlives recompositions, so it reads the latest list through this.
    val latestOrdered by rememberUpdatedState(ordered)

    // Keeps the pill's count steady while it animates out after the selection is cleared,
    // instead of flashing "0 selected".
    val lastSelectedCount = remember { intArrayOf(0) }
    if (selectMode) lastSelectedCount[0] = selected.size
    val pillCount = lastSelectedCount[0]

    // A closing viewer asks for its photo's row, so it can shrink back into it: scroll it into
    // view if it isn't, wait for the rows to lay out (and report where their thumbnails are),
    // then answer.
    if (photoTransition != null) {
        val latestEntries by rememberUpdatedState(entries)
        LaunchedEffect(photoTransition, listState) {
            snapshotFlow { photoTransition.revealRequest }.filterNotNull().collect { uri ->
                val index = latestEntries.indexOfFirst { it.key == uri }
                if (index >= 0 && !listState.isFullyVisible(uri)) {
                    // One row above it in view too, so it isn't right under the top fading edge.
                    listState.scrollToItem((index - 1).coerceAtLeast(0))
                }
                // Either way, let the rows be placed before answering: the list isn't placed at
                // all while the viewer is open, and only is again now that it's closing. A frame's
                // layout runs after its frame callbacks, hence two.
                repeat(2) { withFrameNanos { } }
                photoTransition.onRevealed(uri)
            }
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // Rows fade out at the list's edges rather than being cut off.
                .verticalFadingEdges(listState)
                .dragToSelect(
                    listState = listState,
                    selectableKeys = { latestOrdered.map { it.uri } },
                    selection = { selected },
                    onSelectionChange = { selected = it },
                    haptics = haptics,
                ),
            // The list runs behind the navigation bar (and keyboard), so its content is padded
            // for them: the last row can scroll clear of both, plus the pill when it's showing.
            contentPadding = PaddingValues(bottom = bottomInset + if (selectMode) PillClearance else 0.dp),
        ) {
            items(entries, key = { it.key }, contentType = { it.contentType }) { entry ->
                when (entry) {
                    is ListEntry.Header -> SectionHeader(stringResource(entry.title))
                    is ListEntry.Empty -> EmptyText(stringResource(entry.text))
                    is ListEntry.Placeholder -> {
                        PlaceholderRow()
                        RowDivider()
                    }
                    is ListEntry.Photo -> {
                        val photo = entry.photo
                        PhotoRow(
                            photo = photo,
                            isSelected = photo.uri in selected,
                            onClick = { if (selectMode) toggle(photo) else onPhotoClick(entry.index) },
                            onLongPress = { toggle(photo) },
                            photoTransition = photoTransition,
                        )
                        RowDivider()
                    }
                }
            }
        }

        ActionPill(
            visible = selectMode,
            pulseKey = selected.size,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(bottom = PillBottomMargin),
            label = pluralStringResource(R.plurals.select_count, pillCount, pillCount),
            onShare = { sharePhotos(context, ordered.filter { it.uri in selected }) },
            onDelete = { confirmDelete = true },
            // Only when every selected photo has a RAW: "open these RAWs" shouldn't skip some.
            onRaw = ordered.filter { it.uri in selected }
                .takeIf { selection -> selection.isNotEmpty() && selection.all { it.rawUri != null } }
                ?.let { selection -> { openRaws(context, selection) } },
            onCancel = { selected = emptySet() },
        )
    }

    if (confirmDelete) {
        val count = selected.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(pluralStringResource(R.plurals.select_delete_dialog_title, count, count)) },
            text = { Text(pluralStringResource(R.plurals.select_delete_dialog_text, count)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePhotos(selected.toList())
                    selected = emptySet()
                    confirmDelete = false
                }) { Text(stringResource(R.string.viewer_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.viewer_delete_cancel)) }
            },
        )
    }
}

/** One item of the list, in order; [Photo.index] is the photo's position in [photosNewestFirst]. */
private sealed interface ListEntry {
    val key: Any
    val contentType: String

    data class Header(@StringRes val title: Int, override val key: String) : ListEntry {
        override val contentType get() = "header"
    }

    data class Empty(@StringRes val text: Int, override val key: String) : ListEntry {
        override val contentType get() = "empty"
    }

    data class Placeholder(val number: Int) : ListEntry {
        override val key get() = "placeholder-$number"
        override val contentType get() = "placeholder"
    }

    data class Photo(val photo: SyncedPhoto, val index: Int) : ListEntry {
        override val key: Any get() = photo.uri
        override val contentType get() = "photo"
    }
}

/**
 * With [showSessionSections], photos downloaded this session (newest first in [ordered], so at
 * its start) come under their own header, after a placeholder for each download in flight.
 */
private fun listEntries(
    state: SyncUiState,
    ordered: List<SyncedPhoto>,
    showSessionSections: Boolean,
    inFlightCount: Int,
): List<ListEntry> = buildList {
    if (showSessionSections) {
        val sessionCount = ordered.count { it.uri in state.sessionPhotoUris }
        add(ListEntry.Header(R.string.sync_section_this_session, key = "header-session"))
        repeat(inFlightCount) { add(ListEntry.Placeholder(it)) }
        if (sessionCount == 0 && inFlightCount == 0) {
            add(ListEntry.Empty(R.string.sync_section_this_session_empty, key = "session-empty"))
        }
        ordered.take(sessionCount).forEachIndexed { index, photo -> add(ListEntry.Photo(photo, index)) }
        if (ordered.size > sessionCount) {
            add(ListEntry.Header(R.string.sync_section_earlier, key = "header-earlier"))
            ordered.drop(sessionCount).forEachIndexed { index, photo -> add(ListEntry.Photo(photo, sessionCount + index)) }
        }
    } else {
        add(ListEntry.Header(R.string.photos_section_all, key = "header-all"))
        if (ordered.isEmpty()) add(ListEntry.Empty(R.string.photos_section_all_empty, key = "all-empty"))
        ordered.forEachIndexed { index, photo -> add(ListEntry.Photo(photo, index)) }
    }
}

/** Whether the item keyed [key] is entirely within the list's viewport (clear of the bottom content padding). */
private fun LazyListState.isFullyVisible(key: Any): Boolean {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return false
    return item.offset >= info.viewportStartOffset && item.offset + item.size <= info.viewportEndOffset - info.afterContentPadding
}

/** How much a row grows as it fades out behind its photo flying into the viewer. */
private const val ROW_TRANSITION_EXPAND = 0.05f

/**
 * Scales the row up by [ROW_TRANSITION_EXPAND] and fades it out as [progress] goes 0 to 1. The
 * scale is a draw transform rather than a layer one, so the row's (and its thumbnail's) reported
 * position stays where it's laid out, which is where the photo flies back to.
 */
private fun Modifier.expandAndFade(progress: () -> Float): Modifier = this
    .graphicsLayer { alpha = 1f - progress() }
    .drawWithContent {
        val factor = 1f + ROW_TRANSITION_EXPAND * progress()
        scale(factor) { this@drawWithContent.drawContent() }
    }

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // Lines up with the screen's own text, which the list (spanning the full width) doesn't inherit.
        modifier = Modifier.padding(horizontal = ScreenHorizontalPadding, vertical = 8.dp),
    )
}

/** Stands in for a photo that's still downloading; same footprint as [PhotoRow] so the list doesn't jump. */
@Composable
private fun PlaceholderRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenHorizontalPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ThumbnailTextGap),
    ) {
        Box(
            modifier = Modifier
                .size(ThumbnailSize)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        Column {
            Text(stringResource(R.string.sync_placeholder_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.sync_placeholder_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Between rows: a hairline inset to the screen margins, like a rule in a printed list. */
@Composable
private fun RowDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = ScreenHorizontalPadding))
}

/** A quiet serif heading, set in the secondary text color rather than an accent. */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenHorizontalPadding)
            .padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun PhotoRow(
    photo: SyncedPhoto,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    photoTransition: PhotoTransitionState?,
) {
    val interactionSource = remember { MutableInteractionSource() }
    // This row's photo is on its way into (or back from) the viewer. Derived, so a transition
    // starting recomposes only the row it's about, not every visible one.
    val inFlight by remember(photoTransition, photo.uri) {
        derivedStateOf { photoTransition != null && photoTransition.activeUri == photo.uri }
    }
    val thumbnailCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
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
                model = photo.uri,
                contentDescription = photo.filename,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(ThumbnailSize)
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
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
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
