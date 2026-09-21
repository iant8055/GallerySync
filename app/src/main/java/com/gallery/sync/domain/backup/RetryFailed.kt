package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.AlbumMode

/**
 * When an album offers *Retry failed*.
 *
 * A file that fails five times is marked failed and the queue stops picking it (`BackupEngine.MAX_ATTEMPTS`),
 * so without a way back it would sit unsent for good. Until 21 Sept 2026 the way back existed in the database
 * (`resetFailures`) and nothing called it.
 *
 * Offered only where the album is being backed up at all. An Off album sends nothing, so putting its files
 * back in the queue would change nothing but the count.
 *
 * Retrying only ever adds work: it puts files back in the queue to be uploaded. It removes nothing from the
 * phone or from OneDrive.
 */
object RetryFailed {
    fun offered(mode: AlbumMode, failed: Int): Boolean = mode != AlbumMode.OFF && failed > 0
}
