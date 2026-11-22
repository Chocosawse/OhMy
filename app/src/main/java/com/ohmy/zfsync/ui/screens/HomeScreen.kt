package com.ohmy.zfsync.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ohmy.zfsync.R
import com.ohmy.zfsync.core.model.ConnectionState
import com.ohmy.zfsync.core.model.SavedCameraCredentials
import com.ohmy.zfsync.core.model.SyncUiState
import com.ohmy.zfsync.core.model.photosNewestFirst
import com.ohmy.zfsync.ui.components.ActionPill
import com.ohmy.zfsync.ui.components.ChevronDownIcon
import com.ohmy.zfsync.ui.components.EyeIcon
import com.ohmy.zfsync.ui.components.EyeOffIcon
import com.ohmy.zfsync.ui.components.GridLayoutIcon
import com.ohmy.zfsync.ui.components.HomePhotoGrid
import com.ohmy.zfsync.ui.components.HomePhotoList
import com.ohmy.zfsync.ui.components.ListLayoutIcon
import com.ohmy.zfsync.ui.components.PhotoTransitionState
import com.ohmy.zfsync.ui.components.SelectIcon
import com.ohmy.zfsync.ui.components.SettingsGearIcon
import com.ohmy.zfsync.ui.components.openRaws
import com.ohmy.zfsync.ui.components.rememberHomeSelection
import com.ohmy.zfsync.ui.components.sharePhotos
import com.ohmy.zfsync.ui.home.DayPart
import com.ohmy.zfsync.ui.home.HomeEntry
import com.ohmy.zfsync.ui.home.HomeLayout
import com.ohmy.zfsync.ui.home.HomeStatus
import com.ohmy.zfsync.ui.home.SectionTitle
import com.ohmy.zfsync.ui.home.dayTitle
import com.ohmy.zfsync.ui.home.homeEntries
import com.ohmy.zfsync.ui.home.homeStatus
import com.ohmy.zfsync.ui.home.kickerText
import com.ohmy.zfsync.ui.home.lastSyncText
import com.ohmy.zfsync.ui.home.needsSetup
import com.ohmy.zfsync.ui.home.rememberToday
import com.ohmy.zfsync.ui.home.syncProgress
import com.ohmy.zfsync.ui.theme.HomeTypography
import com.ohmy.zfsync.ui.theme.LocalHomeColors
import com.ohmy.zfsync.ui.theme.OvershootEasing
import com.ohmy.zfsync.ui.theme.ScreenHorizontalPadding
import com.ohmy.zfsync.ui.theme.animationsEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** Gap between the floating pill and the bottom inset (navigation bar or keyboard). */
private val PillBottomMargin = 24.dp

/** Leaves room at the bottom of the content so the floating pill never covers the last photos. */
private val PillClearance = 88.dp + PillBottomMargin

/** The status row and progress rule, pinned at the top once the title has scrolled away. */
private val PinnedHeight = 48.dp + 3.dp

/** How long after connecting the card stays open, so the change to "connected" is seen. */
private const val CARD_COLLAPSE_DELAY_MILLIS = 600L

private const val CARD_MOTION_MILLIS = 200
/** Switching layout: the old one fades out, then the new one fades in. */
private const val LAYOUT_FADE_OUT_MILLIS = 90
private const val LAYOUT_FADE_IN_MILLIS = 210

/** The glyph's inset in a 48dp touch target, for lining up an icon's edge with the content edge. */
private fun glyphInset(glyph: Float) = ((48f - glyph) / 2f).dp

/** Screens narrower than this stack the card's two fields. */
private const val SIDE_BY_SIDE_FIELDS_MIN_WIDTH_DP = 480

/**
 * The app's one main screen, connected or not. Top to bottom: today's date and the title (which
 * scroll away), the status row and progress rule (which then pin to the top), the connection card
 * when it's open, the library row, and the album in dated sections, as a list or a grid
 * ([layout]). Synced photos are on the phone, so they're browsable without a connection.
 */
