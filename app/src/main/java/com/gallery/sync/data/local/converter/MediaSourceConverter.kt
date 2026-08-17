package com.gallery.sync.data.local.converter

import androidx.room.TypeConverter
import com.gallery.sync.data.local.entity.MediaSource

/**
 * Stores [MediaSource] as its `name` string.
 *
 * An unrecognised string throws rather than defaulting to a provider: a silent default would let
 * index corruption masquerade as OneDrive data.
 */
class MediaSourceConverter {

    @TypeConverter
    fun fromMediaSource(source: MediaSource): String = source.name

    @TypeConverter
    fun toMediaSource(value: String): MediaSource =
        MediaSource.entries.firstOrNull { it.name == value }
            ?: throw IllegalArgumentException("Unknown MediaSource value: $value")
}
