package com.gallery.sync.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gallery.sync.R
import androidx.compose.ui.res.stringResource

/**
 * Which tab headers have their details hidden (Ian, 25 Oct 2026: "the less screen real estate we take up with the
 * Header the better"). Each of Albums, Restore and Archive, and an album's own page, has a **▲ Hide Details ▲** line
 * across the bottom of its coloured card that folds away everything but the title, the number, the main button and
 * whatever is happening right now; it becomes **▼ Show Details ▼**. Remembered per tab across launches, in
 * `AppearanceSettings`, and provided once by `MainActivity`.
 *
 * Nothing that asks the user something is ever behind it: Archive's question and its Yes/No sit below the card, and a
 * result or a failure stays on the visible side.
 */
class HeaderDetails(
    private val hidden: Set<String>,
    private val onSet: (key: String, shown: Boolean) -> Unit
) {
    fun shown(key: String): Boolean = key !in hidden
    fun set(key: String, shown: Boolean) = onSet(key, shown)
}

/** Every header shows its details unless something provides otherwise, so the setup tour's pictures are unaffected. */
val LocalHeaderDetails = staticCompositionLocalOf { HeaderDetails(emptySet()) { _, _ -> } }

/** The keys, one per tab: a folder opened inside a tab shares its tab's choice. */
object HeaderKey {
    const val ALBUMS = "albums"
    const val ALBUM_PAGE = "album_page"
    const val RESTORE = "restore"
    const val ARCHIVE = "archive"
}

/** Whether the header under [key] shows its details. */
@Composable
fun detailsShown(key: String): Boolean = LocalHeaderDetails.current.shown(key)

/** [content], sliding open and shut with the header's details. */
@Composable
fun ColumnScope.HeaderDetailsSection(shown: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible = shown,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** The line across the bottom of the card that hides or shows the details. */
@Composable
fun HeaderDetailsToggle(key: String, modifier: Modifier = Modifier) {
    val details = LocalHeaderDetails.current
    val shown = details.shown(key)
    val content = LocalContentColor.current
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = content.copy(alpha = 0.25f))
        Text(
            text = stringResource(if (shown) R.string.header_hide_details else R.string.header_show_details),
            style = MaterialTheme.typography.labelLarge,
            color = content.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { details.set(key, !shown) }
                .padding(top = 8.dp, bottom = 2.dp)
        )
    }
}