@Composable
fun HomeScreen(
    state: SyncUiState,
    savedCredentials: SavedCameraCredentials?,
    layout: HomeLayout,
    onLayoutChange: (HomeLayout) -> Unit,
    onConnect: (ssid: String, password: String) -> Unit,
    onDisconnect: () -> Unit,
    onPhotoClick: (index: Int) -> Unit,
    onDeletePhotos: (List<Uri>) -> Unit,
    onOpenSettings: () -> Unit,
    onBrowseAlbums: () -> Unit,
    modifier: Modifier = Modifier,
    photoTransition: PhotoTransitionState? = null,
    /** Whether Settings is showing over this screen: the gear turns as it opens and closes. */
    settingsOpen: Boolean = false,
    today: LocalDate = rememberToday(),
    /** Previews only: open the card regardless of the status. */
    cardInitiallyExpanded: Boolean? = null,
) {
    val colors = LocalHomeColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    val animate = animationsEnabled()

    val status = homeStatus(state, hasSavedCamera = savedCredentials != null)
    var expanded by rememberSaveable { mutableStateOf(cardInitiallyExpanded ?: status.needsSetup) }
    // Opens on its own when there's no camera to sync with; closes a moment after connecting.
    LaunchedEffect(status.needsSetup) { if (status.needsSetup) expanded = true }
    val connected = status is HomeStatus.Connected
    var wasConnected by remember { mutableStateOf(connected) }
    LaunchedEffect(connected) {
        if (connected && !wasConnected) {
            delay(CARD_COLLAPSE_DELAY_MILLIS)
            expanded = false
        }
        wasConnected = connected
    }

    // Held here, not in the card, so typing survives the card scrolling out of composition.
    var ssid by rememberSaveable(savedCredentials) { mutableStateOf(savedCredentials?.ssid.orEmpty()) }
    var password by rememberSaveable(savedCredentials) { mutableStateOf(savedCredentials?.password.orEmpty()) }

    val selection = rememberHomeSelection()
    var confirmDelete by remember { mutableStateOf(false) }
    // A selected photo can vanish underneath us (deleted in another app, picked up on resume).
    LaunchedEffect(state.photos) { selection.retainAll(state.photos.mapTo(HashSet()) { it.uri }) }
    BackHandler(enabled = selection.active) { selection.clear() }

    val ordered = remember(state.photos) { state.photosNewestFirst() }
    val entries = remember(ordered, state.pending, today) { homeEntries(ordered, state.pending, today, LocalDateTime.now()) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    // The layout on screen: it follows [layout] once the photos have faded out (see below).
    var shownLayout by remember { mutableStateOf(layout) }
    val photosAlpha = remember { Animatable(1f) }
    // Where the status row starts within the header, so it pins exactly as it reaches the top.
    val statusTop = remember { mutableIntStateOf(Int.MAX_VALUE) }
    val pinned by remember {
        derivedStateOf {
            val (index, offset) = when (shownLayout) {
                HomeLayout.List -> listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                HomeLayout.Grid -> gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
            }
            index > 0 || offset >= statusTop.intValue
        }
    }

    suspend fun scrollTo(target: HomeLayout, index: Int, offset: Int) {
        when (target) {
            HomeLayout.List -> listState.scrollToItem(index, offset)
            HomeLayout.Grid -> gridState.scrollToItem(index, offset)
        }
    }

    val toggleCard: () -> Unit = {
        expanded = !expanded
        // Opened from the pinned row: bring the card into view.
        if (expanded && pinned) scope.launch { scrollTo(shownLayout, 0, 0) }
    }

    /**
     * Switching layout: the photos fade out, the other layout takes their place scrolled to the
     * same photo (or, with the header in view, to the same spot, so the header doesn't move),
     * and the photos fade back in. The header, status row and library row stay put throughout.
     */
    LaunchedEffect(layout) {
        if (layout == shownLayout) {
            // Switched back mid-fade.
            if (animate) photosAlpha.animateTo(1f, tween(LAYOUT_FADE_IN_MILLIS)) else photosAlpha.snapTo(1f)
            return@LaunchedEffect
        }
        val (index, offset, visible) = when (shownLayout) {
            HomeLayout.List -> Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                listState.layoutInfo.visibleItemsInfo.map { it.index },
            )
            HomeLayout.Grid -> Triple(
                gridState.firstVisibleItemIndex,
                gridState.firstVisibleItemScrollOffset,
                gridState.layoutInfo.visibleItemsInfo.map { it.index },
            )
        }
        val topPhoto = visible.firstOrNull { i ->
            val entry = entries.getOrNull(i)
            entry is HomeEntry.Photo || entry is HomeEntry.Pending
        }
        if (animate) photosAlpha.animateTo(0f, tween(LAYOUT_FADE_OUT_MILLIS)) else photosAlpha.snapTo(0f)
        // Set before the other layout is first measured, so its very first frame is already there.
        val (targetIndex, targetOffset) = if (index == 0 || topPhoto == null) index to offset else topPhoto to 0
        when (layout) {
            HomeLayout.List -> listState.requestScrollToItem(targetIndex, targetOffset)
            HomeLayout.Grid -> gridState.requestScrollToItem(targetIndex, targetOffset)
        }
        shownLayout = layout
        if (index != 0 && topPhoto != null) {
            // Just below the pinned status row, which would otherwise cover it (the header is
            // scrolled away, so this happens out of sight while the photos are faded out).
            withFrameNanos { }
            val pinnedPx = with(density) { PinnedHeight.roundToPx() }.toFloat()
            when (layout) {
                HomeLayout.List -> listState.scrollBy(-pinnedPx)
                HomeLayout.Grid -> gridState.scrollBy(-pinnedPx)
            }
        }
        if (animate) photosAlpha.animateTo(1f, tween(LAYOUT_FADE_IN_MILLIS)) else photosAlpha.snapTo(1f)
    }

    val progress = syncProgress(state)
    val header: @Composable () -> Unit = {
        HomeHeader(
            today = today,
            status = status,
            lastSyncMillis = state.lastSyncMillis,
            progress = progress,
            expanded = expanded,
            onToggleCard = toggleCard,
            pinned = { pinned },
            onStatusPlaced = { statusTop.intValue = it },
            onOpenSettings = onOpenSettings,
            settingsOpen = settingsOpen,
            card = {
                ConnectionCard(
                    status = status,
                    connectionState = state.connectionState,
                    ssid = ssid,
                    onSsidChange = { ssid = it },
                    password = password,
                    onPasswordChange = { password = it },
                    onConnect = { onConnect(ssid.trim(), password) },
                    onDisconnect = onDisconnect,
                    syncError = state.lastError,
                )
            },
            layout = layout,
            onSwitchLayout = { onLayoutChange(layout.toggled) },
            onSelect = selection::start,
            onBrowseAlbums = onBrowseAlbums,
        )
    }
    val sectionTitle: @Composable (SectionTitle) -> String = { title ->
        when (title) {
            is SectionTitle.Today -> stringResource(title.part.label)
            is SectionTitle.Day -> dayTitle(title.date, today)
        }
    }
    val bottomInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val bottomPadding = bottomInset + if (selection.active) PillClearance else 0.dp

    // Keeps the pill's count steady while it animates out after the selection is cleared,
    // instead of flashing "0 selected".
    val lastSelectedCount = remember { intArrayOf(0) }
    if (selection.active) lastSelectedCount[0] = selection.selected.size
    val pillCount = lastSelectedCount[0]
    val selectedPhotos = ordered.filter { it.uri in selection.selected }

    Box(modifier = modifier.fillMaxSize().background(colors.background)) {
        // Only the photos fade as the layout switches (see above); the rest stays put.
        val contentAlpha = { photosAlpha.value }
        Box(Modifier.fillMaxSize()) {
            when (shownLayout) {
                HomeLayout.List -> HomePhotoList(
                    entries = entries,
                    listState = listState,
                    selection = selection,
                    onPhotoClick = onPhotoClick,
                    header = header,
                    sectionTitle = sectionTitle,
                    bottomPadding = bottomPadding,
                    photoTransition = photoTransition,
                    contentAlpha = contentAlpha,
                )
                HomeLayout.Grid -> HomePhotoGrid(
                    entries = entries,
                    gridState = gridState,
                    selection = selection,
                    onPhotoClick = onPhotoClick,
                    header = header,
                    sectionTitle = sectionTitle,
                    bottomPadding = bottomPadding,
                    photoTransition = photoTransition,
                    contentAlpha = contentAlpha,
                )
            }
        }
        // On the page's own color, no shadow: the photos simply pass under it.
        if (pinned) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(horizontal = ScreenHorizontalPadding),
            ) {
                StatusRow(status, state.lastSyncMillis, today, expanded, toggleCard)
                ProgressRule(progress)
            }
        }

        ActionPill(
            visible = selection.active,
            pulseKey = selection.selected.size,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(bottom = PillBottomMargin),
            label = pluralStringResource(R.plurals.select_count, pillCount, pillCount),
            onShare = { if (selectedPhotos.isNotEmpty()) sharePhotos(context, selectedPhotos) },
            onDelete = { if (selectedPhotos.isNotEmpty()) confirmDelete = true },
            // Only when every selected photo has a RAW: "open these RAWs" shouldn't skip some.
            onRaw = selectedPhotos
                .takeIf { it.isNotEmpty() && it.all { photo -> photo.rawUri != null } }
                ?.let { photos -> { openRaws(context, photos) } },
            onCancel = selection::clear,
        )
    }

    if (confirmDelete) {
        val count = selection.selected.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(pluralStringResource(R.plurals.select_delete_dialog_title, count, count)) },
            text = { Text(pluralStringResource(R.plurals.select_delete_dialog_text, count)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePhotos(selection.selected.toList())
                    selection.clear()
                    confirmDelete = false
                }) { Text(stringResource(R.string.viewer_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.viewer_delete_cancel)) }
            },
        )
    }
}

