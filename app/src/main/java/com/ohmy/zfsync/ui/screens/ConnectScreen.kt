package com.ohmy.zfsync.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ohmy.zfsync.R
import com.ohmy.zfsync.core.model.ConnectionState
import com.ohmy.zfsync.core.model.SavedCameraCredentials
import com.ohmy.zfsync.core.model.SyncUiState
import com.ohmy.zfsync.ui.components.PhotoList
import com.ohmy.zfsync.ui.components.UnderlinedTextField
import com.ohmy.zfsync.ui.theme.ScreenHorizontalPadding

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

    Column(modifier = modifier.fillMaxSize().padding(top = 16.dp)) {
        // Only the form gets the side margin; the list spans the width and insets its own rows.
        // Spacing follows the editorial layout: tight within a group (title and its instructions,
        // the two fields), wider between groups.
        Column(modifier = Modifier.padding(horizontal = ScreenHorizontalPadding)) {
            Text(text = stringResource(R.string.connect_title), style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(16.dp))
            val lead = stringResource(R.string.connect_instructions_lead)
            val instructions = stringResource(R.string.connect_instructions)
            Text(
                text = remember(lead, instructions) {
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(lead) }
                        append(' ')
                        append(instructions)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(24.dp))
            UnderlinedTextField(
                value = ssid,
                onValueChange = { ssid = it },
                label = stringResource(R.string.connect_ssid_label),
                enabled = !isConnecting,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
            UnderlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.connect_password_label),
                enabled = !isConnecting,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { onConnect(ssid.trim(), password) },
                enabled = !isConnecting && ssid.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(ConnectButtonHeight),
            ) {
                val label = when {
                    isConnecting -> stringResource(R.string.connect_button_connecting)
                    isReconnect -> stringResource(R.string.connect_button_reconnect, ssid)
                    else -> stringResource(R.string.connect_button)
                }
                Text(label)
            }
            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
            }
            if (connectionState is ConnectionState.Error) {
                Text(
                    text = connectionState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }

        // Previously synced photos live on the phone, so they're browsable (and shareable /
        // deletable) without a camera connection.
        Spacer(modifier = Modifier.height(24.dp))
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

/** Taller than Material's 40dp default: the form's one action, full width, at the screen's scale. */
private val ConnectButtonHeight = 48.dp
