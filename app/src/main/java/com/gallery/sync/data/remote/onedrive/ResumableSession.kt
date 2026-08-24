package com.gallery.sync.data.remote.onedrive

/**
 * A Graph upload session that can outlive the run that created it.
 *
 * `createUploadSession` returns a pre-authorised URL valid for roughly five hours. Holding onto it
 * is what makes a large upload survive being interrupted — without it, the next run opens a fresh
 * session and Graph starts counting from zero again.
 *
 * Observed on the Fold 4, 19 Aug 2026: a run force-stopped at 157,286,400 of 163,846,425 bytes (96%)
 * restarted at byte zero, with the ledger row still `PENDING` and `attemptCount = 0`. At the ~3 MB/s
 * measured there a ten-minute window covers about 1.8 GB, but the ceiling scales with upstream, not
 * with file size: at 2 Mbps the same window covers ~150 MB, and that video could never complete.
 *
 * Deliberately a plain data class with no Retrofit or OkHttp types, so it can cross the repository
 * boundary without dragging the network layer with it.
 */
data class ResumableSession(

    /** Pre-authorised. Carries its own credentials, so no `Authorization` header may be attached. */
    val uploadUrl: String,

    /**
     * Epoch millis, or null when Graph did not say.
     *
     * Null means "unknown", never "expired": the session status request is authoritative, and
     * guessing an expiry we were not told would throw away sessions that are still good.
     */
    val expiresAtEpochMillis: Long?
) {
    /**
     * Whether this is known to be past its expiry.
     *
     * False when the expiry is unknown — the caller then asks the server rather than assuming.
     */
    fun hasExpired(nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        expiresAtEpochMillis != null && expiresAtEpochMillis <= nowEpochMillis
}
