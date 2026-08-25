package com.gallery.sync.data.local.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gallery.sync.domain.backup.TreeScope
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.scopeDataStore: DataStore<Preferences> by preferencesDataStore(name = "media_scope")

/** One folder the user granted, as the UI needs to show it. */
data class GrantedDirectory(
    /** The tree URI, kept so the grant can be released when the folder is removed. */
    val treeUri: String,
    /** Path relative to the volume root, e.g. `DCIM/Camera`. Comparable to `RELATIVE_PATH`. */
    val relativePath: String
) {
    val displayName: String get() = TreeScope.displayNameOf(relativePath)
}

/**
 * The folders the app may read from and write into.
 *
 * ### Why the grant is persisted rather than re-requested
 *
 * `takePersistableUriPermission` survives reboots, which is what lets a background worker proxy a
 * photo without an Activity. Losing the grant would not merely be inconvenient — it would make
 * unattended optimising impossible, which is the mechanism v0.3 rests on.
 *
 * ### Removing a folder does not remove anything else
 *
 * Dropping a directory releases its permission and takes its albums out of the scan. It deletes no
 * ledger row and no album mode. Re-granting the same folder brings everything back with its history
 * intact and re-uploads nothing, because the ledger is keyed on content rather than on scope.
 */
@Singleton
class ScopedDirectories @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** Every granted folder, newest grant last. */
    val directories: Flow<List<GrantedDirectory>> =
        context.scopeDataStore.data.map { stored ->
            stored[KEY_TREES].orEmpty().mapNotNull { toDirectory(Uri.parse(it)) }
                .sortedBy { it.relativePath }
        }

    /** Just the paths, for [TreeScope.isInScope]. */
    val scope: Flow<List<String>> = directories.map { dirs -> dirs.map { it.relativePath } }

    suspend fun currentScope(): List<String> = scope.first()

    suspend fun current(): List<GrantedDirectory> = directories.first()

    /** Whether anything has been granted. The engine has nothing correct to do until it has. */
    suspend fun hasAny(): Boolean = current().isNotEmpty()

    /**
     * Records a folder the user picked, taking read and write permission that survives a reboot.
     *
     * Returns false when the tree is not usable as a scope — chiefly a whole-volume grant, which
     * would quietly re-include everything the user was narrowing away from.
     */
    suspend fun add(treeUri: Uri): Boolean {
        val directory = toDirectory(treeUri) ?: run {
            Logger.w(TAG, "refused a tree with no usable path")
            return false
        }

        val taken = runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }.isSuccess

        if (!taken) {
            Logger.e(TAG, "could not persist permission for the chosen folder")
            return false
        }

        context.scopeDataStore.edit { prefs ->
            prefs[KEY_TREES] = prefs[KEY_TREES].orEmpty() + directory.treeUri
        }
        Logger.i(TAG, "granted ${directory.relativePath}")
        return true
    }

    /**
     * Stops looking in a folder, and hands its permission back.
     *
     * Releasing matters: an app holding grants it no longer uses is holding write access to a
     * user's photos for no reason, and the count is capped by the system.
     */
    suspend fun remove(treeUri: String) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(treeUri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }.onFailure { Logger.w(TAG, "could not release a grant; dropping it anyway") }

        context.scopeDataStore.edit { prefs ->
            prefs[KEY_TREES] = prefs[KEY_TREES].orEmpty() - treeUri
        }
        Logger.i(TAG, "removed a granted folder")
    }

    /**
     * Drops any stored tree the system no longer honours.
     *
     * Grants can be revoked outside the app — storage unmounted, the folder deleted, the user
     * clearing permissions. A stale entry would leave the UI claiming a folder is being watched
     * while nothing in it is readable, which is worse than showing one fewer row.
     */
    suspend fun forgetRevokedGrants() {
        val held = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .mapTo(HashSet()) { it.uri.toString() }

        context.scopeDataStore.edit { prefs ->
            val stored = prefs[KEY_TREES].orEmpty()
            val surviving = stored.intersect(held)
            if (surviving.size != stored.size) {
                Logger.w(TAG, "${stored.size - surviving.size} granted folders are no longer held")
                prefs[KEY_TREES] = surviving
            }
        }
    }

    private fun toDirectory(treeUri: Uri): GrantedDirectory? {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return null
        val path = TreeScope.pathFromTreeDocumentId(documentId) ?: return null
        return GrantedDirectory(treeUri = treeUri.toString(), relativePath = path)
    }

    private companion object {
        const val TAG = "ScopedDirs"
        val KEY_TREES = stringSetPreferencesKey("granted_tree_uris")
    }
}
