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

    /** Any other non-2xx response. [body] is the raw error payload, when the provider sent one. */
    data class Http(val code: Int, val body: String?) : RemoteError

    /** Anything not covered above. */
    data class Unknown(val cause: Throwable) : RemoteError
}
