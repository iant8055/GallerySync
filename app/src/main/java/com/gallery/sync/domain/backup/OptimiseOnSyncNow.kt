package com.gallery.sync.domain.backup

/**
 * Whether **Sync now** has optimising to do, so the button can be pressed when nothing needs sending.
 *
 * Ian, 19 Sept 2026: Manual optimising happens *"through the Sync Now button on the Albums tab"*. That
 * button was only ever enabled while files were waiting to upload, so a Manual album whose files were
 * all backed up already had nothing to press. It is enabled here too, whenever a kind set to Manual
 * has something ready.
 */
object OptimiseOnSyncNow {

    fun isWaiting(
        setupComplete: Boolean,
        optimiseEnabled: Boolean,
        optimisePhotos: Boolean,
        photoMode: OptimiseMode,
        optimiseVideo: Boolean,
        videoMode: OptimiseMode,
        photosReady: Int,
        videoReady: Int
    ): Boolean =
        (PhotoOptimisePolicy.runsOnSyncNow(setupComplete, optimiseEnabled, optimisePhotos, photoMode) && photosReady > 0) ||
            (VideoOptimisePolicy.runsOnSyncNow(setupComplete, optimiseEnabled, optimiseVideo, videoMode) && videoReady > 0)
}
