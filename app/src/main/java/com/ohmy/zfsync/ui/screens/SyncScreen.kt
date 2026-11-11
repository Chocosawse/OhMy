package com.ohmy.zfsync.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ohmy.zfsync.R
import com.ohmy.zfsync.model.SyncUiState
import com.ohmy.zfsync.model.SyncedPhoto
import com.ohmy.zfsync.sync.DownloadStatus
import java.text.DateFormat
import java.util.Date

@Composable
fun SyncScreen(
    state: SyncUiState,
    downloadStatus: DownloadStatus,
    cameraIp: String,
    onAutoSyncChange: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = stringResource(R.string.sync_connected_to, cameraIp),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = pluralStringResource(R.plurals.sync_photo_count, state.downloadedPhotos.size, state.downloadedPhotos.size),
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
                Text(stringResource(R.string.sync_downloading), style = MaterialTheme.typography.bodyMedium)
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

        val context = LocalContext.current
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.downloadedPhotos.asReversed()) { photo ->
                PhotoRow(photo, onClick = { openInGallery(context, photo.uri) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PhotoRow(photo: SyncedPhoto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.filename,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
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

/**
 * MediaStore.ACTION_REVIEW opens the system photo viewer showing the whole album/bucket the
 * photo belongs to, scrolled to this one -- swipeable -- rather than ACTION_VIEW's single-photo
 * behavior (which depends on how the particular receiving gallery app chooses to handle it).
 * Falls back to plain ACTION_VIEW if no installed app declares support for ACTION_REVIEW.
 */
private fun openInGallery(context: Context, uri: Uri) {
    val reviewIntent = Intent(MediaStore.ACTION_REVIEW).apply {
        setDataAndType(uri, "image/jpeg")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "image/jpeg")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(reviewIntent)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(viewIntent)
        } catch (e2: ActivityNotFoundException) {
            Toast.makeText(context, context.getString(R.string.sync_no_viewer_app), Toast.LENGTH_SHORT).show()
        }
    }
}
