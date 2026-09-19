package com.gallery.sync.data.local

import android.content.Context
import com.gallery.sync.di.IoDispatcher
import com.gallery.sync.domain.backup.DriveListing
import com.gallery.sync.domain.backup.DriveListingCodec
import com.gallery.sync.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the Restore tab's OneDrive listing on disk, so a closed app does not lose it.
 *
 * A cache and nothing more: it lives in the app's cache directory, so the system or the user clearing
 * the cache costs one read of the drive and nothing else. Anything unreadable, or stored under a
 * different OneDrive folder from the one in use now, loads as "nothing stored".
 *
 * Cleared on sign-out, because a listing is one account's library and must not be shown to the next.
 */
@Singleton
class DriveListingStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val dispatcher: CoroutineDispatcher
) {

    /** What was on disk: the listing and when OneDrive was read to make it. */
    data class Stored(val listing: DriveListing, val listedAtMillis: Long)

    private val file: File get() = File(context.cacheDir, FILE_NAME)

    /** The stored listing, if there is one, it is readable, and it was read under [root]. */
    suspend fun load(root: String): Stored? = withContext(dispatcher) {
        runCatching {
            val target = file
            if (!target.exists()) return@runCatching null
            val decoded = DriveListingCodec.decode(target.readText()) ?: return@runCatching null
            if (decoded.root != root) return@runCatching null
            Stored(decoded.listing, decoded.listedAtMillis)
        }.onFailure { Logger.w(TAG, "could not read the stored listing: ${it.message}") }
            .getOrNull()
    }

    /** Written to a temporary file and renamed, so a run killed half way leaves the old file intact. */
    suspend fun save(listing: DriveListing, listedAtMillis: Long, root: String) = withContext(dispatcher) {
        runCatching {
            val temp = File(context.cacheDir, "$FILE_NAME.tmp")
            temp.writeText(DriveListingCodec.encode(listing, listedAtMillis, root))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        }.onFailure { Logger.w(TAG, "could not store the listing: ${it.message}") }
        Unit
    }

    suspend fun clear() = withContext(dispatcher) {
        runCatching { file.delete() }
        Unit
    }

    private companion object {
        const val TAG = "DriveListingStore"
        const val FILE_NAME = "restore-drive-listing.txt"
    }
}
