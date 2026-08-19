package com.gallery.sync.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.gallery.sync.data.local.converter.AlbumModeConverter
import com.gallery.sync.data.local.converter.BackupStateConverter
import com.gallery.sync.data.local.converter.MediaSourceConverter
import com.gallery.sync.data.local.dao.AlbumPreferenceDao
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.MediaFolderDao
import com.gallery.sync.data.local.dao.MediaItemDao
import com.gallery.sync.data.local.entity.AlbumPreferenceEntity
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.MediaFolderEntity
import com.gallery.sync.data.local.entity.MediaItemEntity

/**
 * The local index of cloud-hosted media, and the backup ledger.
 *
 * The media index is a cache and never the source of truth. The **ledger is different**: it is the
 * only record of which local files have reached OneDrive. Losing it does not just cost a rebuild —
 * it would make the app re-upload an entire library, or believe files are safe that were never
 * sent. Schema changes ship a real migration; see [Migrations].
 *
 * Version 2 adds `backup_entries` and `album_preferences`. Version 3 records photo proxies.
 * Version 4 replaces the album on/off flag with a four-valued mode.
 */
@Database(
    entities = [
        MediaItemEntity::class,
        MediaFolderEntity::class,
        BackupEntryEntity::class,
        AlbumPreferenceEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(MediaSourceConverter::class, BackupStateConverter::class, AlbumModeConverter::class)
abstract class GallerySyncDatabase : RoomDatabase() {

    abstract fun mediaItemDao(): MediaItemDao

    abstract fun mediaFolderDao(): MediaFolderDao

    abstract fun backupEntryDao(): BackupEntryDao

    abstract fun albumPreferenceDao(): AlbumPreferenceDao

    companion object {
        const val DATABASE_NAME = "gallery_sync.db"
    }
}
