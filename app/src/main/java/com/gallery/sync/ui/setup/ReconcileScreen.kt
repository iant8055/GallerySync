package com.gallery.sync.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.data.local.media.GrantedDirectory
import androidx.compose.ui.semantics.Role
import com.gallery.sync.domain.backup.FirstBackupHold
import com.gallery.sync.domain.backup.LibraryChoice
import com.gallery.sync.domain.backup.LibraryEstimate
import com.gallery.sync.domain.backup.FirstBackupWindow
import com.gallery.sync.domain.backup.MediaTally
import com.gallery.sync.domain.backup.RemoteRoots
import com.gallery.sync.ui.common.LabelWithAction
import com.gallery.sync.ui.common.formatBytes
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The first honest number in setup: how much of the library OneDrive already holds.
 *
 * Deliberately not a gallery or a file list — it is three counts and a total, which is everything
 * needed to answer "should I back everything up?" and nothing more.
 *
 * ### The unchecked line is not decoration
 *
 * When albums could not be listed, this says so and says nothing about their contents. Reporting
 * them as "not backed up" would tell someone their library is unprotected when it may be entirely
 * safe, and the natural response — upload everything — costs hours of transfer and duplicate quota.
 * That exact mistake was made on 19 Aug 2026 with 8,177 files.
 */
@Composable
fun ReconcileScreen(
    modifier: Modifier = Modifier,
    viewModel: ReconcileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Nothing below here means anything yet. With no folders granted the scan returns nothing,
        // and a reconciliation reporting zero outstanding would announce that the whole library is
        // already backed up — false, and false in the direction that stops someone acting.
        //
        // Says so, and says where to fix it. Source folders moved to Settings on 26 Aug 2026, and a
        // screen that simply renders nothing because a choice was made elsewhere is the defect this
        // app kept producing that day — correct behaviour with no way to read it.
        if (!state.hasSources) {
            Text(
                text = stringResource(R.string.reconcile_no_sources),
                style = MaterialTheme.typography.bodyMedium
            )
            return@Column
        }

        HorizontalDivider()

        Text(
            text = stringResource(R.string.reconcile_title),
            style = MaterialTheme.typography.titleMedium
        )

        if (state.noMediaAccess) {
            Text(
                text = stringResource(R.string.reconcile_no_access),
                style = MaterialTheme.typography.bodyMedium
            )
            return@Column
        }

        if (state.running) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        val result = state.result
        if (result == null) {
            CircularProgressIndicator()
            return@Column
        }

        MediaLine(
            label = stringResource(R.string.reconcile_photos),
            backedUp = result.photosBackedUp,
            outstanding = result.photosOutstanding
        )
        MediaLine(
            label = stringResource(R.string.reconcile_videos),
            backedUp = result.videosBackedUp,
            outstanding = result.videosOutstanding
        )

        // The number the "back up everything" decision actually turns on.
        //
        // `!state.running` is not cosmetic. Mid-run the totals cover only the albums checked so far,
        // and `isComplete` is trivially true because nothing has failed yet — so after one album of
        // ninety this claimed the whole library was already safe. Announcing that before the check
        // finishes is the same false reassurance the unchecked category exists to prevent, pointed
        // the other way.
        val outstanding = result.outstanding
        val allSafe = !state.running && outstanding.files == 0 && result.isComplete
        Text(
            text = if (allSafe) {
                stringResource(R.string.reconcile_all_safe)
            } else {
                stringResource(
                    R.string.reconcile_outstanding,
                    formatBytes(context, outstanding.bytes)
                )
            },
            style = MaterialTheme.typography.bodyLarge
        )

        // Said plainly, and never folded into either other figure.
        if (!result.isComplete) {
            Text(
                text = stringResource(
                    R.string.reconcile_incomplete,
                    result.albumsUnchecked,
                    formatBytes(context, result.unchecked.bytes)
                ),
                style = MaterialTheme.typography.bodySmall
            )
            if (!state.running) {
                OutlinedButton(onClick = viewModel::start) {
                    Text(stringResource(R.string.reconcile_retry), maxLines = 1)
                }
            }
        }

        HorizontalDivider()

        LibrarySection(
            selected = state.libraryChoice,
            applied = state.libraryApplied,
            applying = state.applyingLibraryChoice,
            photoBytes = result.photos.bytes,
            videoBytes = result.videos.bytes,
            outstandingFiles = result.outstanding.files,
            totalFiles = result.backedUp.files + result.outstanding.files,
            onSelected = viewModel::setLibraryChoice,
            onApply = viewModel::applyLibraryChoice
        )

        // The destination and the first-backup window used to sit below here, and both moved to
        // Settings on 26 Aug 2026 when this tab became Archive — see DestinationSection for why they
        // belong there. What is left is the two things this screen is actually about: the
        // reconciliation readout, and Gate 2's choice about the existing library.
        //
        // Nothing renders this screen at present. It is kept whole, and compiling, because TASK-014's
        // wizard is built from exactly these two pieces; deleting them would mean writing them again
        // from the commit history a week from now.
    }
}

