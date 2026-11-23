package com.gallery.sync.ui.backup

import com.gallery.sync.ui.common.FileOpenerHost
import com.gallery.sync.ui.common.OpenFileViewModel
import com.gallery.sync.ui.common.longPressToOpen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.gallery.sync.domain.backup.BackupLocation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gallery.sync.R
import com.gallery.sync.data.local.entity.AlbumMode
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import com.gallery.sync.domain.backup.AlbumFileSort
import com.gallery.sync.domain.backup.ArchiveAge
import com.gallery.sync.domain.backup.CameraOptimizeAge
import com.gallery.sync.ui.common.ArchiveAgePicker
import com.gallery.sync.ui.common.label
import com.gallery.sync.domain.backup.CameraOptimizeChoice
import com.gallery.sync.domain.backup.CameraSelection
import com.gallery.sync.domain.backup.FilePin
import com.gallery.sync.domain.backup.FileSort
import com.gallery.sync.domain.backup.RetryFailed
import com.gallery.sync.ui.common.detailsShown
import com.gallery.sync.ui.common.HeaderKey
import com.gallery.sync.ui.common.HeaderDetailsToggle
import com.gallery.sync.ui.common.HeaderDetailsSection
import com.gallery.sync.ui.common.CappedTextSize
import com.gallery.sync.ui.common.HeroOutlinedButton
import com.gallery.sync.ui.common.SignalIcons
import com.gallery.sync.ui.common.SwipeChoiceBox
import com.gallery.sync.ui.common.formatBytes
import com.gallery.sync.ui.common.labelRes
import com.gallery.sync.ui.help.HelpButton
import com.gallery.sync.ui.help.HelpTopic
import com.gallery.sync.ui.help.WithHelp
import com.gallery.sync.ui.theme.LocalGallerySyncColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A file's date on its line: day, short month, year. Read in the phone's language. */
private val FileDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/** Two columns of file cards from here up, as on the Restore tab: unfolded, more rows rather than wider ones. */
private val WideBreakpoint = 600.dp

/**
 * What the camera folder's header needs from its owner. Null for every other album, which is what keeps the
 * control off them.
 *
 * Ian, 27 Sept 2026: the camera folder's optimize is a manual operation. Every file is listed; the header's age,
 * Photos and Videos select files, starting at the Camera defaults in Settings, a swipe selects or deselects one,
 * and the header acts like the Albums tab's, with Sync now and Rescan.
 */
data class CameraOptimizeControls(
    /** Where the header's three choices start: the Camera defaults in Settings. */
    val defaults: CameraOptimizeChoice,
    /** What optimizing these files is expected to give back. */
    val estimate: (List<BackupEntryEntity>) -> Long,
    /** An optimize pass is queued or running, so Sync now and the swipes wait. */
    val running: Boolean,
    /** A backup run is going, from this Sync now or any other. */
    val uploading: Boolean,
    /** Rescan's cloud check is in progress. */
    val checkingCloud: Boolean,
    val onRescan: () -> Unit,
    /** Yes to *make these your default settings*, on leaving with choices that differ from [defaults]. */
    val onSaveDefaults: (CameraOptimizeChoice) -> Unit,
    /** The header's choices while they differ from [defaults], or null: lets the bottom bar ask before leaving. */
    val onUnsavedChanged: (CameraOptimizeChoice?) -> Unit,
    /** *Sync now*: upload what is waiting, then optimize exactly the selected files. */
    val onSyncNow: (selectedIds: Set<String>) -> Unit
)

/**
 * An album's files.
 *
 * Laid out like the Restore tab's folder view (Ian, 18 Sept 2026): the same green card at the top the
 * other tabs open with, and one rounded card per file below it, in the same style and the same order
 * of lines. It is still a plain list of names and marks. No thumbnails, no preview: looking at photos
 * is the gallery app's job.
 */