private val DayPart.label: Int
    get() = when (this) {
        DayPart.LateNight -> R.string.section_late_night
        DayPart.Morning -> R.string.section_morning
        DayPart.Afternoon -> R.string.section_afternoon
        DayPart.Evening -> R.string.section_evening
        DayPart.Night -> R.string.section_night
    }

/** From the title's baseline to the rule under it. */
private val TitleToRule = 20.dp

/** From the bottom of the gear's 48dp touch target up to the bottom of its drawn teeth (stroke included). */
private val GearGlyphBottomInset = 14.dp

/** How long the gear's spin takes, each way. */
private const val GEAR_SPIN_MILLIS = 700

/**
 * The settings gear. It gives a full turn as Settings opens, and turns back the other way as
 * Settings closes and the home screen is back.
 */
@Composable
private fun SettingsButton(settingsOpen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val animate = animationsEnabled()
    // Starts where it should be already, e.g. with Settings open after rotating the phone.
    val rotation = remember { Animatable(if (settingsOpen) 360f else 0f) }
    LaunchedEffect(settingsOpen) {
        val target = if (settingsOpen) 360f else 0f
        // From wherever a spin still running has got to.
        if (animate) rotation.animateTo(target, tween(GEAR_SPIN_MILLIS, easing = OvershootEasing)) else rotation.snapTo(target)
    }
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            SettingsGearIcon,
            contentDescription = stringResource(R.string.settings_title),
            tint = LocalHomeColors.current.ink,
            modifier = Modifier.graphicsLayer { rotationZ = rotation.value },
        )
    }
}

