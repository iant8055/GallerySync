package com.gallery.sync.ui.settings

import com.gallery.sync.data.remote.cloud.ConnectionKind
import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.billing.MultiCloudTrial
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepFreeQuestionTest {

    private fun cloud(location: BackupLocation, connected: Boolean = true) =
        ProviderState(location, ConnectionKind.OAUTH, accountLabel = if (connected) "x" else null)

    private val two = listOf(cloud(BackupLocation.ONEDRIVE), cloud(BackupLocation.GOOGLE_PHOTOS))

    private fun state(
        providers: List<ProviderState> = two,
        trial: MultiCloudTrial.State = MultiCloudTrial.State.Ended,
        pro: Boolean = false,
        answered: Boolean = false
    ) = CloudProvidersUiState(
        providers = providers, trial = trial, isProUnlocked = pro, keepFreeAnswered = answered, loaded = true
    )

    @Test
    fun `asked once the trial has ended with two clouds and no Pro`() {
        assertTrue(state().needsKeepFreeQuestion)
    }

    @Test
    fun `not asked while the trial runs, with Pro, with one cloud, or once answered`() {
        assertFalse(state(trial = MultiCloudTrial.State.Active(10)).needsKeepFreeQuestion)
        assertFalse(state(pro = true).needsKeepFreeQuestion)
        assertFalse(state(providers = listOf(cloud(BackupLocation.ONEDRIVE))).needsKeepFreeQuestion)
        assertFalse(state(answered = true).needsKeepFreeQuestion)
    }
}
