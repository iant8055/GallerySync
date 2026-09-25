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
import com.gallery.sync.domain.backup.RestoreEverythingFrom
import com.gallery.sync.domain.billing.BillingRepository
import com.gallery.sync.domain.billing.MultiCloudEntitlement
import com.gallery.sync.domain.billing.MultiCloudTrial
import com.gallery.sync.domain.billing.PurchaseOutcome
import com.gallery.sync.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

/** Where the "what do you want to do with the files there?" question has got to. */
sealed interface SignOutState {
    val location: BackupLocation

    /** The first step: what signing out means, before anything else is asked. */
    data class Warning(override val location: BackupLocation) : SignOutState

    /** Asking. [files] and [bytes] are what "restore them" would bring back to full size on this phone. */
    data class Asking(override val location: BackupLocation, val files: Int, val bytes: Long) : SignOutState

    /** Restoring first; the sign-out follows once every file is back. */
    data class Restoring(
        override val location: BackupLocation,
        val finished: Int,
        val total: Int,
        val current: String
    ) : SignOutState

    /** Some files could not be restored, so the app has not signed out and lets the user decide. */
    data class Incomplete(override val location: BackupLocation, val restored: Int, val failed: Int) : SignOutState
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
    val loaded: Boolean = false,
    /** Whether the "which cloud do you want to keep free?" question has been answered. */
    val keepFreeAnswered: Boolean = false,
    /** The question shown when the user signs out of a cloud that holds files; null when none is open. */
    val signOut: SignOutState? = null
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

    /**
     * The trial has ended, Pro is not bought, and more than one cloud is connected: the one moment the user
     * has to say which cloud stays free. Asked once. Nothing is moved by the answer — see [CloudProvidersViewModel].
     */
    val needsKeepFreeQuestion: Boolean
        get() = loaded && !isProUnlocked && trial == MultiCloudTrial.State.Ended && !keepFreeAnswered && connected.size > 1

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
    private val entitlement: MultiCloudEntitlement,
    private val restoreEverything: RestoreEverythingFrom
) : ViewModel() {

    private var restoreJob: Job? = null

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
                main = settings.current().backupLocation,
                isProUnlocked = billing.isPurchased(),
                trial = entitlement.trialState(),
                keepFreeAnswered = settings.current().keepFreeAnswered,
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
     * Chooses the one free cloud — in the setup wizard, where folders have not been paired yet and follow the
     * choice. Never from Settings: a pairing the user set is not rewritten behind their back (Ian, 24 Sept
     * 2026). Anything already uploaded keeps its recorded history.
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

    /**
     * The answer to "which cloud do you want to keep free?" once the trial has ended. Only changes which cloud
     * is free; every pairing stays as it is, and a folder paired with another cloud simply waits, with a line
     * in Settings saying so, until the user re-pairs it or unlocks Pro.
     */
    fun keepFree(location: BackupLocation) {
        viewModelScope.launch {
            settings.setBackupLocation(location)
            settings.setKeepFreeAnswered()
            refresh()
        }
    }

    /**
     * The sign-out button. Opens a warning first; nothing happens until the user goes on from it (Ian, 25 Sept
     * 2026). See [continueSignOut].
     */
    fun requestSignOut(location: BackupLocation) {
        _state.value = _state.value.copy(signOut = SignOutState.Warning(location))
    }

    /**
     * Past the warning. When the cloud holds files that are not at full size on this phone, ask what to do with
     * them; with nothing to bring back there is nothing to decide, so it signs out at once. Either way no pairing
     * moves: see [disconnect].
     */
    fun continueSignOut() {
        val location = _state.value.signOut?.location ?: return
        viewModelScope.launch {
            val plan = restoreEverything.plan(location)
            if (plan.total == 0) {
                _state.value = _state.value.copy(signOut = null)
                disconnect(location)
            } else {
                _state.value = _state.value.copy(signOut = SignOutState.Asking(location, plan.total, plan.bytes))
            }
        }
    }

    /** Closes the question, or Stops a restore in flight; either way the app stays signed in. */
    fun cancelSignOut() {
        restoreJob?.cancel()
        restoreJob = null
        _state.value = _state.value.copy(signOut = null)
    }

    /** "Leave them": sign out and touch nothing. */
    fun signOutLeavingFiles() {
        val location = _state.value.signOut?.location ?: return
        _state.value = _state.value.copy(signOut = null)
        disconnect(location)
    }

    /**
     * "Restore them to the phone": bring every file back while still signed in, then sign out. If any file could
     * not be restored the app stays signed in and says so, because signing out would end the chance to fetch it.
     */
    fun restoreThenSignOut() {
        val location = _state.value.signOut?.location ?: return
        if (restoreJob?.isActive == true) return
        restoreJob = viewModelScope.launch {
            try {
                val plan = restoreEverything.plan(location)
                _state.value = _state.value.copy(signOut = SignOutState.Restoring(location, 0, plan.total, ""))
                val outcome = restoreEverything.run(plan) { finished, total, current ->
                    _state.value = _state.value.copy(signOut = SignOutState.Restoring(location, finished, total, current))
                }
                if (outcome.failed == 0) {
                    _state.value = _state.value.copy(signOut = null)
                    disconnect(location)
                } else {
                    _state.value = _state.value.copy(
                        signOut = SignOutState.Incomplete(location, outcome.restored + outcome.downloaded, outcome.failed)
                    )
                }
            } catch (e: CancellationException) {
                _state.value = _state.value.copy(signOut = null)
                throw e
            }
        }
    }

    /**
     * Signs out and changes nothing else. A folder paired with this cloud stays paired, and its unsent files
     * stay routed to it: the upload pass skips a cloud that is not connected without failing anything, so they
     * simply wait. Signing out is not a decision about where files go: the user may have done it to switch
     * accounts, or by accident, or want the files kept where they are (Ian, 25 Sept 2026, after signing out of
     * Dropbox to change account quietly moved his `dropbox` folder to OneDrive). Moving a folder is the user's
     * choice, made in the folder's own menu in Settings.
     */
    fun disconnect(location: BackupLocation) {
        viewModelScope.launch {
            connections.of(location)?.signOut()
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
