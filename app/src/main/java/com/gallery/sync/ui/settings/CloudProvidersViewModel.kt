package com.gallery.sync.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.FolderPreferenceDao
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.data.remote.auth.SignInResult
import com.gallery.sync.data.remote.cloud.CloudConnections
import com.gallery.sync.data.remote.cloud.ConnectionKind
import com.gallery.sync.data.remote.cloud.KeyField
import com.gallery.sync.domain.backup.BackupLocation
import com.gallery.sync.domain.billing.BillingRepository
import com.gallery.sync.domain.billing.MultiCloudEntitlement
import com.gallery.sync.domain.billing.MultiCloudTrial
import com.gallery.sync.domain.billing.PurchaseOutcome
import com.gallery.sync.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One optional cloud as the screens show it. */
data class ProviderState(
    val location: BackupLocation,
    val kind: ConnectionKind,
    /** The connected account, or null when not connected. */
    val accountLabel: String? = null,
    val keyFields: List<KeyField> = emptyList()
) {
    val isConnected: Boolean get() = accountLabel != null
}

/**
 * Every optional cloud, plus the one entitlement that covers them all (Pro, or the 30-day trial).
 * See TASK-026 and [MultiCloudTrial].
 */
data class CloudProvidersUiState(
    val providers: List<ProviderState> = emptyList(),
    val isProUnlocked: Boolean = false,
    /** A sign-in, key check or purchase is in progress; the buttons disable themselves while true. */
    val isBusy: Boolean = false,
    /** The last thing that went wrong, for a one-line explanation. Cleared on the next attempt. */
    val lastError: String? = null,
    val trial: MultiCloudTrial.State = MultiCloudTrial.State.NotStarted
) {
    val connected: List<ProviderState> get() = providers.filter { it.isConnected }

    /** Bought, or inside the trial — the one question every gate asks. */
    val isEntitled: Boolean get() = isProUnlocked || trial is MultiCloudTrial.State.Active

    /** Whether a second cloud can actually be chosen for a folder right now. */
    val isAvailable: Boolean get() = connected.isNotEmpty() && isEntitled

    /** What a folder's menu may offer besides OneDrive. */
    val destinations: List<BackupLocation> get() = if (isEntitled) connected.map { it.location } else emptyList()
}

/**
 * Drives connecting, disconnecting and unlocking the optional clouds, in Settings and in the setup
 * wizard alike. Knows no provider by name: it lists whatever [CloudConnections] offers.
 *
 * Kept apart from [com.gallery.sync.ui.backup.BackupViewModel] the way `ThemeViewModel` is — a
 * focused concern, not one more thing on an already large ViewModel.
 */
@HiltViewModel
class CloudProvidersViewModel @Inject constructor(
    private val connections: CloudConnections,
    private val billing: BillingRepository,
    private val settings: BackupSettings,
    private val folderDao: FolderPreferenceDao,
    private val entryDao: BackupEntryDao,
    private val entitlement: MultiCloudEntitlement
) : ViewModel() {

    private val _state = MutableStateFlow(CloudProvidersUiState())
    val state: StateFlow<CloudProvidersUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Re-reads connection and purchase state fresh. Never trusts a cached flag — see [BillingRepository]. */
    fun refresh() {
        viewModelScope.launch {
            val providers = connections.offered().map {
                ProviderState(it.location, it.kind, it.accountLabel(), it.keyFields)
            }
            _state.value = _state.value.copy(
                providers = providers,
                isProUnlocked = billing.isPurchased(),
                trial = entitlement.trialState()
            )
        }
    }

    /** Starts the 30-day trial. Only ever called from a button with the terms on screen above it. */
    fun startTrial() {
        viewModelScope.launch {
            entitlement.startTrial()
            refresh()
        }
    }

    fun connect(location: BackupLocation, activity: Activity) {
        val connection = connections.of(location) ?: return
        runBusy {
            when (val result = connection.signIn(activity)) {
                is SignInResult.Success -> Logger.i(TAG, "connected $location")
                SignInResult.Cancelled -> Unit
                is SignInResult.Failed -> {
                    Logger.w(TAG, "$location sign-in failed: ${result.errorCode}")
                    _state.value = _state.value.copy(lastError = result.errorCode)
                }
            }
        }
    }

    fun connectWithKeys(location: BackupLocation, values: Map<String, String>) {
        val connection = connections.of(location) ?: return
        runBusy {
            when (val result = connection.connectWithKeys(values)) {
                is SignInResult.Success -> Logger.i(TAG, "connected $location")
                SignInResult.Cancelled -> Unit
                is SignInResult.Failed -> _state.value = _state.value.copy(lastError = result.errorCode)
            }
        }
    }

    fun disconnect(location: BackupLocation) {
        viewModelScope.launch {
            connections.of(location)?.signOut()
            // Never leave a destination pointed at a provider that just became unreachable — the
            // same reasoning as BackupLocations' "at least one must stay on". Covers every folder
            // routed there, the fallback default, and files still waiting to go. Files already sent
            // keep their recorded history: nothing uploaded is touched.
            folderDao.reassign(location, BackupLocation.ONEDRIVE)
            entryDao.retargetAllUnsent(location, BackupLocation.ONEDRIVE)
            if (settings.current().backupLocation == location) {
                settings.setBackupLocation(BackupLocation.ONEDRIVE)
            }
            refresh()
        }
    }

    fun unlockPro(activity: Activity) {
        runBusy {
            when (val result = billing.purchase(activity)) {
                PurchaseOutcome.Success, PurchaseOutcome.AlreadyOwned -> Logger.i(TAG, "pro_unlock now owned")
                PurchaseOutcome.Cancelled -> Unit
                is PurchaseOutcome.Failed -> {
                    Logger.w(TAG, "purchase failed: ${result.debugMessage}")
                    _state.value = _state.value.copy(lastError = result.debugMessage)
                }
            }
        }
    }

    private fun runBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true, lastError = null)
            try {
                block()
            } finally {
                _state.value = _state.value.copy(isBusy = false)
                refresh()
            }
        }
    }

    private companion object {
        const val TAG = "CloudProvidersVM"
    }
}
