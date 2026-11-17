package com.ohmy.zfsync.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ohmy.zfsync.R
import com.ohmy.zfsync.model.SyncedPhoto
import com.ohmy.zfsync.ui.theme.MOTION_DURATION_MILLIS
import com.ohmy.zfsync.ui.theme.animatePop
import com.ohmy.zfsync.ui.theme.cascadeDelayMillis
import com.ohmy.zfsync.ui.theme.pop

/**
 * Floating pill of photo actions, shared by the viewer (over a black backdrop) and the list's
 * select mode (over the themed surface) -- hence the color parameters. [label] and [onCancel]
 * are optional extras the viewer doesn't need.
 *
 * Toggling [visible] animates the pill itself: showing pops the capsule in while its items
 * cascade in from both ends toward the middle; hiding cascades them out from the middle and
 * collapses the capsule last. Once fully hidden it leaves composition.
 */
@Composable
fun ActionPill(
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    label: String? = null,
    onCancel: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val items = buildList<@Composable () -> Unit> {
        if (label != null) {
            add { Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 12.dp, end = 4.dp)) }
        }
        add {
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.viewer_share))
            }
        }
        add {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.viewer_delete))
            }
        }
        if (onCancel != null) {
            add {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.select_cancel))
                }
            }
        }
    }
    val last = items.lastIndex
    // Showing: the capsule pops in first and its items cascade in once it has fully settled.
    // Hiding: the items cascade out first, and the capsule collapses as the last ones leave.
    val lastItemExitDelay = (0..last).maxOf { cascadeDelayMillis(it, 0, last, showing = false) }
    val container = animatePop(visible, delayMillis = if (visible) 0 else lastItemExitDelay + MOTION_DURATION_MILLIS / 2)
    // Stays composed until the exit finishes; derived so the animation itself doesn't recompose.
    val latestVisible by rememberUpdatedState(visible)
    val composed by remember { derivedStateOf { latestVisible || !container.isGone } }
    if (!composed) return

    Surface(
        modifier = modifier.pop(container),
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                key(index) {
                    val cascadeDelay = cascadeDelayMillis(index, 0, last, showing = visible)
                    val itemPop = animatePop(visible, if (visible) MOTION_DURATION_MILLIS + cascadeDelay else cascadeDelay)
                    Box(Modifier.pop(itemPop)) { item() }
                }
            }
        }
    }
}

/** Opens the system share sheet for one or more photos. */
fun sharePhotos(context: Context, photos: List<SyncedPhoto>) {
    if (photos.isEmpty()) return
    val uris = ArrayList(photos.map { it.uri })
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }.apply {
        type = "image/jpeg"
        // The read grant only covers URIs in the intent's data/ClipData, not EXTRA_STREAM, so
        // list them all here -- otherwise a receiving app without media access can't open them.
        clipData = ClipData.newRawUri(null, uris.first()).also { clip ->
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, context.getString(R.string.viewer_share_chooser_title))
    try {
        context.startActivity(chooser)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.sync_no_viewer_app), Toast.LENGTH_SHORT).show()
    }
}
