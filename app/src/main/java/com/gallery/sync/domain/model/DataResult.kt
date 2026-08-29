package com.gallery.sync.domain.model

/**
 * The outcome of a repository call: either a value or a typed [RemoteError].
 *
 * Repositories return this instead of throwing so that callers — ViewModels, workers, the
 * ContentProvider — handle remote failure as data rather than as control flow.
 */
sealed interface DataResult<out T> {

    data class Success<T>(val value: T) : DataResult<T>

    data class Failure(val error: RemoteError) : DataResult<Nothing>
}

/** Why a remote call did not produce a value. */
sealed interface RemoteError {

    /** The provider rejected the token we sent (HTTP 401): missing, expired, or revoked. */
    data object Unauthorized : RemoteError

    /** No token was available at all, so no network call was attempted. */
    data object NoToken : RemoteError

    /** Transport-level failure — offline, DNS, timeout, socket reset. */
    data object Network : RemoteError

    /**
     * The local file is gone: deleted, moved, or on a card that was unmounted.
     *
     * Kept apart from [Network] because both arrive as an `IOException` and the difference
     * decides whether a whole run should stop. A missing file affects exactly one item and the
     * run should continue; reporting it as a lost connection is both wrong and alarming.
     */
    data object LocalFileMissing : RemoteError

    /**
     * The local file is there but reads as zero bytes, so nothing was uploaded.
     *
     * Kept apart from [LocalFileMissing] because the two want opposite handling. A missing file is
     * never coming back, so its row is forgotten. A zero-length read almost always is transient — a
     * file mid-write, mid-proxy, or just trashed — so the row is kept and simply tried again.
     *
     * It exists at all because sending those zero bytes was the alternative, and that writes an
     * empty file to the drive under the photo's name. `conflictBehavior` is `rename`, correctly, so
     * no later upload can repair it: the name stays occupied by an empty file for good. See
     * `UploadOutcome.EmptySource`.
     */
    data object EmptyLocalFile : RemoteError

    /**
     * The drive is full, so the write could not be stored.
     *
     * Separated from [Http] because it is the one remote failure the user can actually fix, and
     * "your OneDrive is full" is a far better message than "error 507".
     */
    data object InsufficientStorage : RemoteError

    /** Any other non-2xx response. [body] is the raw error payload, when the provider sent one. */
    data class Http(val code: Int, val body: String?) : RemoteError

    /** Anything not covered above. */
    data class Unknown(val cause: Throwable) : RemoteError
}
