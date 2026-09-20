package com.gallery.sync.data.local.converter

import androidx.room.TypeConverter
import com.gallery.sync.data.local.entity.CloudCopyDecision

/**
 * Stores [CloudCopyDecision] as its name, following [AlbumModeConverter], and `null` as null.
 *
 * An unreadable value falls back to [CloudCopyDecision.KEPT], never to `null`: a corrupt row stops
 * a file being offered for removal rather than guessing that it may be.
 */
class CloudCopyDecisionConverter {

    @TypeConverter
    fun fromDecision(decision: CloudCopyDecision?): String? = decision?.name

    @TypeConverter
    fun toDecision(value: String?): CloudCopyDecision? =
        value?.let { runCatching { CloudCopyDecision.valueOf(it) }.getOrDefault(CloudCopyDecision.KEPT) }
}
