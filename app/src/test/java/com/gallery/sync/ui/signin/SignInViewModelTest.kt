package com.gallery.sync.ui.signin

import android.app.Activity
import com.gallery.sync.data.remote.auth.OneDriveSignIn
import com.gallery.sync.data.remote.auth.SignInResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Unit tests for [SignInViewModel].
 *
 * Driven through a fake [OneDriveSignIn] rather than MSAL, whose types cannot be constructed off
 * device. The behaviour worth pinning is that cancellation is not treated as an error, and that
 * a failed sign-out does not leave the screen claiming the user is signed out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    private val activity: Activity = mock()

    @Before
    fun setUp() {
        // viewModelScope posts to Dispatchers.Main, which does not exist in a JVM unit test.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts signed out when no account is cached`() = runTest {
        val viewModel = SignInViewModel(FakeSignIn(accountName = null))

        assertEquals(SignInUiState.SignedOut, viewModel.state.value)
    }

    @Test
    fun `starts signed in when an account is already cached`() = runTest {
        val viewModel = SignInViewModel(FakeSignIn(accountName = "ian@example.com"))

        assertEquals(SignInUiState.SignedIn("ian@example.com"), viewModel.state.value)
    }

    @Test
    fun `a successful sign-in reports the account name`() = runTest {
        val fake = FakeSignIn(accountName = null, signInResult = SignInResult.Success("ian@example.com"))
        val viewModel = SignInViewModel(fake)

        viewModel.signIn(activity)

        assertEquals(SignInUiState.SignedIn("ian@example.com"), viewModel.state.value)
    }

    @Test
    fun `cancelling sign-in is not an error and returns to signed out`() = runTest {
        val fake = FakeSignIn(accountName = null, signInResult = SignInResult.Cancelled)
        val viewModel = SignInViewModel(fake)

        viewModel.signIn(activity)

        assertEquals(SignInUiState.SignedOut, viewModel.state.value)
    }

    @Test
    fun `a failed sign-in surfaces the error code`() = runTest {
        val fake = FakeSignIn(accountName = null, signInResult = SignInResult.Failed("invalid_grant"))
        val viewModel = SignInViewModel(fake)

        viewModel.signIn(activity)

        assertEquals(SignInUiState.Error("invalid_grant"), viewModel.state.value)
    }

    @Test
    fun `signing out clears the account`() = runTest {
        val fake = FakeSignIn(accountName = "ian@example.com", signOutSucceeds = true)
        val viewModel = SignInViewModel(fake)

        viewModel.signOut()

        assertEquals(SignInUiState.SignedOut, viewModel.state.value)
    }

    @Test
    fun `a failed sign-out leaves the account shown as still signed in`() = runTest {
        // The screen must not claim the user is signed out while MSAL still holds the account.
        val fake = FakeSignIn(accountName = "ian@example.com", signOutSucceeds = false)
        val viewModel = SignInViewModel(fake)

        viewModel.signOut()

        assertEquals(SignInUiState.SignedIn("ian@example.com"), viewModel.state.value)
    }

    private class FakeSignIn(
        private var accountName: String?,
        private val signInResult: SignInResult = SignInResult.Cancelled,
        private val signOutSucceeds: Boolean = true
    ) : OneDriveSignIn {

        override suspend fun currentAccountName(): String? = accountName

        override suspend fun signIn(activity: Activity): SignInResult {
            if (signInResult is SignInResult.Success) accountName = signInResult.accountName
            return signInResult
        }

        override suspend fun signOut(): Boolean {
            if (signOutSucceeds) accountName = null
            return signOutSucceeds
        }
    }
}
