package com.gallery.sync.ui.retrieve

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gallery.sync.R
import com.gallery.sync.ui.help.HelpTopic
import com.gallery.sync.ui.help.WithHelp
import com.gallery.sync.domain.backup.CloudDeletionPolicy

/**
 * Deletion sync: the one setting, what happens to the OneDrive copy of a file deleted from the phone.
 *
 * Leave or Ask, and nothing else. The waiting period and the "gone from this phone" review list that
 * used to sit under Ask were taken out on 19 Sept 2026 (Ian). What to do about files that have gone is
 * decided in a window that opens with the app (`ui/deleted/DeletedFilesGate`), and that window only
 * ever appears under Ask.
 */
@Composable
fun DeletionSection(
    modifier: Modifier = Modifier,
    viewModel: DeletionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        WithHelp(HelpTopic.SETTINGS_DELETION) {
            Text(
                text = stringResource(R.string.deletion_title),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Column(Modifier.selectableGroup()) {
            PolicyRow(
                label = stringResource(R.string.deletion_leave),
                selected = state.policy == CloudDeletionPolicy.LEAVE,
                onSelect = { viewModel.setPolicy(CloudDeletionPolicy.LEAVE) }
            )
            PolicyRow(
                label = stringResource(R.string.deletion_ask),
                selected = state.policy == CloudDeletionPolicy.ASK,
                onSelect = { viewModel.setPolicy(CloudDeletionPolicy.ASK) }
            )
        }
    }
}

@Composable
private fun PolicyRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
