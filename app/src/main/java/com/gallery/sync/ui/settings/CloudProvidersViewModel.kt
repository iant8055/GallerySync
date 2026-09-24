package com.gallery.sync.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.FolderPreferenceDao
import com.gallery.sync.data.local.entity.AlbumMode
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
    /** The user's one free cloud: the app-wide default destination. Always uploads. */
    val main: BackupLocation = BackupLocation.DEFAULT,
    val isProUnlocked: Boolean = false,
    /** A sign-in, key check or purchase is in progress; the buttons disable themselves while true. */
    val isBusy: Boolean = false,
    /** The last thing that went wrong, for a one-line explanation. Cleared on the next attempt. */
    val lastError: String? = null,
    val trial: MultiCloudTrial.State = MultiCloudTrial.State.NotStarted,
    /** False until the first refresh has read what is connected, so the app does not flash the wizard. */
    val loaded: Boolean = false
) {
    val connected: List<ProviderState> get() = providers.filter { it.isConnected }

    val anyConnected: Boolean get() = connected.isNotEmpty()

    val mainConnected: Boolean get() = connected.any { it.location == main }

    /** Connected clouds other than the main one — the ones that need Pro or the trial. */
    val extraConnected: List<ProviderState> get() = connected.filter { it.location != main }

    /** Bought, or inside the trial — the one question every gate asks. */
    val isEntitled: Boolean get() = isProUnlocked || trial is MultiCloudTrial.State.Active

    /** What a folder's menu may offer: the main cloud always, the others only with Pro or the trial. */
    val destinations: List<BackupLocation>
        get() = if (isEntitled) connected.map { it.location } else listOf(main)

    /** Whether a folder menu is worth showing: there is a genuine second choice. */
    val isAvailable: Boolean get() = destinations.size > 1
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
            // With more than one cloud connected the default album mode is locked at Off (Ian, 24 Sept 2026),
            // so a new album waits for the user to choose where it goes. Enforced here as well as greyed
            // in Settings, so connecting a second cloud by any route takes effect at once.
            if (providers.count { it.isConnected } > 1 && settings.current().defaultAlbumMode != AlbumMode.OFF) {
                settings.setDefaultAlbumMode(AlbumMode.OFF)
            }
            _state.value = _state.value.copy(
                providers = providers,
                main = settings.current().backupLocation,
                isProUnlocked = billing.isPurchased(),
                trial = entitlement.trialState(),
                loaded = true
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

    /**
     * Makes [location] the one free cloud. Folders and files still waiting that were bound for the old
     * main cloud follow it; anything already uploaded keeps its recorded history.
     */
    fun setMain(location: BackupLocation) {
        viewModelScope.launch {
            val old = settings.current().backupLocation
            if (old == location) return@launch
            settings.setBackupLocation(location)
            folderDao.reassign(old, location)
            entryDao.retargetAllUnsent(old, location)
            refresh()
        }
    }

    fun disconnect(location: BackupLocation) {
        viewModelScope.launch {
            connections.of(location)?.signOut()
            // Never leave a destination pointed at a cloud that just became unreachable. Folders and
            // files still waiting move to the main cloud — or, if it was the main cloud that went, to
            // another connected one, and failing that to the default. Files already sent keep their
            // recorded history: nothing uploaded is touched.
            val main = settings.current().backupLocation
            val target = if (location != main) {
                main
            } else {
                connections.offered().firstOrNull { it.location != location && it.accountLabel() != null }?.location
                    ?: BackupLocation.DEFAULT
            }
            if (target != location) {
                folderDao.reassign(location, target)
                entryDao.retargetAllUnsent(location, target)
            }
            if (main == location && target != location) settings.setBackupLocation(target)
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
