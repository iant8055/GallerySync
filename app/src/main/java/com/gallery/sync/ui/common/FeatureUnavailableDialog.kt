package com.gallery.sync.ui.common

import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gallery.sync.R
import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.backup.CloudFeature

/** The feature's name as the message shows it, in capitals as asked for. */
@get:StringRes
val CloudFeature.nameRes: Int
    get() = when (this) {
        CloudFeature.RESTORE -> R.string.feature_restore
        CloudFeature.ARCHIVE -> R.string.feature_archive
        CloudFeature.SYNC -> R.string.feature_sync
    }

/**
 * What the user gets on trying something their cloud cannot do (Ian, 24 Sept 2026): the feature stays
 * visible but greyed, and tapping it says plainly that this cloud does not support it and what to do.
 *
 * Restore, Archive and Sync all rest on OneDrive's byte-size proof that a copy is safe, and Restore
 * downloads by OneDrive's own ids, so a backup that went anywhere else can never take part in them.
 */
@Composable
fun FeatureUnavailableDialog(
    cloud: BackupLocation,
    feature: CloudFeature,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    R.string.feature_unsupported_title,
                    stringResource(cloud.labelRes()),
                    stringResource(feature.nameRes)
                )
            )
        },
        text = { Text(stringResource(R.string.feature_unsupported_body)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.tour_ok)) }
        }
    )
}
