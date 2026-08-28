package com.gallery.sync.domain.setup

import androidx.annotation.StringRes
import com.gallery.sync.R

/**
 * The things a user has to be told before the app's behaviour makes sense.
 *
 * One topic exists exactly once, here, and is read by three places: the first-run bubbles, the Help
 * screen, and the just-in-time prompt shown at first use when the tour was skipped. Three separate
 * descriptions of one irreversible operation drift apart, and the drift is invisible until someone
 * reads two of them side by side.
 *
 * [key] is what gets written to the acknowledgement record, so it must stay stable. Renaming an enum
 * constant is free; changing its key silently un-acknowledges the topic for every existing install.
 */
enum class SetupTopic(
    val key: String,
    @StringRes val title: Int,
    @StringRes val body: Int,
    /**
     * The label that advances past this topic.
     *
     * Null means a plain Next. A non-null value names the consequence being acknowledged, and is
     * deliberately not the bare "I understand" — a generic button can be pressed without the
     * sentence above it entering the decision, which is the fatigue pattern the record exists to
     * avoid. Only topics describing a file leaving the phone or being rewritten carry one; a button
     * that appears everywhere carries no weight, exactly as the Archive confirmation carries weight
     * by being rare.
     */
    @StringRes val acknowledgeLabel: Int? = null
) {
    WHAT_THIS_IS("what_this_is", R.string.topic_what_title, R.string.topic_what_body),

    FOLDERS("folders", R.string.topic_folders_title, R.string.topic_folders_body),

    MODES(
        "modes",
        R.string.topic_modes_title,
        R.string.topic_modes_body,
        R.string.topic_modes_ack
    ),

    ARCHIVE(
        "archive",
        R.string.topic_archive_title,
        R.string.topic_archive_body,
        R.string.topic_archive_ack
    ),

    PROMISE("promise", R.string.topic_promise_title, R.string.topic_promise_body),

    GETTING_BACK("getting_back", R.string.topic_getting_back_title, R.string.topic_getting_back_body),

    OPTIMISING(
        "optimising",
        R.string.topic_optimising_title,
        R.string.topic_optimising_body,
        R.string.topic_optimising_ack
    ),

    DELETING("deleting", R.string.topic_deleting_title, R.string.topic_deleting_body),

    EMPTYING_TRASH("emptying_trash", R.string.topic_trash_title, R.string.topic_trash_body),

    WHEN_THINGS_HAPPEN("when_things_happen", R.string.topic_when_title, R.string.topic_when_body);

    val requiresAcknowledgement: Boolean get() = acknowledgeLabel != null

    companion object {
        /**
         * Topics whose explanation must be on file before a destructive mode can be chosen.
         *
         * Acknowledging one of these is *not* consent to anything — that is what the Archive
         * confirmation is for, and the two must never be collapsed. This is only "the explanation
         * has been put in front of you", which is the presentation half of informed consent.
         */
        val gatingDestructiveModes = setOf(ARCHIVE)
    }
}
