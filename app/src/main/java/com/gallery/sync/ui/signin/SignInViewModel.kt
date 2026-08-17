package com.gallery.sync.ui.signin

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gallery.sync.data.remote.auth.OneDriveSignIn
import com.gallery.sync.data.remote.auth.SignInResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the sign-in screen is showing right now. */
sealed interface SignInUiState {

    /** Checking the MSAL cache on first composition. */
    data object Loading : SignInUiState

    /** A sign-in or sign-out is in flight; controls should be disabled. */
    data object Working : SignInUiState

    data object SignedOut : SignInUiState

    data class SignedIn(val accountName: String) : SignInUiState

    data class Error(val errorCode: String) : SignInUiState
}

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val oneDriveSignIn: OneDriveSignIn
) : ViewModel() {

    private val _state = MutableStateFlow<SignInUiState>(SignInUiState.Loading)
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Re-reads the MSAL cache. Safe to call after any operation to resync with the truth. */
    fun refresh() {
        viewModelScope.launch {
            _state.value = oneDriveSignIn.currentAccountName()
                ?.let { SignInUiState.SignedIn(it) }
                ?: SignInUiState.SignedOut
        }
    }

    fun signIn(activity: Activity) {
        viewModelScope.launch {
            _state.value = SignInUiState.Working
            when (val result = oneDriveSignIn.signIn(activity)) {
                is SignInResult.Success -> _state.value = SignInUiState.SignedIn(result.accountName)

                // Backing out of the browser tab is not a failure, so show no error — just
                // return to whatever the account state actually was.
                SignInResult.Cancelled -> refresh()

                is SignInResult.Failed -> _state.value = SignInUiState.Error(result.errorCode)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _state.value = SignInUiState.Working
            oneDriveSignIn.signOut()
            // Refresh rather than assuming signed-out: if sign-out failed the account is still
            // there, and the screen should say so instead of lying.
            refresh()
        }
    }
}