@Composable
private fun MediaLine(label: String, backedUp: MediaTally, outstanding: MediaTally) {
    val context = LocalContext.current
    val total = backedUp.files + outstanding.files

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(
            text = stringResource(
                R.string.reconcile_line,
                pluralStringResource(R.plurals.file_count, total, total),
                pluralStringResource(R.plurals.file_count, backedUp.files, backedUp.files),
                pluralStringResource(
                    R.plurals.file_count,
                    outstanding.files,
                    outstanding.files
                )
            ),
            style = MaterialTheme.typography.bodySmall
        )
        if (outstanding.bytes > 0) {
            Text(
                text = formatBytes(context, outstanding.bytes),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * When the first whole-library upload will run, and what it is waiting for.
 *
 * The first backup is measured in hours, not seconds — 148 GB on a real device — so the useful thing
 * to show is *when it will happen*, not a progress bar someone has to sit and watch. Once the backlog
 * clears this section says so and stops offering settings that no longer do anything.
 *
 * The hold is named rather than reduced to "waiting". "Waiting until 1am" and "waiting for you to
 * plug in" ask different things of the user, and a phone that appears to be doing nothing for an
 * unexplained reason is the thing this is trying to avoid.
 */
/**
 * Gate 2: what happens to the library already on the phone.
 *
 * One choice applied to thousands of files, made by someone who has not yet watched the app do
 * anything — so the safest option is the default, and the other two say plainly what they cost.
 *
 * Archive is not offered here and must not be added. Setting every album at once to the only mode
 * that removes files, before v0.4 retrieval exists to undo it, is the largest irreversible action
 * this product can take at the moment the user knows least about it.
 *
 * Selecting does nothing; applying is a separate tap. A radio list that acted on touch would make
 * the most consequential screen in the app the easiest one to trigger by accident.
 */
@Composable
private fun LibrarySection(
    selected: LibraryChoice,
    applied: Int?,
    applying: Boolean,
    photoBytes: Long,
    videoBytes: Long,
    /** Files not yet in OneDrive - what BACK_UP_AND_OPTIMISE_NEW would actually touch. */
    outstandingFiles: Int,
    /** Everything in scope, so the middle option can be shown against what it leaves alone. */
    totalFiles: Int,
    onSelected: (LibraryChoice) -> Unit,
    onApply: () -> Unit
) {
    val context = LocalContext.current
    val freed = LibraryEstimate.spaceFreedBySync(photoBytes)
    val marginal = LibraryEstimate.isSavingMarginal(photoBytes, videoBytes)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.library_title),
            style = MaterialTheme.typography.titleSmall
        )

        Column(Modifier.selectableGroup()) {
            LibraryChoice.entries.forEach { choice ->
                val detail = when (choice) {
                    LibraryChoice.CHOOSE_PER_ALBUM ->
                        stringResource(R.string.library_per_album_detail)

                    LibraryChoice.BACK_UP_EVERYTHING ->
                        stringResource(R.string.library_back_up_all_detail)

                    // Both counts, deliberately. On a typical install most of the library is already
                    // in OneDrive - Samsung's own sync put it there - so this option can touch a
                    // couple of percent of a library while sounding substantial. Showing what it
                    // leaves alone is what stops the honest option looking broken.
                    LibraryChoice.BACK_UP_AND_OPTIMISE_NEW ->
                        stringResource(
                            R.string.library_optimise_new_detail,
                            outstandingFiles,
                            totalFiles
                        )

                    // Only photos shrink. On a library that is mostly video, saying "frees space"
                    // without saying how little invites someone to expect most of it back — so the
                    // wording leads with what stays instead.
                    LibraryChoice.BACK_UP_AND_FREE_SPACE -> if (marginal) {
                        stringResource(
                            R.string.library_free_space_detail_marginal,
                            formatBytes(context, freed),
                            formatBytes(context, photoBytes + videoBytes)
                        )
                    } else {
                        stringResource(
                            R.string.library_free_space_detail,
                            formatBytes(context, freed)
                        )
                    }
                }

                ChoiceRow(
                    label = when (choice) {
                        LibraryChoice.CHOOSE_PER_ALBUM -> stringResource(R.string.library_per_album)
                        LibraryChoice.BACK_UP_EVERYTHING -> stringResource(R.string.library_back_up_all)
                        LibraryChoice.BACK_UP_AND_OPTIMISE_NEW ->
                            stringResource(R.string.library_optimise_new)

                        LibraryChoice.BACK_UP_AND_FREE_SPACE -> stringResource(R.string.library_free_space)
                    },
                    detail = detail,
                    selected = choice == selected,
                    onSelect = { onSelected(choice) }
                )
            }
        }

        // Only where it is true. Saying it beside "choose album by album" would be a warning about
        // something that is not going to happen.
        if (selected.uploads) {
            Text(
                text = stringResource(R.string.library_first_run_warning),
                style = MaterialTheme.typography.bodySmall
            )
        }

        applied?.let { count ->
            Text(
                text = stringResource(
                    R.string.library_applied,
                    pluralStringResource(R.plurals.album_count, count, count)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        OutlinedButton(onClick = onApply, enabled = !applying) {
            Text(stringResource(R.string.library_apply), maxLines = 1)
        }
    }
}

/** One radio option with its consequence written underneath it. */
@Composable
private fun ChoiceRow(
    label: String,
    detail: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}
