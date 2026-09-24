package com.gallery.sync.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.gallery.sync.data.local.converter.AlbumModeConverter
import com.gallery.sync.data.local.converter.BackupLocationConverter
import com.gallery.sync.data.local.converter.CloudCopyDecisionConverter
import com.gallery.sync.data.local.converter.BackupStateConverter
import com.gallery.sync.data.local.converter.MediaSourceConverter
import com.gallery.sync.data.local.dao.AlbumCloudStatusDao
import com.gallery.sync.data.local.dao.AlbumPreferenceDao
import com.gallery.sync.data.local.dao.BackupEntryDao
import com.gallery.sync.data.local.dao.FolderPreferenceDao
import com.gallery.sync.data.local.dao.MediaFolderDao
import com.gallery.sync.data.local.dao.MediaItemDao
import com.gallery.sync.data.local.dao.UnsentDepartureDao
import com.gallery.sync.data.local.entity.AlbumCloudStatusEntity
import com.gallery.sync.data.local.entity.AlbumPreferenceEntity
import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.FolderPreferenceEntity
import com.gallery.sync.data.local.entity.MediaFolderEntity
import com.gallery.sync.data.local.entity.MediaItemEntity
import com.gallery.sync.data.local.entity.UnsentDepartureEntity

/**
 * The local index of cloud-hosted media, and the backup ledger.
 *
 * The media index is a cache and never the source of truth. The **ledger is different**: it is the
 * only record of which local files have reached OneDrive. Losing it does not just cost a rebuild —
 * it would make the app re-upload an entire library, or believe files are safe that were never
 * sent. Schema changes ship a real migration; see [Migrations].
 *
 * Version 2 adds `backup_entries` and `album_preferences`. Version 3 records photo proxies.
 * Version 4 replaces the album on/off flag with a four-valued mode. Version 5 records files that
 * were examined and found not worth proxying. Version 12 gave each ledger row a
 * [com.gallery.sync.domain.backup.BackupLocation] recording where it was actually sent, and briefly
 * gave `album_preferences` one too. Version 13 removed the latter in favour of one app-wide setting
 * (`BackupPreferences.backupLocation`). Version 14 landed one day later at a granularity in between
 * the two: `folder_preferences`, one destination per top-level media folder (`DCIM`, `Pictures`...),
 * coarser than the per-album choice v13 removed, finer than v13's single app-wide setting, which
 * stays as the fallback for a folder with no row here. See TASK-026.
 */
@Database(
    entities = [
        MediaItemEntity::class,
        MediaFolderEntity::class,
        BackupEntryEntity::class,
        AlbumPreferenceEntity::class,
        AlbumCloudStatusEntity::class,
        UnsentDepartureEntity::class,
        FolderPreferenceEntity::class
    ],
    version = 14,
    exportSchema = true
)
@TypeConverters(
    MediaSourceConverter::class,
    BackupStateConverter::class,
    AlbumModeConverter::class,
    CloudCopyDecisionConverter::class,
    BackupLocationConverter::class
)
abstract class GallerySyncDatabase : RoomDatabase() {

    abstract fun mediaItemDao(): MediaItemDao

    abstract fun mediaFolderDao(): MediaFolderDao

    abstract fun backupEntryDao(): BackupEntryDao

    abstract fun albumPreferenceDao(): AlbumPreferenceDao

    abstract fun albumCloudStatusDao(): AlbumCloudStatusDao

    abstract fun unsentDepartureDao(): UnsentDepartureDao

    abstract fun folderPreferenceDao(): FolderPreferenceDao

    companion object {
        const val DATABASE_NAME = "gallery_sync.db"
    }
}
