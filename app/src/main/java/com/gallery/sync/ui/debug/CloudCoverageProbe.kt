package com.gallery.sync.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Debug-only dry run: how much of this library is already in OneDrive?
 *
 * Uploads nothing. See [CloudCoverageViewModel] for what the two match columns mean and why they
 * differ.
 */
@Composable
fun CloudCoverageProbe(
    modifier: Modifier = Modifier,
    viewModel: CloudCoverageViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Cloud coverage dry run", style = MaterialTheme.typography.titleMedium)
        Text(
            "Read-only. Counts how many local files are already in Samsung Gallery/DCIM, both the " +
                "way the engine checks today (one page) and the way it should (every page).",
            style = MaterialTheme.typography.bodySmall
        )

        Button(
            onClick = viewModel::run,
            enabled = !state.running,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.running) "Running…" else "Run coverage check")
        }

        state.rows.filter { it.localFiles > 0 }.forEach { row ->
            val detail = row.error
                ?: "local ${row.localFiles} · remote ${row.remoteAllPages} " +
                "(page 1: ${row.remoteFirstPage}) · matched ${row.matchedAllPages}" +
                if (row.matchedAllPages != row.matchedFirstPageOnly) {
                    " — engine sees only ${row.matchedFirstPageOnly}"
                } else ""

            Text(
                "${row.album}: $detail",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 1.dp)
            )
        }

        state.log.forEach {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
