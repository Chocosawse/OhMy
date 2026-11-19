package com.ohmy.zfsync.ui.components

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
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

private val SelectionSaver = listSaver<Set<Uri>, Uri>(save = { it.toList() }, restore = { it.toSet() })

/** Gap between the floating pill and the bottom inset (navigation bar or keyboard). */
private val PillBottomMargin = 24.dp

/** Leaves room at the bottom of the list so the floating pill never covers the last row. */
private val PillClearance = 88.dp + PillBottomMargin

/** Rows span the screen, so they expand far less than small controls (PRESS_EXPAND_SCALE). */
private const val ROW_PRESS_EXPAND_SCALE = 1.01f

/** Inset of each row's content from the list's edges, so thumbnails don't touch the screen edge. */
private val ListItemHorizontalPadding = 16.dp

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
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
    inFlightCount: Int = 0,
) {
    // Select mode is simply "something is selected": long-press selects the first photo, and
    // deselecting the last one (or Cancel / system back) leaves it.
    var selected by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf(emptySet<Uri>()) }
    val selectMode = selected.isNotEmpty()
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val ordered = state.photosNewestFirst()

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

    /** Rows for [photos], whose first element sits at [startIndex] in [ordered]. */
    fun LazyListScope.photoRows(photos: List<SyncedPhoto>, startIndex: Int) {
        itemsIndexed(photos, key = { _, photo -> photo.uri }) { index, photo ->
            PhotoRow(
                photo = photo,
                isSelected = photo.uri in selected,
                onClick = { if (selectMode) toggle(photo) else onPhotoClick(startIndex + index) },
                onLongPress = { toggle(photo) },
            )
            HorizontalDivider()
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
            if (showSessionSections) {
                val sessionCount = ordered.count { it.uri in state.sessionPhotoUris }
                item(key = "header-session") { SectionHeader(stringResource(R.string.sync_section_this_session)) }
                items(inFlightCount, key = { "placeholder-$it" }) {
                    PlaceholderRow()
                    HorizontalDivider()
                }
                if (sessionCount == 0 && inFlightCount == 0) {
                    item(key = "session-empty") { EmptyText(stringResource(R.string.sync_section_this_session_empty)) }
                }
                photoRows(ordered.take(sessionCount), startIndex = 0)
                if (ordered.size > sessionCount) {
                    item(key = "header-earlier") { SectionHeader(stringResource(R.string.sync_section_earlier)) }
                    photoRows(ordered.drop(sessionCount), startIndex = sessionCount)
                }
            } else {
                item(key = "header-all") { SectionHeader(stringResource(R.string.photos_section_all)) }
                if (ordered.isEmpty()) {
                    item(key = "all-empty") { EmptyText(stringResource(R.string.photos_section_all_empty)) }
                }
                photoRows(ordered, startIndex = 0)
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
            onCancel = { selected = emptySet() },
            onMenu = onOpenMenu,
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = ListItemHorizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        Column {
            Text(stringResource(R.string.sync_placeholder_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.sync_placeholder_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
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
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
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
            .padding(horizontal = ListItemHorizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            AsyncImage(
                model = photo.uri,
                contentDescription = photo.filename,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp),
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
            Text(photo.filename, style = MaterialTheme.typography.bodyLarge)
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
