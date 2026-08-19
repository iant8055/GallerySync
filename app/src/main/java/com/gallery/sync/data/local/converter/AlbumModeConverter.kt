package com.gallery.sync.data.local.converter

import androidx.room.TypeConverter
import com.gallery.sync.data.local.entity.AlbumMode

/**
 * Stores [AlbumMode] as its name, following [BackupStateConverter].
 *
 * The name rather than the ordinal: a constant inserted into the middle of the enum would otherwise
 * reinterpret every existing row, and here that could turn a `BACKUP` album into an `ARCHIVE` one —
 * which removes files from the phone.
 *
 * An unreadable value falls back to [AlbumMode.DEFAULT], the mode that touches nothing locally.
 * Falling back rather than throwing keeps a single corrupt row from breaking the album list, and
 * the direction of the fallback is what matters: the safe end of the ladder, never the destructive
 * one.
 */
class AlbumModeConverter {

    @TypeConverter
    fun fromAlbumMode(mode: AlbumMode): String = mode.name

    @TypeConverter
    fun toAlbumMode(value: String): AlbumMode =
        runCatching { AlbumMode.valueOf(value) }.getOrDefault(AlbumMode.DEFAULT)
}
