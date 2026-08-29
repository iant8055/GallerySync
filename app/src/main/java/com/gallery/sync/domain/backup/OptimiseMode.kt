package com.gallery.sync.domain.backup

/**
 * Whether optimising happens on its own, or only when the user asks.
 *
 * Asked for by Ian, 28 Aug 2026, as the middle question of the Settings section: *"How do you want
 * your photos Optimized — Auto / Manually."*
 *
 * ### Auto is genuinely unattended, which it did not used to be
 *
 * Worth stating because the app's own copy said otherwise until today. `settings_auto_optimise_on`
 * still reads *"Android still asks you to confirm each batch — it does not allow this to happen
 * unattended"*, which stopped being true on 26 Aug 2026 when the proxy write moved to the persisted
 * SAF tree grant. `ProxyApplier` keeps both routes and picks by `safWriter.covers(paths)`: inside a
 * granted tree it writes with no dialog, outside one it falls back to `createWriteRequest` and a
 * per-batch tap.
 *
 * So **Auto means unattended for folders the user granted at setup**, which is the ordinary case,
 * and means "asks per batch" for anything outside them. A screen offering Auto has to be able to
 * say which, rather than promising the better of the two and delivering the other.
 */
enum class OptimiseMode {

    /**
     * The app optimises eligible files on its own, once they pass the age threshold.
     *
     * Still bounded by everything else: Sync albums only, verified in OneDrive only, and never a
     * file this phone cannot decode.
     */
    Auto,

    /**
     * Nothing happens until the user presses the button.
     *
     * Not the same as switching the feature off — it keeps the candidate list current and the
     * saving visible, so someone can reclaim space at a moment of their choosing rather than
     * discovering it happened.
     */
    Manual;

    companion object {

        /**
         * [Auto].
         *
         * The feature is off entirely until the master switch is turned on, and someone who has just
         * turned on "optimise my photos and video to save space" has asked for the thing to happen.
         * Defaulting to Manual there would answer a question they did not ask.
         */
        val DEFAULT = Auto

        fun fromNameOrDefault(name: String?): OptimiseMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
