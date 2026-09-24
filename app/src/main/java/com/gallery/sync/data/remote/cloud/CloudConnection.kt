package com.gallery.sync.data.remote.cloud

import android.app.Activity
import androidx.annotation.StringRes
import com.gallery.sync.data.remote.auth.SignInResult
import com.gallery.sync.domain.backup.BackupLocation

/** How the user connects a provider: a browser sign-in, or by pasting keys the provider issued. */
enum class ConnectionKind { OAUTH, ACCESS_KEYS }

/** One box in the "paste your keys" form. [isSecret] fields are drawn hidden and never logged. */
data class KeyField(
    val id: String,
    @StringRes val labelRes: Int,
    val isSecret: Boolean = false,
    val defaultValue: String = "",
    /** Faint example text shown inside the empty box, so the label itself can stay short. */
    @StringRes val hintRes: Int? = null
)

/**
 * How one cloud is connected and disconnected — TASK-026, all the optional clouds at once.
 *
 * The screens list these rather than knowing any provider by name, so a new provider is one adapter
 * and one binding. OneDrive is not one of them: it is the app's base and keeps its own sign-in.
 */
interface CloudConnection {

    val location: BackupLocation

    val kind: ConnectionKind

    /**
     * Whether this build can offer the provider at all. False when the app registration it needs
     * (a client id from the provider's developer console) has not been filled in: showing a Connect
     * button that cannot succeed is exactly the "action that cannot succeed" TASK-014 rules out.
     */
    val isOfferedInThisBuild: Boolean

    /** A label for the connected account, or null when not connected. */
    suspend fun accountLabel(): String?

    /** Browser sign-in, for [ConnectionKind.OAUTH]. Others return `Failed("not_oauth")`. */
    suspend fun signIn(activity: Activity): SignInResult = SignInResult.Failed("not_oauth")

    /** The boxes for [ConnectionKind.ACCESS_KEYS]. */
    val keyFields: List<KeyField> get() = emptyList()

    /**
     * Checks [values] against the provider and stores them only if they work. A wrong key must fail
     * here, in front of the user, rather than at 3 a.m. in the middle of a backup.
     */
    suspend fun connectWithKeys(values: Map<String, String>): SignInResult = SignInResult.Failed("not_keys")

    /** Forgets the stored credentials. Always succeeds; there is no server-side session to end. */
    suspend fun signOut()
}

/** Every optional cloud this build knows about. Injected as a set. */
class CloudConnections(private val all: Set<CloudConnection>) {

    /** In a stable, display order. */
    fun offered(): List<CloudConnection> =
        all.filter { it.isOfferedInThisBuild }.sortedBy { it.location.ordinal }

    fun of(location: BackupLocation): CloudConnection? = all.firstOrNull { it.location == location }
}
