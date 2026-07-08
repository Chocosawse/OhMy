package com.ohmy.zfsync.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ohmy.zfsync.R
import com.ohmy.zfsync.model.SyncUiState
import java.text.DateFormat
import java.util.Date

@Composable
fun SyncScreen(
    state: SyncUiState,
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

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.downloadedPhotos.asReversed()) { photo ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(photo.filename, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        DateFormat.getDateTimeInstance().format(Date(photo.savedAtMillis)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
