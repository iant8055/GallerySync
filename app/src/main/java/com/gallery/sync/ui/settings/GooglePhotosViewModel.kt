package com.gallery.sync.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.FolderPreferenceDao
import com.gallery.sync.data.local.settings.BackupSettings
import com.gallery.sync.data.remote.auth.GooglePhotosSignIn
import com.gallery.sync.data.remote.auth.SignInResult
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

/**
 * Sign-in and purchase state for Google Photos — the two things that together decide whether it's
 * choosable as the app-wide destination. See TASK-026 and [GooglePhotosDestination
 * .canChoose][com.gallery.sync.domain.backup.GooglePhotosDestination].
 */
data class GooglePhotosUiState(
    val accountLabel: String? = null,
    val isProUnlocked: Boolean = false,
    /** A sign-in or purchase flow is in progress; both buttons disable themselves while true. */
    val isBusy: Boolean = false,
    /** The last thing that went wrong, for a one-line explanation. Cleared on the next attempt. */
    val lastError: String? = null,
    /** The 30-day multi-cloud trial. See [MultiCloudTrial]. */
    val trial: MultiCloudTrial.State = MultiCloudTrial.State.NotStarted
) {
    val isSignedIn: Boolean get() = accountLabel != null

    /** Whether Google Photos can actually be chosen as the destination right now. */
    val isAvailable: Boolean get() = isSignedIn && isEntitled

    /** Bought, or inside the trial — the one question every gate asks. */
    val isEntitled: Boolean get() = isProUnlocked || trial is MultiCloudTrial.State.Active
}

/**
 * Drives the Settings-tab "Connect Google Photos" / "Unlock Pro" controls.
 *
 * Kept apart from [com.gallery.sync.ui.backup.BackupViewModel] the way [com.gallery.sync.ui.theme
 * .ThemeViewModel] already is — a separate, focused concern, not one more thing piled onto an
 * already large ViewModel. `SettingsScreen` composes both, same as it already composes
 * `BackupViewModel` and `ThemeViewModel` side by side.
 */
@HiltViewModel
class GooglePhotosViewModel @Inject constructor(
    private val signIn: GooglePhotosSignIn,
    private val billing: BillingRepository,
    private val settings: BackupSettings,
    private val folderDao: FolderPreferenceDao,
    private val entryDao: BackupEntryDao,
    private val entitlement: MultiCloudEntitlement
) : ViewModel() {

    private val _state = MutableStateFlow(GooglePhotosUiState())
    val state: StateFlow<GooglePhotosUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Re-reads sign-in and purchase state fresh. Never trusts a cached flag — see [BillingRepository]. */
    fun refresh() {
        viewModelScope.launch {
            val account = signIn.currentAccountName()
            val purchased = billing.isPurchased()
            val trial = entitlement.trialState()
            _state.value = _state.value.copy(accountLabel = account, isProUnlocked = purchased, trial = trial)
        }
    }

    /**
     * Starts the 30-day trial. Only ever called from a button that has just put the terms on screen —
     * see [MultiCloudTrial]. Safe to call twice; the original start stands.
     */
    fun startTrial() {
        viewModelScope.launch {
            entitlement.startTrial()
            refresh()
        }
    }

    fun connect(activity: Activity) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true, lastError = null)
            when (val result = signIn.signIn(activity)) {
                is SignInResult.Success -> Logger.i(TAG, "connected to Google Photos")
                SignInResult.Cancelled -> Unit
                is SignInResult.Failed -> {
                    Logger.w(TAG, "Google Photos sign-in failed: ${result.errorCode}")
                    _state.value = _state.value.copy(lastError = result.errorCode)
                }
            }
            _state.value = _state.value.copy(isBusy = false)
            refresh()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            signIn.signOut()
            // Never leave a destination pointed at a provider that just became unreachable — same
            // reasoning as BackupLocations' "at least one must stay on". Covers every folder routed
            // here, the fallback default, and files still waiting to go. Files already sent keep their
            // recorded history: nothing uploaded is touched.
            folderDao.reassign(BackupLocation.GOOGLE_PHOTOS, BackupLocation.ONEDRIVE)
            entryDao.retargetAllUnsent(BackupLocation.GOOGLE_PHOTOS, BackupLocation.ONEDRIVE)
            if (settings.current().backupLocation == BackupLocation.GOOGLE_PHOTOS) {
                settings.setBackupLocation(BackupLocation.ONEDRIVE)
            }
            refresh()
        }
    }

    fun unlockPro(activity: Activity) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true, lastError = null)
            when (val result = billing.purchase(activity)) {
                PurchaseOutcome.Success, PurchaseOutcome.AlreadyOwned ->
                    Logger.i(TAG, "pro_unlock now owned")

                PurchaseOutcome.Cancelled -> Unit
                is PurchaseOutcome.Failed -> {
                    Logger.w(TAG, "purchase failed: ${result.debugMessage}")
                    _state.value = _state.value.copy(lastError = result.debugMessage)
                }
            }
            _state.value = _state.value.copy(isBusy = false)
            refresh()
        }
    }

    private companion object {
        const val TAG = "GooglePhotosVM"
    }
}