/** Everything above the album, as the scrolling content's first item. */
@Composable
private fun HomeHeader(
    today: LocalDate,
    status: HomeStatus,
    lastSyncMillis: Long?,
    progress: Float?,
    expanded: Boolean,
    onToggleCard: () -> Unit,
    pinned: () -> Boolean,
    onStatusPlaced: (top: Int) -> Unit,
    onOpenSettings: () -> Unit,
    settingsOpen: Boolean,
    card: @Composable () -> Unit,
    layout: HomeLayout,
    onSwitchLayout: () -> Unit,
    onSelect: () -> Unit,
    onBrowseAlbums: () -> Unit,
) {
    val colors = LocalHomeColors.current
    val cardSpec = if (animationsEnabled()) CARD_MOTION_MILLIS else 0
    Column(modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(kickerText(today), style = HomeTypography.kicker, color = colors.muted)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.home_title),
                    style = HomeTypography.title,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // 16dp from the letters (their baseline: "Nikon Zf" has no descenders) to the
                    // rule, rather than from the bottom of the line's box.
                    modifier = Modifier.paddingFromBaseline(bottom = TitleToRule),
                )
            }
            SettingsButton(
                settingsOpen = settingsOpen,
                onClick = onOpenSettings,
                // The glyph's right edge on the content edge and its bottom on the title's
                // baseline; the touch target runs past both.
                modifier = Modifier
                    .offset(x = glyphInset(22f))
                    .padding(bottom = TitleToRule - GearGlyphBottomInset),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.ink))
        Column(
            modifier = Modifier
                .onPlaced { onStatusPlaced(it.positionInParent().y.toInt()) }
                // While pinned, its copy at the top stands in for it.
                .graphicsLayer { alpha = if (pinned()) 0f else 1f },
        ) {
            StatusRow(status, lastSyncMillis, today, expanded, onToggleCard)
            ProgressRule(progress)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(cardSpec)) + fadeIn(tween(cardSpec)),
            exit = shrinkVertically(tween(cardSpec)) + fadeOut(tween(cardSpec)),
        ) {
            Box(Modifier.padding(top = 16.dp)) { card() }
        }
        LibraryRow(
            layout = layout,
            onSwitchLayout = onSwitchLayout,
            onSelect = onSelect,
            onBrowseAlbums = onBrowseAlbums,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * The connection at a glance: a dot (or ring), what's going on, the camera's network, and on the
 * right how syncing is going or when it last did. Tapping opens or closes the connection card.
 */
@Composable
private fun StatusRow(status: HomeStatus, lastSyncMillis: Long?, today: LocalDate, expanded: Boolean, onToggle: () -> Unit) {
    val colors = LocalHomeColors.current
    val label = when (status) {
        is HomeStatus.Connected -> stringResource(R.string.status_connected)
        is HomeStatus.Connecting -> stringResource(R.string.status_connecting)
        HomeStatus.CameraNotFound -> stringResource(R.string.status_camera_not_found)
        HomeStatus.NotSetUp -> stringResource(R.string.status_not_set_up)
    }
    val ssid = when (status) {
        is HomeStatus.Connected -> status.ssid
        is HomeStatus.Connecting -> status.ssid
        else -> null
    }
    val lastSync = lastSyncMillis?.let { stringResource(R.string.status_last_sync, lastSyncText(it, today)) }
    // The right-hand text, and whether it's in ink (live) or muted (history).
    val (summary, summaryLive) = when (status) {
        is HomeStatus.Connected -> when {
            !status.syncing -> stringResource(R.string.status_up_to_date)
            status.left > 0 -> stringResource(R.string.status_syncing_left, status.left)
            else -> stringResource(R.string.status_syncing)
        } to true
        is HomeStatus.Connecting, HomeStatus.CameraNotFound -> lastSync to false
        HomeStatus.NotSetUp -> null to false
    }
    val description = listOfNotNull(label, ssid, summary).joinToString(", ")
    val stateText = stringResource(if (expanded) R.string.status_expanded else R.string.status_collapsed)
    val actionLabel = stringResource(if (expanded) R.string.status_hide_card else R.string.status_show_card)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clearAndSetSemantics {
                contentDescription = description
                stateDescription = stateText
                role = Role.Button
                onClick(label = actionLabel) { onToggle(); true }
            }
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusIndicator(filled = status is HomeStatus.Connected, pulsing = status is HomeStatus.Connecting)
            Text(label, style = HomeTypography.status, fontWeight = FontWeight.Medium, color = colors.ink, maxLines = 1)
            if (ssid != null) {
                Text(ssid, style = HomeTypography.status, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(
            modifier = Modifier.padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (summary != null) {
                Text(summary, style = HomeTypography.status, color = if (summaryLive) colors.ink else colors.muted, maxLines = 1)
            }
            Icon(
                ChevronDownIcon,
                contentDescription = null,
                tint = colors.ink,
                modifier = Modifier.size(16.dp).rotate(if (expanded) 180f else 0f),
            )
        }
    }
}

/** Connected: a dot in the connected color. Otherwise a ring, which pulses while connecting. */
@Composable
private fun StatusIndicator(filled: Boolean, pulsing: Boolean) {
    val colors = LocalHomeColors.current
    val alpha = if (pulsing && animationsEnabled()) {
        val pulse = rememberInfiniteTransition(label = "connectingPulse")
        pulse.animateFloat(
            initialValue = 1f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
            label = "connectingPulseAlpha",
        )
    } else {
        null
    }
    Canvas(modifier = Modifier.size(8.dp).graphicsLayer { this.alpha = alpha?.value ?: 1f }) {
        if (filled) {
            drawCircle(colors.connected)
        } else {
            val stroke = 1.5.dp.toPx()
            drawCircle(colors.ink, radius = size.minDimension / 2 - stroke / 2, style = Stroke(stroke))
        }
    }
}

/** The current batch's progress as a 3dp rule: accent fill from the left, or a sliding segment while its size is unknown. */
@Composable
private fun ProgressRule(progress: Float?) {
    val colors = LocalHomeColors.current
    val animate = animationsEnabled()
    // Not a control: nothing to focus or read; the status row says how syncing is going.
    val base = Modifier.fillMaxWidth().height(3.dp).background(colors.track).clearAndSetSemantics { }
    if (progress == null) {
        val position = if (animate) {
            val transition = rememberInfiniteTransition(label = "progressSlide")
            transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "progressSlidePosition")
        } else {
            null
        }
        Box(
            modifier = base.drawBehind {
                val segment = size.width * 0.25f
                // From just off the left edge to just off the right, clipped to the rule; still, at the start.
                val left = position?.let { -segment + (size.width + segment) * it.value } ?: 0f
                val start = left.coerceAtLeast(0f)
                val end = (left + segment).coerceAtMost(size.width)
                if (end > start) drawRect(colors.accent, topLeft = Offset(start, 0f), size = Size(end - start, size.height))
            },
        )
    } else {
        val fraction by animateFloatAsState(
            targetValue = progress,
            animationSpec = tween(if (animate) 300 else 0, easing = EaseOut),
            label = "progressFill",
        )
        Box(modifier = base.drawBehind { drawRect(colors.accent, size = Size(size.width * fraction, size.height)) })
    }
}

/** Setting up the camera's Wi‑Fi and connecting; once connected, the connection and a way to end it. */
@Composable
private fun ConnectionCard(
    status: HomeStatus,
    connectionState: ConnectionState,
    ssid: String,
    onSsidChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    syncError: String?,
) {
    val colors = LocalHomeColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card, RoundedCornerShape(20.dp))
            .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (status is HomeStatus.Connected) {
            ConnectedBlock(status.ssid, (connectionState as? ConnectionState.Connected)?.cameraIp, syncError, onDisconnect)
        } else {
            SetupSteps()
            val isConnecting = status is HomeStatus.Connecting
            CredentialFields(ssid, onSsidChange, password, onPasswordChange, enabled = !isConnecting)
            Button(
                onClick = onConnect,
                enabled = !isConnecting && ssid.isNotBlank() && password.isNotBlank(),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentFill,
                    contentColor = colors.onAccent,
                    // Connecting: still the button, just busy. Missing details: plainly unavailable.
                    disabledContainerColor = colors.accentFill.copy(alpha = if (isConnecting) 0.7f else 0.38f),
                    disabledContentColor = colors.onAccent.copy(alpha = if (isConnecting) 1f else 0.7f),
                ),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                val label = when {
                    isConnecting -> stringResource(R.string.connect_button_connecting)
                    ssid.isBlank() -> stringResource(R.string.connect_button)
                    else -> stringResource(R.string.connect_button_to, ssid.trim())
                }
                Text(label, style = HomeTypography.button, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (connectionState is ConnectionState.Error) {
                Text(connectionState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Connected: which camera, that new shots come in on their own, and Disconnect. */
@Composable
private fun ConnectedBlock(ssid: String?, cameraIp: String?, syncError: String?, onDisconnect: () -> Unit) {
    val colors = LocalHomeColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (ssid != null) stringResource(R.string.card_connected_title, ssid) else stringResource(R.string.card_connected_title_plain),
            style = HomeTypography.cardTitle,
            color = colors.ink,
        )
        Text(
            if (cameraIp != null) stringResource(R.string.card_connected_text_ip, cameraIp) else stringResource(R.string.card_connected_text),
            style = HomeTypography.body,
            color = colors.ink,
        )
        if (syncError != null) {
            Text(syncError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
    // Outlined in ink: available, but not the screen's main action.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .border(1.dp, colors.ink, RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onDisconnect),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.sync_disconnect_button), style = HomeTypography.button, color = colors.ink)
    }
}

/** How to turn on the camera's Wi‑Fi, as two numbered steps. */
@Composable
private fun SetupSteps() {
    val colors = LocalHomeColors.current
    Column {
        Text(stringResource(R.string.card_setup_title), style = HomeTypography.cardTitle, color = colors.ink)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val menu = stringResource(R.string.card_step_1_menu)
            val step1 = stringResource(R.string.card_step_1, menu)
            Step(
                number = "01",
                text = remember(step1, menu) {
                    buildAnnotatedString {
                        val at = step1.indexOf(menu)
                        if (at < 0) {
                            append(step1)
                        } else {
                            append(step1.substring(0, at))
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(menu) }
                            append(step1.substring(at + menu.length))
                        }
                    }
                },
            )
            Step(number = "02", text = buildAnnotatedString { append(stringResource(R.string.card_step_2)) })
        }
    }
}

private val StepNumberStyle = TextStyle(
    fontFamily = HomeTypography.status.fontFamily,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

@Composable
private fun Step(number: String, text: AnnotatedString) {
    val colors = LocalHomeColors.current
    Row {
        // Nudged down to sit on the text's first line.
        Text(number, style = StepNumberStyle, color = colors.muted, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.size(14.dp))
        Text(text, style = HomeTypography.body, color = colors.ink)
    }
}

/** The network name and password, side by side, or stacked on a narrow screen. */
@Composable
private fun CredentialFields(
    ssid: String,
    onSsidChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    enabled: Boolean,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val ssidField: @Composable (Modifier) -> Unit = { modifier ->
        UnderlinedField(
            label = stringResource(R.string.connect_ssid_label),
            value = ssid,
            onValueChange = onSsidChange,
            enabled = enabled,
            modifier = modifier,
        )
    }
    val passwordField: @Composable (Modifier) -> Unit = { modifier ->
        UnderlinedField(
            label = stringResource(R.string.connect_password_label),
            value = password,
            onValueChange = onPasswordChange,
            enabled = enabled,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = modifier,
            trailing = {
                // The glyph's right edge on the field's; the touch target runs past it.
                IconButton(
                    onClick = { showPassword = !showPassword },
                    modifier = Modifier.requiredSize(48.dp).offset(x = glyphInset(20f)),
                ) {
                    Icon(
                        if (showPassword) EyeOffIcon else EyeIcon,
                        contentDescription = stringResource(if (showPassword) R.string.connect_hide_password else R.string.connect_show_password),
                        tint = LocalHomeColors.current.ink,
                    )
                }
            },
        )
    }
    if (LocalConfiguration.current.screenWidthDp < SIDE_BY_SIDE_FIELDS_MIN_WIDTH_DP) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ssidField(Modifier.fillMaxWidth())
            passwordField(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ssidField(Modifier.weight(1f))
            passwordField(Modifier.weight(1f))
        }
    }
}

/**
 * A label over a bare 40dp input with a 1dp ink rule under it: no fill or padding. Focus
 * thickens the rule, as the app's fields always have.
 */
@Composable
private fun UnderlinedField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalHomeColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val ink = if (enabled) colors.ink else colors.ink.copy(alpha = 0.38f)
    Column(modifier = modifier) {
        Text(label, style = HomeTypography.fieldLabel, color = colors.muted)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = HomeTypography.fieldValue.copy(color = ink),
            cursorBrush = SolidColor(colors.ink),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth().height(40.dp),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val rule = (if (focused) 2.dp else 1.dp).toPx()
                            drawRect(ink, topLeft = Offset(0f, size.height - rule), size = Size(size.width, rule))
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) { inner() }
                    trailing?.invoke()
                }
            },
        )
    }
}

