package com.ohmy.zfsync.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.ohmy.zfsync.R
import com.ohmy.zfsync.core.model.Album
import com.ohmy.zfsync.ui.theme.ScreenHorizontalPadding

/** How much of the device's photos the app may see. */
enum class PhotoAccess { None, Partial, Full }

/** The photos permissions to ask for on this Android version. */
private val photoPermissions: Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun Context.photoAccess(): PhotoAccess {
    fun granted(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && granted(Manifest.permission.READ_MEDIA_IMAGES) -> PhotoAccess.Full
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> PhotoAccess.Full
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> PhotoAccess.Partial
        else -> PhotoAccess.None
    }
}

/**
 * Full-screen list of the device's photo albums, to open any of them in the gallery viewer. Asks
 * for the photos permission first; with partial access (Android 14+, the user picked which photos
 * to share) it says so and offers to change the selection. [onAccessChanged] asks for [albums] to
 * be reloaded.
 */
@Composable
fun AlbumsScreen(
    albums: List<Album>,
    onAccessChanged: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    var access by remember { mutableStateOf(context.photoAccess()) }
    val requestAccess = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = context.photoAccess()
        onAccessChanged()
    }
    LaunchedEffect(Unit) { onAccessChanged() }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.theme_close))
                }
                Text(stringResource(R.string.albums_title), style = MaterialTheme.typography.titleLarge)
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = ScreenHorizontalPadding, end = ScreenHorizontalPadding, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (access) {
                    PhotoAccess.None -> item(key = "access", span = { GridItemSpan(maxLineSpan) }) {
                        AccessPrompt(onAllow = { requestAccess.launch(photoPermissions) })
                    }
                    PhotoAccess.Partial -> item(key = "partial", span = { GridItemSpan(maxLineSpan) }) {
                        PartialAccessNote(onChange = { requestAccess.launch(photoPermissions) })
                    }
                    PhotoAccess.Full -> Unit
                }
                items(albums, key = { it.id }) { album ->
                    AlbumTile(album, onClick = { onOpenAlbum(album) })
                }
            }
        }
    }
}

/** Why the app wants the photos permission, and a button to grant it. */
@Composable
private fun AccessPrompt(onAllow: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        Text(stringResource(R.string.albums_access_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.albums_access_text), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onAllow) { Text(stringResource(R.string.albums_access_button)) }
    }
}

/** With partial access: only the photos the user picked show, and they can pick again. */
@Composable
private fun PartialAccessNote(onChange: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.albums_partial),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onChange) { Text(stringResource(R.string.albums_partial_change)) }
    }
}

/** An album: its newest photo as a square cover, its name, and how many photos it holds. */
@Composable
private fun AlbumTile(album: Album, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        AsyncImage(
            model = album.coverUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Text(
            album.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            pluralStringResource(R.plurals.albums_photo_count, album.photoCount, album.photoCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