@Composable
fun AlbumDetailScreen(
    albumName: String,
    mode: AlbumMode,
    entries: List<BackupEntryEntity>,
    onBack: () -> Unit,
    /** The Cloud this album's new files go to, and the backup folder chosen there (null for Google Photos). */
    backsUpTo: BackupLocation? = null,
    backupFolder: String? = null,
    /** The tick box: keep one file at full size, or let it follow its album again. See `FilePin`. */
    onSetPinned: (BackupEntryEntity, Boolean) -> Unit,
    /** *Retry failed*: put the failed files back in the queue and start a backup. See `RetryFailed`. */
    onRetryFailed: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Files sent that a Cloud no longer holds, as the last check found (row ids). Selected and resent like the Restore
     * tab's files (Ian, 22 Nov 2026). See `RequeueMissingFromCloud`.
     */
    missingFromCloud: Set<String> = emptySet(),
    onResend: (Set<String>) -> Unit = {},
    /** Files whose upload keeps being interrupted without getting anywhere: said on their card. See `UploadStalls`. */
    stalledUploads: Set<String> = emptySet(),
    /** Only the Camera album passes this. See [CameraOptimizeControls]. */
    camera: CameraOptimizeControls? = null,
    opener: OpenFileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    FileOpenerHost(opener)
    var sort by rememberSaveable { mutableStateOf(FileSort.NAME) }

    // The camera folder's three choices. They start at the Camera defaults in Settings and change here for this
    // visit only; leaving with different ones asks whether to make them the defaults (Ian, 27 Sept 2026).
    val defaults = camera?.defaults ?: CameraOptimizeChoice.DEFAULT
    var ageName by rememberSaveable(albumName) { mutableStateOf(defaults.age.storedName) }
    var photos by rememberSaveable(albumName) { mutableStateOf(defaults.photos) }
    var videos by rememberSaveable(albumName) { mutableStateOf(defaults.videos) }
    val choice = CameraOptimizeChoice(
        ArchiveAge.parse(ageName) ?: defaults.age, photos, videos
    )

    // What Sync now will optimize, highlighted as on the Restore tab. Set by the header's choices, then changed a
    // file at a time by swiping; a changed choice selects afresh. For this visit only (Ian, 27 Sept 2026).
    var selection by rememberSaveable(albumName) { mutableStateOf(ArrayList<String>()) }
    var selectionMade by rememberSaveable(albumName) { mutableStateOf(false) }
    fun selectByChoice(next: CameraOptimizeChoice) {
        val before = next.age.thresholdEpochSeconds(Instant.now())
        selection = ArrayList(CameraSelection.byChoice(entries, next, before))
    }
    // The list arrives after the screen opens, so the first selection waits for it.
    LaunchedEffect(camera != null, entries.isNotEmpty()) {
        if (camera != null && !selectionMade && entries.isNotEmpty()) {
            selectByChoice(choice)
            selectionMade = true
        }
    }
    val selected = remember(selection) { selection.toHashSet() }

    // Which of the files gone from the Cloud to send again. Nothing is selected to start with (Ian, 22 Nov 2026: they
    // may have been deleted there on purpose); right selects, left deselects, as on the Restore tab.
    var resendSelection by rememberSaveable(albumName) { mutableStateOf(ArrayList<String>()) }
    val resendable = if (mode.uploads) missingFromCloud else emptySet()
    val toResend = remember(resendSelection, resendable) { resendSelection.filter { it in resendable }.toSet() }
    val toOptimize = remember(entries, selected) { CameraSelection.toOptimize(entries, selected) }

    // Every file, in the camera folder as everywhere else. Nothing on the header hides one.
    val shown = remember(entries, sort) { AlbumFileSort.sorted(entries, sort) }

    // Leaving the camera folder with choices that are not the defaults asks once whether to keep them
    // (Ian, 27 Sept 2026). Yes writes them to Settings; No leaves Settings alone; either way the screen closes.
    var askDefaults by remember { mutableStateOf(false) }
    // Told to the view model as it changes, so that leaving by the bottom bar asks too, not only the return
    // arrow and Back (Ian, 27 Sept 2026). The question itself is then asked by the bar's owner.
    LaunchedEffect(choice, camera?.defaults) {
        camera?.onUnsavedChanged(choice.takeIf { it != camera.defaults })
    }
    // However the screen closes, nothing is left marked unsaved for the bar to ask about later. The bar's own
    // question is answered before its tab changes, so this runs after the answer, never instead of it.
    val latestCamera by rememberUpdatedState(camera)
    DisposableEffect(Unit) { onDispose { latestCamera?.onUnsavedChanged(null) } }
    val leave = {
        if (camera != null && choice != camera.defaults) askDefaults = true else onBack()
    }
    BackHandler(onBack = leave)

    if (askDefaults && camera != null) {
        AlertDialog(
            onDismissRequest = { askDefaults = false },
            text = { Text(stringResource(R.string.camera_defaults_question)) },
            confirmButton = {
                TextButton(onClick = {
                    askDefaults = false
                    camera.onSaveDefaults(choice)
                    onBack()
                }) { Text(stringResource(R.string.camera_defaults_yes)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    askDefaults = false
                    camera.onUnsavedChanged(null)
                    onBack()
                }) { Text(stringResource(R.string.camera_defaults_no)) }
            }
        )
    }

    // Which files get a tick box: the ones Restore has put back, and only those (Ian, 18 Sept 2026 —
    // the box exists to undo Restore's pin, so a file Restore never touched has nothing to undo).
    //
    // Worked out once when the list opens rather than from what is ticked now. Restore's pin is the
    // only thing that sets the flag, so "ticked when the list opened" is "restored"; and a file the
    // user unticks keeps its box until they leave, so an accidental untick can be put back. Once the
    // list is closed an unticked file has no box: nothing else records that it was ever restored.
    val restoredIds = remember(albumName) {
        entries.filter { FilePin.isPinned(it.modeOverride) }.mapTo(HashSet()) { it.id }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            DetailHeader(
                albumName = albumName,
                mode = mode,
                entries = entries,
                sort = sort,
                onSort = { sort = it },
                showKeepColumn = restoredIds.isNotEmpty() && camera == null,
                camera = camera?.let {
                    CameraHeader(
                        controls = it,
                        choice = choice,
                        toOptimize = toOptimize,
                        // Why nothing is selected, when the choices are on but the age leaves every file out: said
                        // plainly, or a ticked Photos over a grey list reads as a toggle that did nothing.
                        tooYoung = !choice.nothingChosen && choice.age != CameraOptimizeAge.All &&
                            CameraSelection.byChoice(entries, choice, Long.MAX_VALUE).isNotEmpty() &&
                            CameraSelection.byChoice(entries, choice, choice.age.thresholdEpochSeconds(Instant.now())).isEmpty(),
                        hasPending = entries.any { entry -> entry.state == BackupState.PENDING },
                        onAge = { picked ->
                            ageName = picked.storedName
                            selectByChoice(choice.copy(age = picked))
                        },
                        onPhotos = { on ->
                            photos = on
                            selectByChoice(choice.copy(photos = on))
                        },
                        onVideos = { on ->
                            videos = on
                            selectByChoice(choice.copy(videos = on))
                        },
                        onSyncNow = { it.onSyncNow(toOptimize.mapTo(HashSet()) { entry -> entry.id }) }
                    )
                },
                onRetryFailed = onRetryFailed,
                resend = ResendHeader(
                    missing = missingFromCloud.size,
                    canResend = mode.uploads,
                    selected = toResend.size,
                    onSelectAll = { resendSelection = ArrayList(resendable) },
                    onClear = { resendSelection = ArrayList() },
                    onResend = {
                        onResend(toResend)
                        resendSelection = ArrayList()
                    }
                ),
                onBack = leave,
                backsUpTo = backsUpTo,
                backupFolder = backupFolder
            )
        }

        HorizontalDivider()

        if (entries.isEmpty()) {
            Text(
                text = "No files tracked yet",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val columns = if (maxWidth >= WideBreakpoint) 2 else 1
                val half = if (shown.isEmpty()) 0 else (shown.size + columns - 1) / columns

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(half) { index ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (column in 0 until columns) {
                                val position = index + column * half
                                Box(modifier = Modifier.weight(1f)) {
                                    shown.getOrNull(position)?.let { entry ->
                                        if (camera != null) {
                                            // Right selects, left deselects, as in every list that selects. A file
                                            // that cannot be optimized is listed greyed and takes no swipe.
                                            val selectable = CameraSelection.isSelectable(entry)
                                            val isSelected = selectable && entry.id in selected
                                            val toggle = {
                                                selection = ArrayList(
                                                    if (isSelected) selection - entry.id else selection + entry.id
                                                )
                                            }
                                            SwipeChoiceBox(
                                                enabled = !camera.running && selectable,
                                                stateKey = isSelected,
                                                onSwipeRight = { if (!isSelected) toggle() },
                                                onSwipeLeft = { if (isSelected) toggle() },
                                                accessibilityLabel = stringResource(
                                                    if (isSelected) R.string.camera_action_keep else R.string.camera_action_optimize
                                                ),
                                                onAccessibilityAction = toggle
                                            ) { drawn ->
                                                FileCard(
                                                    entry = entry,
                                                    context = context,
                                                    showKeepBox = false,
                                                    onSetPinned = {},
                                                    modifier = drawn.longPressToOpen { opener.open(entry) },
                                                    cameraSelected = isSelected
                                                )
                                            }
                                        } else if (entry.id in resendable) {
                                            // Gone from the Cloud since it was sent: greyed until selected, highlighted
                                            // once selected, as on the Restore tab (Ian, 22 Nov 2026).
                                            val isSelected = entry.id in toResend
                                            val toggle = {
                                                resendSelection = ArrayList(
                                                    if (isSelected) resendSelection - entry.id else resendSelection + entry.id
                                                )
                                            }
                                            SwipeChoiceBox(
                                                enabled = true,
                                                stateKey = isSelected,
                                                onSwipeRight = { if (!isSelected) toggle() },
                                                onSwipeLeft = { if (isSelected) toggle() },
                                                accessibilityLabel = stringResource(
                                                    if (isSelected) R.string.select_action_deselect else R.string.select_action_select
                                                ),
                                                onAccessibilityAction = toggle
                                            ) { drawn ->
                                                FileCard(
                                                    entry = entry,
                                                    context = context,
                                                    showKeepBox = false,
                                                    onSetPinned = {},
                                                    modifier = drawn.longPressToOpen { opener.open(entry) },
                                                    cameraSelected = isSelected,
                                                    goneFromCloud = true
                                                )
                                            }
                                        } else {
                                            FileCard(
                                                entry = entry,
                                                context = context,
                                                showKeepBox = entry.id in restoredIds,
                                                onSetPinned = { pinned -> onSetPinned(entry, pinned) },
                                                modifier = Modifier.longPressToOpen { opener.open(entry) },
                                                goneFromCloud = entry.id in missingFromCloud,
                                                stalled = entry.id in stalledUploads
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The green card: the way back, the folder's name, its mode and counts, and the two controls.
 *
 * The same surface, shape and colours as the card every other tab opens with, so this screen reads as
 * part of the app and not as a page bolted on. Drawn here rather than through `HeroCard` because that
 * card splits itself into two columns when the screen is wide, and this content is one column.
 */
@Composable
private fun DetailHeader(
    albumName: String,
    mode: AlbumMode,
    entries: List<BackupEntryEntity>,
    sort: FileSort,
    onSort: (FileSort) -> Unit,
    showKeepColumn: Boolean,
    camera: CameraHeader?,
    onRetryFailed: () -> Unit,
    resend: ResendHeader,
    onBack: () -> Unit,
    backsUpTo: BackupLocation? = null,
    backupFolder: String? = null
) {
    val signal = LocalGallerySyncColors.current
    var sortMenuOpen by remember { mutableStateOf(false) }
    val detailsOn = detailsShown(HeaderKey.ALBUM_PAGE)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = signal.heroContainer,
        contentColor = signal.onHero
    ) {
        CappedTextSize {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The app's own return glyph, in the card's ink: it was the theme's green, which is
                    // the card's own colour here and would have vanished.
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = SignalIcons.Back,
                            contentDescription = stringResource(R.string.retrieve_back),
                            modifier = Modifier.size(32.dp),
                            tint = LocalContentColor.current
                        )
                    }
                    // The folder's name, bold. Ian, 18 Sept 2026.
                    Text(
                        text = albumName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    // In the camera folder, how many files Sync now will optimize, large, as Restore and Archive show
                    // their number (Ian, 27 Sept 2026). It moves as the choices change and files are swiped.
                    // Set a little lower and to the right of centre (Ian, 28 Sept 2026).
                    if (camera != null) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                text = camera.toOptimize.size.toString(),
                                style = MaterialTheme.typography.displayMedium,
                                modifier = Modifier.padding(start = 24.dp, top = 8.dp)
                            )
                        }
                    }
                    HelpButton(HelpTopic.ALBUM_DETAIL)
                }

                Column(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.album_detail_mode_files,
                            mode.label(),
                            pluralStringResource(R.plurals.file_count, entries.size, entries.size)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // Where this album's files go: the Cloud and the folder in it (Ian, 12 Oct 2026). New files follow
                    // the folder's pairing and the folder chosen in that Cloud; files already sent elsewhere are named too.
                    // Where the files go folds away with the details (Ian, 25 Oct 2026); the counts, Retry and the
                    // camera folder's Sync now stay, and so does the Sort / Keep line, which labels the boxes below.
                    HeaderDetailsSection(detailsOn) {
                        if (backsUpTo != null) {
                            Text(
                                text = if (backsUpTo == BackupLocation.GOOGLE_PHOTOS || backupFolder == null) {
                                    stringResource(R.string.album_backs_up_library)
                                } else {
                                    stringResource(R.string.album_backs_up_to, stringResource(backsUpTo.labelRes()), "$backupFolder/$albumName")
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val elsewhere = entries.asSequence()
                                .filter { it.state == BackupState.UPLOADED && it.location != backsUpTo }
                                .map { it.location }.distinct().toList()
                            if (elsewhere.isNotEmpty()) {
                                Text(
                                    text = stringResource(
                                        R.string.album_also_in,
                                        elsewhere.map { stringResource(it.labelRes()) }.joinToString(", ")
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    if (entries.isNotEmpty()) {
                        // Plain text in the card's own ink: the coloured counts this line used to have
                        // were the theme's primary and tertiary, which are not made to sit on green.
                        val keptText = stringResource(R.string.album_status_kept, entries.count { FilePin.isPinned(it.modeOverride) })
                        val notInCloudText = stringResource(R.string.album_status_not_in_cloud, resend.missing)
                        val counts = buildList {
                            // A file gone from the Cloud since is not counted as backed up (Ian, 22 Nov 2026).
                            val backed = entries.count { it.state == BackupState.UPLOADED } - resend.missing
                            val proxied = entries.count { it.isProxied }
                            val kept = entries.count { FilePin.isPinned(it.modeOverride) }
                            val pending = entries.count { it.state == BackupState.PENDING }
                            val failed = entries.count { it.state == BackupState.FAILED }
                            if (backed > 0) add("$backed backed up")
                            if (resend.missing > 0) add(notInCloudText)
                            if (proxied > 0) add("$proxied optimized")
                            if (kept > 0) add(keptText)
                            if (pending > 0) add("$pending pending")
                            if (failed > 0) add("$failed failed")
                        }
                        WithHelp(HelpTopic.ALBUM_FILE_STATUS) {
                            Text(
                                text = counts.joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // Files that failed five times are left alone by the queue, so this is the way back.
                        val failedFiles = entries.count { it.state == BackupState.FAILED }
                        if (RetryFailed.offered(mode, failedFiles)) {
                            Text(
                                text = stringResource(R.string.album_retry_failed_hint),
                                style = MaterialTheme.typography.bodySmall
                            )
                            HeroOutlinedButton(
                                onClick = onRetryFailed,
                                label = stringResource(R.string.album_retry_failed, failedFiles)
                            )
                        }

                        // Sent, and gone from the Cloud since: the user's choice whether they go again (Ian, 22 Nov 2026).
                        // Offered only where the album uploads, as Retry failed is; an Off album says so instead.
                        // No words here (Ian, 22 Nov 2026): the counts line says how many, and the (?) says the rest.
                        // An Off album gets no buttons; its count stays on the counts line.
                        if (resend.missing > 0 && resend.canResend) {
                            WithHelp(HelpTopic.ALBUM_RESEND) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    HeroOutlinedButton(
                                        onClick = if (resend.selected == resend.missing) resend.onClear else resend.onSelectAll,
                                        label = stringResource(
                                            if (resend.selected == resend.missing) R.string.retrieve_clear_selection
                                            else R.string.retrieve_select_all
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    HeroOutlinedButton(
                                        onClick = resend.onResend,
                                        label = stringResource(R.string.album_resend, resend.selected),
                                        enabled = resend.selected > 0,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        if (camera != null) CameraOptimizeSection(camera)

                        // Sort by and Keep at full size on one line. The heading is two lines, and only
                        // there when some file has a box.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.album_sort_label),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Box {
                                HeroOutlinedButton(
                                    onClick = { sortMenuOpen = true },
                                    label = stringResource(sort.label())
                                )
                                DropdownMenu(
                                    expanded = sortMenuOpen,
                                    onDismissRequest = { sortMenuOpen = false }
                                ) {
                                    FileSort.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(option.label())) },
                                            onClick = {
                                                onSort(option)
                                                sortMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            if (showKeepColumn) {
                                HelpButton(HelpTopic.ALBUM_FILE_PIN)
                                Text(
                                    text = stringResource(R.string.album_keep_column),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(56.dp)
                                )
                            }
                        }
                    }
                }
                // Only when there is something to fold away.
                if (backsUpTo != null) HeaderDetailsToggle(HeaderKey.ALBUM_PAGE)
            }
        }
    }
}

/**
 * One file, as the Restore tab draws one: a rounded card, the name in `titleMedium`, then a line with
 * its size and its marks. The tick box, where there is one, sits at the end.
 */
@Composable
private fun FileCard(
    entry: BackupEntryEntity,
    context: android.content.Context,
    showKeepBox: Boolean,
    onSetPinned: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * In the camera folder only: whether Sync now will optimize this file. Selected is highlighted as on the
     * Restore tab; not selected is greyed (Ian, 27 Sept 2026). Null everywhere else, which draws the plain card.
     */
    cameraSelected: Boolean? = null,
    /** Sent, and no longer in its Cloud at the last check: the line says so rather than naming the Cloud as its place. */
    goneFromCloud: Boolean = false,
    /** Its upload keeps being interrupted without getting anywhere; it is sent after the other files. */
    stalled: Boolean = false
) {
    val scheme = MaterialTheme.colorScheme
    val selected = cameraSelected == true
    val container by animateColorAsState(
        if (selected) scheme.primaryContainer else scheme.surface, tween(220), label = "container"
    )
    val content by animateColorAsState(
        if (selected) scheme.onPrimaryContainer else scheme.onSurface, tween(220), label = "content"
    )
    val borderColor by animateColorAsState(
        if (selected) scheme.primary else scheme.outline, tween(220), label = "borderColor"
    )
    val borderWidth by animateDpAsState(if (selected) 2.dp else 1.dp, tween(220), label = "borderWidth")
    Surface(
        modifier = modifier.fillMaxWidth().alpha(if (cameraSelected == false) 0.5f else 1f),
        shape = RoundedCornerShape(22.dp),
        color = container,
        contentColor = content,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // The same type as the Restore tab's file cards, which is the Albums card's (Ian,
            // 19 Sept 2026): bodyLarge for the name, bodySmall for the line under it.
            Column(modifier = Modifier.weight(1f)) {
                // Name and size share the top line, status and place the one below (Ian, 4 Oct 2026).
                // The size moved up so the lower line is about what happened to the file rather than
                // what it is, which is what leaves room to name the Cloud it went to.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = buildString {
                            append(formatBytes(context, entry.sizeOnPhone()))
                            if (entry.isVideo) append(" · video")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
                // Date, marks and Cloud on the lower line, each mark in its own colour. The date is
                // beside the status and not instead of it (Ian, 27 Sept 2026): Sort by Date and the
                // Camera age both go by it, so it has to be on screen. The date taken (Ian, 1 Nov 2026).
                // An optimized file's size is what is on the phone now, not the original's (Ian, 27 Sept 2026): the
                // original size made a file just optimized look as if nothing had shrunk — the size now sits on the
                // line above, and the optimized mark here says the full-size original is in the Cloud.
                val date = FileDateFormat.format(
                    Instant.ofEpochSecond(entry.dateForSorting).atZone(ZoneId.systemDefault())
                )
                // Gone from the Cloud since: it is not backed up any more, whatever the record says.
                val marks = entry.statusLines(stalled).filterNot { goneFromCloud && it.first == "✓ backed up" }
                // Which Cloud it went to, named only once it is actually there (Ian, 4 Oct 2026: "or
                // where it was backed up to" — past tense). A pending or failed row has a *destination*
                // rather than a place, and naming one would read as a claim the app has not earned.
                // Rows written before the column existed carry the default, OneDrive, which is what
                // every file went to then.
                val placed = entry.state == BackupState.UPLOADED
                val cloud = if (placed) stringResource(entry.location.labelRes()) else null
                val notInCloud = if (placed && goneFromCloud) {
                    stringResource(R.string.album_file_not_in_cloud, stringResource(entry.location.labelRes()))
                } else null
                Text(
                    text = buildAnnotatedString {
                        append(date)
                        marks.forEach { (text, color) ->
                            append(" · ")
                            withStyle(SpanStyle(color = color)) { append(text) }
                        }
                        if (notInCloud != null) {
                            append(" · ")
                            withStyle(SpanStyle(color = scheme.error)) { append(notInCloud) }
                        } else if (cloud != null) {
                            append(" · ")
                            append(cloud)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (selected) {
                Icon(
                    imageVector = SignalIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = scheme.primary
                )
            }

            if (showKeepBox) {
                // Colours are the theme's own: a checkbox draws itself from the colour scheme, so it
                // reads in both themes without anything being set here.
                val description = stringResource(R.string.album_file_keep_description, entry.displayName)
                Checkbox(
                    checked = FilePin.isPinned(entry.modeOverride),
                    onCheckedChange = onSetPinned,
                    modifier = Modifier.semantics { contentDescription = description }
                )
            }
        }
    }
}

/** What the header needs for **Resend**: how many files are gone from the Cloud, how many are selected, and the actions. */
private class ResendHeader(
    val missing: Int,
    val canResend: Boolean,
    val selected: Int,
    val onSelectAll: () -> Unit,
    val onClear: () -> Unit,
    val onResend: () -> Unit
)

/** What the header needs to draw the camera folder's controls: the choices, what they select, and what to do. */
private data class CameraHeader(
    val controls: CameraOptimizeControls,
    val choice: CameraOptimizeChoice,
    /** The selected files that can still be optimized: what Sync now will do. */
    val toOptimize: List<BackupEntryEntity>,
    /** Files in this folder waiting to upload, which Sync now sends too. */
    val hasPending: Boolean,
    /** The chosen kinds have files ready, but none is as old as the chosen age. */
    val tooYoung: Boolean,
    val onAge: (ArchiveAge) -> Unit,
    val onPhotos: (Boolean) -> Unit,
    val onVideos: (Boolean) -> Unit,
    val onSyncNow: () -> Unit
)

/**
 * The camera folder's controls: an age and which kinds, which select files in the list below; what the selection
 * would give back; and Sync now and Rescan as the Albums tab has them.
 *
 * Ian's design, 20 Sept 2026, reworked 27 Sept 2026. The choices start at the Camera defaults in Settings. Nothing
 * is optimized until Sync now is pressed, which also sends anything waiting to upload. The count and the estimate
 * are of exactly the files Sync now will hand the worker.
 */
@Composable
private fun CameraOptimizeSection(camera: CameraHeader) {
    val controls = camera.controls
    val context = LocalContext.current
    val signal = LocalGallerySyncColors.current
    val busy = controls.running

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.camera_optimize_heading),
                style = MaterialTheme.typography.bodyMedium
            )
            HelpButton(HelpTopic.ALBUM_CAMERA_OPTIMIZE)
        }
        // The age's two menus, as every age in the app has them (Ian, 14 Nov 2026), then the two kinds. The kinds
        // are toggles: a check mark and full ink when on, the word alone and faded when off.
        ArchiveAgePicker(
            age = camera.choice.age,
            onSetAge = camera.onAge,
            zeroLabel = { stringResource(R.string.camera_age_all) }
        ) { label, menuEnabled, onClick ->
            HeroOutlinedButton(onClick = onClick, label = label, enabled = !busy && menuEnabled)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KindToggle(
                label = stringResource(R.string.camera_kind_photos),
                on = camera.choice.photos,
                enabled = !busy,
                onToggle = camera.onPhotos,
                modifier = Modifier.weight(1f)
            )
            KindToggle(
                label = stringResource(R.string.camera_kind_videos),
                on = camera.choice.videos,
                enabled = !busy,
                onToggle = camera.onVideos,
                modifier = Modifier.weight(1f)
            )
        }

        // One short status line. How selecting works is in the (?) beside it, not spelled out here (Ian, 27 Sept 2026).
        val count = camera.toOptimize.size
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    busy -> stringResource(R.string.camera_optimize_working, count)
                    count == 0 && camera.tooYoung -> stringResource(
                        R.string.camera_optimize_none_old_enough,
                        camera.choice.age.label { stringResource(R.string.camera_age_all) }
                    )
                    count == 0 -> stringResource(R.string.camera_optimize_none_selected)
                    else -> pluralStringResource(
                        R.plurals.camera_optimize_summary,
                        count,
                        count,
                        formatBytes(context, controls.estimate(camera.toOptimize))
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f, fill = false)
            )
            HelpButton(HelpTopic.ALBUM_CAMERA_SELECT)
        }

        // Sync now and Rescan, as the Albums tab has them. Sync now sends what is waiting and optimizes what is
        // listed, so it is pressable when either is there to do.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = camera.onSyncNow,
                enabled = !busy && !controls.uploading && (count > 0 || camera.hasPending),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = signal.accent,
                    contentColor = signal.onAccent,
                    disabledContainerColor = LocalContentColor.current.copy(alpha = 0.14f),
                    disabledContentColor = LocalContentColor.current.copy(alpha = 0.55f)
                )
            ) {
                Text(
                    text = when {
                        busy -> stringResource(R.string.camera_optimize_working, count)
                        controls.uploading -> stringResource(R.string.backup_syncing)
                        else -> stringResource(R.string.backup_run_now)
                    },
                    maxLines = 1
                )
            }
            HeroOutlinedButton(
                onClick = controls.onRescan,
                label = stringResource(
                    if (controls.checkingCloud) R.string.backup_checking_cloud else R.string.backup_rescan
                ),
                enabled = !controls.checkingCloud,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** One kind, Photos or Videos, as a toggle in the hero card's own ink. */
@Composable
private fun KindToggle(
    label: String,
    on: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val description = stringResource(if (on) R.string.camera_kind_on else R.string.camera_kind_off, label)
    HeroOutlinedButton(
        onClick = { onToggle(!on) },
        label = if (on) "✓ $label" else label,
        enabled = enabled,
        modifier = modifier
            .alpha(if (on) 1f else 0.6f)
            .semantics { contentDescription = description }
    )
}

private fun FileSort.label(): Int = when (this) {
    FileSort.NAME -> R.string.album_sort_name
    FileSort.DATE -> R.string.album_sort_date
    FileSort.STATUS -> R.string.album_sort_status
}

/** What the file takes on the phone: the smaller copy once optimized, otherwise the file itself. */
private fun BackupEntryEntity.sizeOnPhone(): Long =
    localProxySizeBytes?.takeIf { isProxied } ?: sizeBytes

/**
 * The file's marks, in order: backed up, then optimized.
 *
 * Both facts, because optimized never replaces backed up. `isProxied` was tested first and won, so
 * every optimized file read "✓ optimized" and nothing said it was in the cloud — on precisely the
 * files where that matters most, since a proxy is the case where the full-resolution image exists
 * *only* in OneDrive. Ian, 4 Sept 2026, reading a folder of 100 rows: "all the files are labeled as
 * optimized NOT backed up". A proxy is only ever written over a file the ledger has verified in the
 * cloud, so the two are not alternatives.
 *
 * They were two lines for a while (Ian, 18 Sept 2026) and share the size's line now (19 Sept).
 *
 * Safe is one colour. Uploaded reads primary whether or not it was later optimized.
 */
@Composable
private fun BackupEntryEntity.statusLines(stalled: Boolean = false): List<Pair<String, Color>> {
    // The safe green, not `primary`: in the light theme primary is nearly black. Ian, 19 Sept 2026.
    val primary = LocalGallerySyncColors.current.safeText
    val optimized = MaterialTheme.colorScheme.tertiary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val error = MaterialTheme.colorScheme.error
    return when {
        isProxied && state == BackupState.UPLOADED -> listOf("✓ backed up" to primary, "optimized" to optimized)
        isProxied -> listOf("✓ optimized" to optimized)
        state == BackupState.UPLOADED -> listOf("✓ backed up" to primary)
        // Ian, 23 Nov 2026: flag a file that keeps failing to finish, and let the others go first.
        state == BackupState.PENDING && stalled -> listOf("⟳ couldn't finish uploading · trying after the rest" to error)
        state == BackupState.PENDING -> listOf("⟳ pending" to muted)
        state == BackupState.FAILED -> listOf("✗ failed" to error)
        else -> listOf("?" to muted)
    }
}

@Composable
private fun AlbumMode.label(): String = when (this) {
    AlbumMode.OFF -> "Off"
    AlbumMode.BACKUP -> "Backup"
    AlbumMode.SYNC -> "Optimize"
    AlbumMode.ARCHIVE -> "Archive"
}
