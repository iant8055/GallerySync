package com.gallery.sync.domain.repository

import com.gallery.sync.domain.backup.BackupLocation

/** What a cloud said when asked about one stored file. */
sealed interface RemoteCheck {

    /** The file is there, and the cloud reports it at [sizeBytes]. */
    data class Present(val sizeBytes: Long) : RemoteCheck

    /** The cloud answered, and the file is not there: never uploaded, deleted, or (Drive) in the trash. */
    data object Gone : RemoteCheck

    /** The cloud could not be asked, or its answer said nothing about the file. Not evidence either way. */
    data object Unknown : RemoteCheck
}

/**
 * One cloud that can confirm, right now, that a file it holds is there and how big it is — TASK-027.
 *
 * This is the proof Archive needs before a file leaves the phone. It asks about **one object by the id the ledger
 * recorded at upload**, live, and never trusts a remembered size: a file deleted from the cloud by hand leaves a
 * ledger row insisting it is safe forever, and removal is the one operation where only "there is a copy now" will do.
 *
 * Three answers, and only one permits removal: [RemoteCheck.Present] at the expected size. [RemoteCheck.Unknown]
 * must be returned for anything that is not a definite yes or a definite no — a timeout, a refused token, an
 * error page — because the caller reads it as "we could not ask, so we do not remove".
 */
interface CloudVerifier {

    val location: BackupLocation

    suspend fun sizeOf(remoteItemId: String): RemoteCheck

    /**
     * Everything this Cloud holds in one album's folder, `<root>/<album>`, as **the id recorded at upload -> its size
     * in the Cloud**, for the Albums tab's per-Cloud "verified" line (Ian, 16 Nov 2026). One listing per album, so a
     * library is checked in a request or two per folder rather than one per file.
     *
     * Null when the Cloud could not be asked (no sign-in, a refused token, a network failure, a partial listing): never
     * read as "nothing is there". An empty map when it answered and the folder does not exist. Items in the Cloud's
     * trash are not listed. Display only: nothing writes ledger state from this.
     */
    suspend fun listAlbum(album: String, root: String): Map<String, Long>?
}

/** Finds the [CloudVerifier] for a location. Injected as a set so a new cloud is one binding. */
class CloudVerifiers(private val all: Set<CloudVerifier>) {

    fun of(location: BackupLocation): CloudVerifier? = all.firstOrNull { it.location == location }
}
