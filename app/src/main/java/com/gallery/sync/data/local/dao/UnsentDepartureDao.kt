package com.gallery.sync.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gallery.sync.data.local.entity.UnsentDepartureEntity

/** Files that left the phone unsent. See [UnsentDepartureEntity]. */
@Dao
interface UnsentDepartureDao {

    /** IGNORE, so a file noticed gone twice keeps the date it was first noticed. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(entries: List<UnsentDepartureEntity>)

    /** Oldest first, so the window lists them in the order they went. */
    @Query("SELECT * FROM unsent_departures ORDER BY goneSinceEpochMillis ASC, displayName ASC")
    suspend fun all(): List<UnsentDepartureEntity>

    @Query("SELECT COUNT(*) FROM unsent_departures")
    suspend fun count(): Int

    /** Bookkeeping only: nothing on the phone or in the drive is touched. */
    @Query("DELETE FROM unsent_departures WHERE id IN (:ids)")
    suspend fun forget(ids: List<String>): Int
}
