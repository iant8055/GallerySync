package com.gallery.sync.domain.repository

import com.gallery.sync.domain.model.DataResult

/**
 * Read-only access to what **this app** has put in the user's Google Photos library.
 *
 * Unlike [OneDriveRepository], there is no folder tree to browse. The Photos Library API only ever
 * exposes content the calling app created (`photoslibrary.readonly.appcreateddata`) — never the
 * user's pre-existing library, never what Google's own auto-backup put there. See TASK-026: this is
 * a platform limit, not a scope GallerySync chose, and it is why Google Photos has no destination
 * picker, no skip-existing reconciliation against a library it cannot see, and no folder concept at
 * all — only albums, which this app does not yet create or use (v1 uploads to the general library).
 *
 * The one thing this interface exists for is dedup: what has GallerySync itself already sent to this
 * account, so a reinstall does not re-upload a library it already delivered once. `mediaItems.list`
 * is scoped to the account and the calling app, not the device, so this should survive a reinstall —
 * confirm that empirically once sign-in exists, the docs imply it but do not spell it out.
 */
interface GooglePhotosRepository {

    /**
     * Lists media items this app has created in the signed-in user's library, newest first.
     *
     * A page at a time — Google's own default and recommendation is 25 items per page, 100 maximum.
     * [pageToken] continues an earlier call; omit it to start from the beginning.
     */
    suspend fun listAppCreatedItems(pageToken: String? = null): DataResult<GooglePhotosPage>
}

/** One page of [GooglePhotosRepository.listAppCreatedItems]. */
data class GooglePhotosPage(
    val items: List<GooglePhotosMediaItem>,
    /** Present when there is another page; pass to the next call. Absent means this was the last. */
    val nextPageToken: String?
)

/**
 * One item as the Library API describes it.
 *
 * No size in bytes anywhere — see the interface doc and TASK-026. [id] is what a later
 * `mediaItems.batchCreate` response is matched back against for dedup; [filename] and
 * [creationTimeEpochSeconds] are what this app has to reconcile against the local ledger with
 * instead of a byte-exact match, since Google gives us nothing stronger.
 */
data class GooglePhotosMediaItem(
    val id: String,
    val filename: String,
    val creationTimeEpochSeconds: Long?
)
