package com.gallery.sync.ui.common

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.gallery.sync.util.Logger
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.ktx.launchReview
import kotlinx.coroutines.launch

/**
 * Google Play's own rating sheet, shown over the app. Ian, 17 Oct 2026: asked once, when Finish ends setup, the
 * moment the user has just watched a backup checked and finished.
 *
 * Play decides whether the sheet actually appears (it limits how often any app may show it) and never says whether
 * the user rated, so nothing here depends on the outcome and nothing is offered in return, as Play's policy requires.
 * On a build not installed from Play it does nothing. It is never allowed to fail or delay anything: the Settings
 * card "Rate us on Play Store" stays for anyone who wants it later.
 */
object ReviewPrompt {

    private const val TAG = "ReviewPrompt"

    fun ask(activity: ComponentActivity) {
        activity.lifecycleScope.launch {
            runCatching {
                val manager = ReviewManagerFactory.create(activity)
                val info = manager.requestReview()
                manager.launchReview(activity, info)
            }.onFailure { Logger.w(TAG, "review sheet not shown: ${it.javaClass.simpleName}") }
        }
    }
}
