package com.gallery.sync.domain.backup

/**
 * How hard to shrink video, chosen by the user.
 *
 * Asked for by Ian, 28 Aug 2026, after watching all four outputs of the resolution sweep on the
 * Fold's inner display. The three points are his; the savings are measured rather than estimated.
 *
 * ### The numbers are real, and they are one clip's
 *
 * Measured on an 18-second daylight clip, 1080x1920, 38.4 MB at ~17 Mbps — an ordinary phone
 * recording rather than a flattering one. The first sweep used a dark, sped-up fireworks clip and
 * overstated every figure by up to 24 points, which is why this comment names the sample.
 *
 * | | Short side | Measured | Ian's figure |
 * |---|---|---|---|
 * | [High] | 480 | 88% | ~85% |
 * | [Medium] | 720 | 73% | ~75% |
 * | [Low] | 1080 | 47% | ~50% |
 *
 * **Re-measured on five real clips, Moto G, 4 Sept 2026**, and the single-clip sweep had flattered
 * [High]. Five clips at [High] returned 84.5% against the 90% then claimed — over-promising, which
 * is the direction that matters, so High is now 85. Three clips at [Medium] returned 75.4, 75.7 and
 * 76.3%: its 75 needed nothing, the figure it already carried was right. [Low] is still one clip's
 * measurement and untested at scale.
 *
 * **[Low] does not downscale at all.** A 1080p clip comes back the same size in pixels and about
 * half the size in bytes, because the saving there is bitrate rather than resolution — the source
 * was ~17 Mbps and the re-encode lands far below it. It is the honest floor of the feature and the
 * one setting that cannot be accused of degrading the picture's shape.
 *
 * ### On the naming, which Ian flagged before a line of UI existed
 *
 * *"We'll have to be careful how or what we label this as."* He is right: on its own "High" is
 * ambiguous, because it means *saves the most* and therefore *the lowest quality*, and a settings
 * screen is exactly where a word ends up on its own.
 *
 * **What disambiguates it is the heading, not the word.** Under *"How much to shrink video"*, High
 * can only mean high shrinking; the axis is named before the options are read. Ian's call, 28 Aug
 * 2026, and the right one — High/Medium/Low is what people already understand, and the fix is to
 * stop the reader having to guess the axis rather than to rename the levels.
 *
 * Each option still carries its outcome and its saving, so no option is ever a bare adjective. It is
 * the 18 Aug naming rule in a third costume: that guarded against a soft name for a hard action,
 * the Archive copy guarded against a hard-sounding promise over a soft result, and this guards
 * against a word whose direction the reader has to infer.
 *
 * [approximateSavingPercent] exists so no screen invents its own number and the two cannot drift.
 */
enum class VideoQuality(
    /**
     * Target for the **short** edge, so portrait and landscape are treated alike.
     *
     * A phone clip is usually stored landscape with a rotation flag, and scaling the long edge would
     * shrink the wrong axis on everything shot upright — which is most video.
     */
    val targetShortSide: Int,

    /** What it saved on the measured clip. See the table above; it varies with content. */
    val approximateSavingPercent: Int
) {

    /** 480p. The most aggressive, and indistinguishable from the source on a 7.6-inch display. */
    High(targetShortSide = 480, approximateSavingPercent = 85),

    /** 720p. */
    Medium(targetShortSide = 720, approximateSavingPercent = 75),

    /** 1080p — a re-encode rather than a downscale. Full resolution kept. */
    Low(targetShortSide = 1080, approximateSavingPercent = 50);

    companion object {

        /**
         * [High], on the evidence.
         *
         * Ian, having compared all four outputs on the Fold 4's inner display — 2176x1812, where
         * 480p upscales about 2.5x: *"even the 480p at 4.77MB is a good looking clip — I can't tell
         * the difference in the quality between them."* Defaulting to a weaker setting would spend
         * most of the feature's value to avoid a difference nobody could see on the hardest screen
         * available.
         *
         * The user can still choose otherwise, which is the point of the setting.
         */
        val DEFAULT = High

        fun fromNameOrDefault(name: String?): VideoQuality =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
