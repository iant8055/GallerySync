package com.gallery.sync.data.local.converter

import androidx.room.TypeConverter
import com.gallery.sync.data.local.entity.BackupState

/**
 * Stores [BackupState] as its name.
 *
 * The name rather than the ordinal, deliberately: ordinals shift when a constant is inserted into
 * the middle of an enum, silently reinterpreting every existing row. Storing the name means a
 * reordering is harmless and an unrecognised value is visible rather than quietly wrong.
 */
class BackupStateConverter {

    @TypeConverter
    fun fromBackupState(state: BackupState): String = state.name

    @TypeConverter
    fun toBackupState(value: String): BackupState =
        runCatching { BackupState.valueOf(value) }.getOrDefault(BackupState.PENDING)
}
