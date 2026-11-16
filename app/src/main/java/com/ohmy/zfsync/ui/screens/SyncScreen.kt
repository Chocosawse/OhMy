package com.ohmy.zfsync.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ohmy.zfsync.R
import com.ohmy.zfsync.model.SyncUiState
import com.ohmy.zfsync.model.SyncedPhoto
import com.ohmy.zfsync.model.photosNewestFirst
import com.ohmy.zfsync.sync.DownloadStatus
import com.ohmy.zfsync.ui.components.ActionPill
import com.ohmy.zfsync.ui.components.dragToSelect
import com.ohmy.zfsync.ui.components.sharePhotos
import java.text.DateFormat
import java.util.Date

private val SelectionSaver = listSaver<Set<Uri>, Uri>(save = { it.toList() }, restore = { it.toSet() })

/** Leaves room at the bottom of the list so the floating pill never covers the last row. */
private val PillClearance = 88.dp

@Composable
fun SyncScreen(
    state: SyncUiState,
    downloadStatus: DownloadStatus,
    cameraIp: String,
    onAutoSyncChange: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onDisconnect: () -> Unit,
    onPhotoClick: (index: Int) -> Unit,
    onDeletePhotos: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Select mode is simply "something is selected": long-press selects the first photo, and
    // deselecting the last one (or Cancel / system back) leaves it.
    var selected by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf(emptySet<Uri>()) }
    val selectMode = selected.isNotEmpty()
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    // Same ordering the viewer uses, so a row's index here is its page index there.
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

    val onRowClick: (SyncedPhoto, Int) -> Unit = { photo, index ->
        if (selectMode) toggle(photo) else onPhotoClick(index)
    }
    val listState = rememberLazyListState()
    // The drag gesture outlives recompositions, so it reads the latest list through this.
    val latestOrdered by rememberUpdatedState(ordered)

    // Keeps the pill's count steady while it animates out after the selection is cleared,
    // instead of flashing "0 selected".
    val lastSelectedCount = remember { intArrayOf(0) }
    if (selectMode) lastSelectedCount[0] = selected.size
    val pillCount = lastSelectedCount[0]

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                text = stringResource(R.string.sync_connected_to, cameraIp),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = pluralStringResource(R.plurals.sync_photo_count, state.photos.size, state.photos.size),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            if (downloadStatus is DownloadStatus.Downloading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 16.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        pluralStringResource(R.plurals.sync_downloading_count, downloadStatus.count, downloadStatus.count),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.sync_auto_sync_label), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = state.autoSyncEnabled, onCheckedChange = onAutoSyncChange)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = onSyncNow, enabled = !state.isSyncing, modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Text(stringResource(if (state.isSyncing) R.string.sync_now_syncing else R.string.sync_now_button))
                }
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Text(stringResource(R.string.sync_disconnect_button))
                }
            }

            state.lastError?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            val sessionCount = ordered.count { it.uri in state.sessionPhotoUris }
            val sessionPhotos = ordered.take(sessionCount)
            val earlierPhotos = ordered.drop(sessionCount)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .dragToSelect(
                        listState = listState,
                        selectableKeys = { latestOrdered.map { it.uri } },
                        selection = { selected },
                        onSelectionChange = { selected = it },
                        haptics = haptics,
                    ),
                contentPadding = PaddingValues(bottom = if (selectMode) PillClearance else 0.dp),
            ) {
                item(key = "header-session") { SectionHeader(stringResource(R.string.sync_section_this_session)) }
                val inFlight = (downloadStatus as? DownloadStatus.Downloading)?.count ?: 0
                items(inFlight, key = { "placeholder-$it" }) {
                    PlaceholderRow()
                    HorizontalDivider()
                }
                if (sessionPhotos.isEmpty() && inFlight == 0) {
                    item(key = "session-empty") {
                        Text(
                            stringResource(R.string.sync_section_this_session_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                itemsIndexed(sessionPhotos, key = { _, photo -> photo.uri }) { index, photo ->
                    PhotoRow(
                        photo = photo,
                        isSelected = photo.uri in selected,
                        onClick = { onRowClick(photo, index) },
                        onLongPress = { toggle(photo) },
                    )
                    HorizontalDivider()
                }
                if (earlierPhotos.isNotEmpty()) {
                    item(key = "header-earlier") { SectionHeader(stringResource(R.string.sync_section_earlier)) }
                    itemsIndexed(earlierPhotos, key = { _, photo -> photo.uri }) { index, photo ->
                        PhotoRow(
                            photo = photo,
                            isSelected = photo.uri in selected,
                            onClick = { onRowClick(photo, sessionCount + index) },
                            onLongPress = { toggle(photo) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = selectMode,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            enter = slideInVertically { it } + scaleIn(initialScale = 0.8f) + fadeIn(),
            exit = slideOutVertically { it } + scaleOut(targetScale = 0.8f) + fadeOut(),
        ) {
            ActionPill(
                label = pluralStringResource(R.plurals.select_count, pillCount, pillCount),
                onShare = { sharePhotos(context, ordered.filter { it.uri in selected }) },
                onDelete = { confirmDelete = true },
                onCancel = { selected = emptySet() },
            )
        }
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

/** Stands in for a photo that's still downloading; same footprint as [PhotoRow] so the list doesn't jump. */
@Composable
private fun PlaceholderRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
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
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun PhotoRow(
    photo: SyncedPhoto,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            // The list's dragToSelect handles the actual long-press gesture (a row-level one
            // would fire too and double-toggle); this exposes the same action to accessibility.
            .semantics { onLongClick { onLongPress(); true } }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            AsyncImage(
                model = photo.uri,
                contentDescription = photo.filename,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
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
        }
    }
}
