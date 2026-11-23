package com.gallery.sync.domain.backup

/**
 * Whether **Sync now** has optimizing to do, so the button can be pressed when nothing needs sending.
 *
 * Ian, 19 Sept 2026: Manual optimizing happens *"through the Sync Now button on the Albums tab"*. That
 * button was only ever enabled while files were waiting to upload, so a Manual album whose files were
 * all backed up already had nothing to press. It is enabled here too, whenever a kind set to Manual
 * has something ready.
 */
object OptimizeOnSyncNow {

    fun isWaiting(
        setupComplete: Boolean,
        optimizeEnabled: Boolean,
        optimizePhotos: Boolean,
        photoMode: OptimizeMode,
        optimizeVideo: Boolean,
        videoMode: OptimizeMode,
        photosReady: Int,
        videoReady: Int
    ): Boolean =
        (PhotoOptimizePolicy.runsOnSyncNow(setupComplete, optimizeEnabled, optimizePhotos, photoMode) && photosReady > 0) ||
            (VideoOptimizePolicy.runsOnSyncNow(setupComplete, optimizeEnabled, optimizeVideo, videoMode) && videoReady > 0)
}
