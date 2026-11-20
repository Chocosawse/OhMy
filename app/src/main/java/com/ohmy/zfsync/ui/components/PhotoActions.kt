package com.ohmy.zfsync.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ohmy.zfsync.R
import com.ohmy.zfsync.core.model.SyncedPhoto
import com.ohmy.zfsync.ui.theme.MOTION_DURATION_MILLIS
import com.ohmy.zfsync.ui.theme.OvershootEasing
import com.ohmy.zfsync.ui.theme.animatePop
import com.ohmy.zfsync.ui.theme.cascadeDelayMillis
import com.ohmy.zfsync.ui.theme.overshootSpec
import com.ohmy.zfsync.ui.theme.pop
import com.ohmy.zfsync.ui.theme.pressExpand

/**
 * Floating pill of photo actions, shared by the viewer (over a black backdrop) and the list's
 * select mode (over the themed surface) -- hence the color parameters. [label], [onRaw] and
 * [onCancel] are optional extras; [onRaw] shows a RAW button (when the selection has RAWs).
 *
 * Toggling [visible] animates the pill itself: showing pops the capsule in while its items
 * cascade in from both ends toward the middle; hiding cascades them out from the middle and
 * collapses the capsule last. Once fully hidden it leaves composition. Each change of
 * [pulseKey] while shown (e.g. the selection count) gives the capsule a brief expand-and-settle
 * pulse.
 */
@Composable
fun ActionPill(
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    pulseKey: Any? = null,
    label: String? = null,
    onRaw: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val items = buildList<@Composable () -> Unit> {
        if (label != null) {
            add { Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 12.dp, end = 4.dp)) }
        }
        add { PillButton(onShare, Icons.Default.Share, stringResource(R.string.viewer_share)) }
        add { PillButton(onDelete, Icons.Default.Delete, stringResource(R.string.viewer_delete)) }
        if (onRaw != null) {
            add { PillTextButton(onRaw, stringResource(R.string.select_raw), stringResource(R.string.select_raw_description)) }
        }
        if (onCancel != null) {
            add { PillButton(onCancel, Icons.Default.Close, stringResource(R.string.select_cancel)) }
        }
    }
    val last = items.lastIndex
    // Showing: the capsule pops in first and its items cascade in once it has fully settled.
    // Hiding: the items cascade out first, and the capsule collapses as the last ones leave.
    val lastItemExitDelay = (0..last).maxOf { cascadeDelayMillis(it, 0, last, showing = false) }
    val container = animatePop(visible, delayMillis = if (visible) 0 else lastItemExitDelay + MOTION_DURATION_MILLIS / 2)
    // Stays composed until the exit finishes; derived so the animation itself doesn't recompose.
    val latestVisible by rememberUpdatedState(visible)
    val composed by remember { derivedStateOf { latestVisible || container.value > 0f } }
    if (!composed) return

    val pulse = remember { Animatable(1f) }
    val isFirstPulseKey = remember { booleanArrayOf(true) }
    LaunchedEffect(pulseKey) {
        // The first key is the pill appearing, which has its own pop; only later changes pulse.
        if (isFirstPulseKey[0]) {
            isFirstPulseKey[0] = false
            return@LaunchedEffect
        }
        if (!visible) return@LaunchedEffect
        // Restarting mid-pulse (fast drag-selection) carries on from the current scale.
        pulse.animateTo(PULSE_SCALE, tween(PULSE_EXPAND_MILLIS, easing = OvershootEasing))
        pulse.animateTo(1f, overshootSpec())
    }

    Surface(
        modifier = modifier
            .pop { container.value }
            .graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
            },
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
                    Box(Modifier.pop { itemPop.value }) { item() }
                }
            }
        }
    }
}

private const val PULSE_SCALE = 1.06f
private const val PULSE_EXPAND_MILLIS = 120

/** One of the pill's actions: an icon button that subtly expands while pressed. */
@Composable
private fun PillButton(onClick: () -> Unit, icon: ImageVector, contentDescription: String) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        modifier = Modifier.pressExpand(interactionSource),
        interactionSource = interactionSource,
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

/** A pill action shown as a word rather than an icon (there's no standard RAW icon), bold. */
@Composable
private fun PillTextButton(onClick: () -> Unit, text: String, contentDescription: String) {
    val interactionSource = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .pressExpand(interactionSource)
            .semantics { this.contentDescription = contentDescription },
        interactionSource = interactionSource,
        colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

/**
 * Hands the photos' RAW files to an app that works with them (Lightroom, Snapseed, a RAW
 * viewer...). One RAW opens with a chooser of apps that can view it; if none can (or for several
 * RAWs), it's shared instead, which is how apps like Lightroom import ("Add to Lightroom").
 */
fun openRaws(context: Context, photos: List<SyncedPhoto>) {
    val raws = photos.mapNotNull { it.rawUri }
    if (raws.isEmpty()) return
    val resolver = context.contentResolver
    val single = raws.singleOrNull()
    val view = single?.let { uri ->
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, resolver.getType(uri) ?: "image/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val intent = if (view != null && view.resolveActivity(context.packageManager) != null) {
        view
    } else {
        Intent(if (raws.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            if (raws.size == 1) putExtra(Intent.EXTRA_STREAM, raws.first()) else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(raws))
            // A shared MIME type when they all agree (e.g. all NEF), else any image.
            type = raws.map { resolver.getType(it) }.distinct().singleOrNull() ?: "image/*"
            clipData = ClipData.newRawUri(null, raws.first()).also { clip -> raws.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    try {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.raw_chooser_title)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.sync_no_viewer_app), Toast.LENGTH_SHORT).show()
    }
}

/**
 * Opens a photo in an image editor (Google Photos, Snapseed, Lightroom...), via a chooser. Only
 * the app's own photos are offered for writing back ([writable]); granting write access to
 * another app's photo would need access the app doesn't have, so editors save a copy instead.
 */
fun editPhoto(context: Context, photo: SyncedPhoto, writable: Boolean) {
    val uri = photo.uri
    val intent = Intent(Intent.ACTION_EDIT)
        .setDataAndType(uri, context.contentResolver.getType(uri) ?: "image/jpeg")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .apply { if (writable) addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
    try {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.edit_chooser_title)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.sync_no_viewer_app), Toast.LENGTH_SHORT).show()
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
