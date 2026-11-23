package com.gallery.sync.domain.backup

import com.gallery.sync.data.local.entity.BackupEntryEntity
import com.gallery.sync.data.local.entity.BackupState
import java.time.Instant

/**
 * How old a Camera file must be for the header's age to select it.
 *
 * Since 14 Nov 2026 the same two menus as every other age in the app ([ArchiveAge]: ½ to 9 and hour to year,
 * or All), on the Camera screen and in Settings (Ian: "anywhere where a time limit can be set should get the
 * same functionality"). Until then Ian's list of 20 Sept 2026: 1 day, 1 week, 1 month, 6 months, 1 year, All.
 * This object keeps only the default and the names that list saved. 6 months was 182 days and reads as
 * 6 months of 30, 180: two days younger, on a choice made with the list in view before anything is done.
 */
object CameraOptimizeAge {

    /** Every file, whatever its age (Ian, 20 Sept 2026). The user's own choice on this screen. */
    val All = ArchiveAge.All

    /** All, out of the box (Ian, 27 Sept 2026). With Photos and Videos both off it still selects nothing. */
    val DEFAULT = All

    private val OLD_NAMES = mapOf(
        "OneDay" to ArchiveAge(1, ArchiveAgeUnit.Day),
        "OneWeek" to ArchiveAge(1, ArchiveAgeUnit.Week),
        "OneMonth" to ArchiveAge(1, ArchiveAgeUnit.Month),
        "SixMonths" to ArchiveAge(6, ArchiveAgeUnit.Month),
        "OneYear" to ArchiveAge(1, ArchiveAgeUnit.Year)
    )

    fun fromNameOrDefault(name: String?): ArchiveAge =
        name?.let { OLD_NAMES[it] ?: ArchiveAge.parse(it) } ?: DEFAULT
}

/**
 * The three choices on the Camera screen's header: how old, and which kinds.
 *
 * They start at the Camera defaults in Settings and can be changed on the screen for that visit. Leaving with a
 * choice that differs from the defaults asks whether to make it the default (Ian, 27 Sept 2026); nothing
 * else writes them back.
 */
data class CameraOptimizeChoice(
    val age: ArchiveAge,
    val photos: Boolean,
    val videos: Boolean
) {
    /** Neither kind is chosen, so the header selects nothing. The list still shows every file. */
    val nothingChosen: Boolean get() = !photos && !videos

    companion object {
        /** Settings' defaults out of the box: both kinds off, age All. Ian, 27 Sept 2026. */
        val DEFAULT = CameraOptimizeChoice(CameraOptimizeAge.DEFAULT, photos = false, videos = false)
    }
}

/**
 * Which files in the camera folder *Sync now* will optimize.
 *
 * ### What is listed and what is selected are separate (Ian, 27 Sept 2026)
 *
 * The screen lists **every** file in the folder, always; nothing on the header or in Settings hides one.
 * The header's age, Photos and Videos *select* files, highlighted as on the Restore tab, and the person
 * can then swipe any file right to select it or left to deselect it. Changing a header choice re-selects
 * by the new choice. The selection lasts for the visit and is not stored; it is what *Sync now* acts on.
 *
 * ### Which files can be selected
 *
 * The same bar as every rewrite in this app: uploaded **and** OneDrive reported the size it has here, so the
 * full-quality original is safe before the local copy shrinks; not already smaller, not already declined,
 * still on the phone. Anything else is listed, greyed, with its usual marks, and cannot be selected.
 *
 * ### What the header selects
 *
 * Every selectable file old enough for the age whose kind is chosen, **except** a file kept at full size
 * ([FilePin], set by Restore): that one starts deselected. Swiping it in selects it for this run, and
 * *Sync now* then clears its pin, so the pin never disagrees with what was done.
 */
object CameraSelection {

    /** Whether [entry] can be optimized at all. Not selectable means listed, greyed, and never acted on. */
    fun isSelectable(entry: BackupEntryEntity): Boolean =
        entry.state == BackupState.UPLOADED &&
            entry.remoteSizeBytes != null &&
            entry.remoteSizeBytes == entry.sizeBytes &&
            !entry.isProxied &&
            !entry.isProxySkipped &&
            !FileSkip.isSkipped(entry.modeOverride) &&
            entry.localMissingSinceEpochMillis == null

    /** Whether the header's [choice], with its age turned into [takenBeforeEpochSeconds], selects [entry]. */
    fun matches(entry: BackupEntryEntity, choice: CameraOptimizeChoice, takenBeforeEpochSeconds: Long): Boolean =
        isSelectable(entry) &&
            !FilePin.isPinned(entry.modeOverride) &&
            (if (entry.isVideo) choice.videos else choice.photos) &&
            entry.dateForSorting <= takenBeforeEpochSeconds

    /** What the header selects: the starting selection, and the one a changed choice replaces it with. */
    fun byChoice(
        entries: List<BackupEntryEntity>,
        choice: CameraOptimizeChoice,
        takenBeforeEpochSeconds: Long
    ): Set<String> =
        entries.filter { matches(it, choice, takenBeforeEpochSeconds) }.mapTo(LinkedHashSet()) { it.id }

    /**
     * The files a run will optimize: those in [selectedIds] that can still be optimized, largest first. The only
     * list the worker acts on. A file that stopped being selectable since it was selected (done, gone, declined)
     * drops out here; nothing outside [selectedIds] can get in.
     */
    fun toOptimize(entries: List<BackupEntryEntity>, selectedIds: Set<String>): List<BackupEntryEntity> =
        entries.filter { it.id in selectedIds && isSelectable(it) }.sortedByDescending { it.sizeBytes }

    /**
     * What optimizing [files] is expected to give back, in bytes.
     *
     * An estimate and labelled as one wherever it is drawn. It is the size times the measured saving for that
     * kind, so it over-promises for a photo that is already small (skipped, no saving) and for a clip that will
     * not shrink. Both are examined only when the work runs.
     */
    fun estimatedSavedBytes(files: List<BackupEntryEntity>, photoSavingPercent: Int, videoSavingPercent: Int): Long =
        files.sumOf { it.sizeBytes * (if (it.isVideo) videoSavingPercent else photoSavingPercent) / 100 }
}