/** Photos and Albums, then Select and the layout toggle. */
@Composable
private fun LibraryRow(
    layout: HomeLayout,
    onSwitchLayout: () -> Unit,
    onSelect: () -> Unit,
    onBrowseAlbums: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalHomeColors.current
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            LibraryTab(stringResource(R.string.photos_section_all), active = true, onClick = {})
            LibraryTab(stringResource(R.string.albums_browse), active = false, onClick = onBrowseAlbums)
        }
        Spacer(Modifier.weight(1f))
        // The last glyph's right edge on the content edge.
        Row(
            modifier = Modifier.offset(x = glyphInset(22f)),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onSelect) {
                Icon(SelectIcon, contentDescription = stringResource(R.string.home_select_photos), tint = colors.ink)
            }
            IconButton(onClick = onSwitchLayout) {
                when (layout) {
                    HomeLayout.List -> Icon(GridLayoutIcon, contentDescription = stringResource(R.string.home_show_grid), tint = colors.ink)
                    HomeLayout.Grid -> Icon(ListLayoutIcon, contentDescription = stringResource(R.string.home_show_list), tint = colors.ink)
                }
            }
        }
    }
}

/** A tab: its text alone, the active one in ink over a 2dp accent rule as wide as the text. */
@Composable
private fun LibraryTab(text: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalHomeColors.current
    Box(
        modifier = Modifier
            .height(48.dp)
            .selectable(selected = active, role = Role.Tab, onClick = onClick)
            .drawBehind {
                if (active) {
                    val rule = 2.dp.toPx()
                    drawRect(colors.accent, topLeft = Offset(0f, size.height - rule), size = Size(size.width, rule))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = HomeTypography.tab, color = if (active) colors.ink else colors.muted)
    }
}
