package com.gallery.sync.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.gallery.sync.data.local.converter.MediaSourceConverter
import com.gallery.sync.data.local.dao.MediaFolderDao
import com.gallery.sync.data.local.dao.MediaItemDao
import com.gallery.sync.data.local.entity.MediaFolderEntity
import com.gallery.sync.data.local.entity.MediaItemEntity

/**
 * The local index of cloud-hosted media — a cache, never the source of truth.
 *
 * Version 1, initial creation, so no migration exists yet. Any later change to a shipped schema
 * requires a written migration and is an escalation, not a destructive rebuild.
 */
@Database(
    entities = [MediaItemEntity::class, MediaFolderEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(MediaSourceConverter::class)
abstract class GallerySyncDatabase : RoomDatabase() {

    abstract fun mediaItemDao(): MediaItemDao

    abstract fun mediaFolderDao(): MediaFolderDao

    companion object {
        const val DATABASE_NAME = "gallery_sync.db"
    }
}
