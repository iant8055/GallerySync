package com.gallery.sync.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * [content] with its text allowed to grow with the phone's text size only up to [max] times (Ian, 24 Oct 2026). Used for
 * the coloured card at the top of each tab, which on a narrow screen with the largest text grew to most of the screen.
 * Everything else in the app follows the phone's text size fully: that setting is how people who need large text read.
 * Below [max] nothing changes at all.
 */
@Composable
fun CappedTextSize(max: Float = HeaderTextScaleCap, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    if (density.fontScale <= max) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides Density(density.density, max)) { content() }
    }
}

/** How far the header cards' text may grow: noticeably larger, without taking over the screen. */
const val HeaderTextScaleCap = 1.3f
