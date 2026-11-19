package com.ohmy.zfsync.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ohmy.zfsync.R
import com.ohmy.zfsync.model.ConnectionState
import com.ohmy.zfsync.model.SyncUiState
import com.ohmy.zfsync.storage.SavedCameraCredentials
import com.ohmy.zfsync.ui.components.PhotoList

@Composable
fun ConnectScreen(
    state: SyncUiState,
    savedCredentials: SavedCameraCredentials?,
    onConnect: (ssid: String, password: String) -> Unit,
    onPhotoClick: (index: Int) -> Unit,
    onDeletePhotos: (List<Uri>) -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connectionState = state.connectionState
    var ssid by remember { mutableStateOf(savedCredentials?.ssid.orEmpty()) }
    var password by remember { mutableStateOf(savedCredentials?.password.orEmpty()) }
    val isConnecting = connectionState is ConnectionState.Connecting
    val isReconnect = savedCredentials != null && ssid == savedCredentials.ssid && password == savedCredentials.password

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = stringResource(R.string.connect_title), style = MaterialTheme.typography.headlineSmall)
            Text(text = stringResource(R.string.connect_instructions), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = ssid,
                onValueChange = { ssid = it },
                label = { Text(stringResource(R.string.connect_ssid_label)) },
                singleLine = true,
                enabled = !isConnecting,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.connect_password_label)) },
                singleLine = true,
                enabled = !isConnecting,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onConnect(ssid.trim(), password) },
                enabled = !isConnecting && ssid.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val label = when {
                    isConnecting -> stringResource(R.string.connect_button_connecting)
                    isReconnect -> stringResource(R.string.connect_button_reconnect, ssid)
                    else -> stringResource(R.string.connect_button)
                }
                Text(label)
            }
            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
            }
            if (connectionState is ConnectionState.Error) {
                Text(
                    text = connectionState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // Previously synced photos live on the phone, so they're browsable (and shareable /
        // deletable) without a camera connection.
        HorizontalDivider(modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        PhotoList(
            state = state,
            showSessionSections = false,
            onPhotoClick = onPhotoClick,
            onDeletePhotos = onDeletePhotos,
            onOpenMenu = onOpenMenu,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}
