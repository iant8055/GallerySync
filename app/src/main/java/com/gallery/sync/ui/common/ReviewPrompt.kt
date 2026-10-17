package com.gallery.sync.ui.common

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.gallery.sync.util.Logger
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.launch

/**
 * Google Play's own rating sheet, shown over the app. Ian, 17 Oct 2026: asked once, after Finish ends setup, the moment
 * the user has just watched a backup checked and finished, and only once the New Albums pop-up that follows setup has
 * been cleared ("wait until it is cleared"), so the two never arrive together.
 *
 * Finish marks it [pending]; the Albums tab asks once nothing else is on screen. Held in memory only: if the app is
 * closed before then, the chance passes, which is fine for a request Play may decline anyway.
 *
 * Play decides whether the sheet actually appears (it limits how often any app may show it) and never says whether
 * the user rated, so nothing here depends on the outcome and nothing is offered in return, as Play's policy requires.
 * On a build not installed from Play it does nothing. It is never allowed to fail or delay anything: the Settings
 * card "Rate us on Play Store" stays for anyone who wants it later.
 */
object ReviewPrompt {

    private const val TAG = "ReviewPrompt"

    /** Set by Finish; cleared the moment the sheet is asked for. */
    var pending by mutableStateOf(false)

    fun askIfPending(activity: ComponentActivity) {
        if (!pending) return
        pending = false
        activity.lifecycleScope.launch {
            runCatching {
                val manager = ReviewManagerFactory.create(activity)
                val info = manager.requestReview()
                manager.launchReview(activity, info)
            }.onFailure { Logger.w(TAG, "review sheet not shown: ${it.javaClass.simpleName}") }
        }
    }
}
