package com.gallery.sync.ui.help

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gallery.sync.R
import com.gallery.sync.ui.common.SignalIcons
import com.gallery.sync.ui.settings.InAppPageDialog
import com.gallery.sync.ui.settings.SupportPage

/**
 * True while first-time setup is on screen, when the only part of the guide a person may open is the
 * setup page (Ian, 18 Sept 2026). The wizard provides it, and a pop-up that would offer "Read more"
 * into the full guide leaves the button out instead.
 */
val LocalSetupOnlyGuide = compositionLocalOf { false }

/**
 * The (?) beside an item. Tap it for the explanation of that item, taken from the How To Guide.
 *
 * ### One source of text
 *
 * Every explanation is written once, in `tools/guide/content/`, and becomes both the published guide
 * and the pop-up this opens, so the two cannot say different things. See [HelpTopic].
 *
 * ### Colour
 *
 * The icon takes `LocalContentColor`, so on the green cards it is the card's own ink and on a plain
 * surface it is the surface's. Nothing is set here, because a colour picked for one theme is exactly
 * how the Teleprompter app shipped unreadable in the other.
 */
@Composable
fun HelpButton(topic: HelpTopic, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(R.string.help_button_description, stringResource(topic.title))

    Box(
        modifier = modifier
            .size(HelpButtonSize)
            .clip(CircleShape)
            .clickable(role = Role.Button) { open = true },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = SignalIcons.Help,
            contentDescription = description,
            modifier = Modifier.size(HelpIconSize)
        )
    }

    if (open) HelpDialog(topic = topic, onDismiss = { open = false })
}

/**
 * [content] with a (?) at its end, for a line of text that has no control of its own.
 *
 * The content takes the width and wraps, and the button keeps its size, so a long line never pushes
 * the (?) off the edge of a narrow card.
 */
@Composable
fun WithHelp(
    topic: HelpTopic,
    modifier: Modifier = Modifier,
    /** For a line that sits centred, such as a card's figure: the button follows the text. */
    centered: Boolean = false,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) {
            Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
        } else {
            Arrangement.spacedBy(4.dp)
        }
    ) {
        Box(modifier = if (centered) Modifier.weight(1f, fill = false) else Modifier.weight(1f)) {
            content()
        }
        HelpButton(topic)
    }
}

/** A dialog title with its (?), for dialogs that ask something worth explaining. */
@Composable
fun TitleWithHelp(title: String, topic: HelpTopic) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = title, modifier = Modifier.weight(1f))
        HelpButton(topic)
    }
}

/**
 * The pop-up: what the item is, where its information comes from, and a way into the full guide.
 *
 * "Read more" opens the guide at this very topic. The pop-up itself needs no network, so the
 * explanation is there even where the guide, which is a web page, is not.
 */
@Composable
fun HelpDialog(topic: HelpTopic, onDismiss: () -> Unit) {
    var showGuide by remember { mutableStateOf(false) }
    val body = stringResource(topic.body)
    val blocks = remember(body) { HelpText.parse(body) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(topic.title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                blocks.forEach { block -> HelpBlockContent(block) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_page_close)) }
        },
        // Withdrawn during first-time setup, where the setup page is all of the guide a person can
        // open. A pop-up there would otherwise lead into every other chapter.
        dismissButton = if (LocalSetupOnlyGuide.current) {
            null
        } else {
            {
                TextButton(onClick = { showGuide = true }) {
                    Text(stringResource(R.string.help_open_guide))
                }
            }
        }
    )

    if (showGuide) {
        InAppPageDialog(
            page = SupportPage.HOW_TO_GUIDE,
            anchor = topic.slug,
            onDismiss = { showGuide = false }
        )
    }
}

@Composable
private fun HelpBlockContent(block: HelpBlock) {
    when (block) {
        is HelpBlock.Heading -> Text(
            text = block.text,
            style = MaterialTheme.typography.titleSmall
        )

        is HelpBlock.Paragraph -> Text(
            text = HelpText.styled(block.text),
            style = MaterialTheme.typography.bodyMedium
        )

        is HelpBlock.Bullets -> ItemList(block.items) { "•" }
        is HelpBlock.Numbered -> ItemList(block.items) { index -> "${index + 1}." }
    }
}

@Composable
private fun ItemList(items: List<String>, marker: (Int) -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = marker(index), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = HelpText.styled(item),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * The button's drawn size, kept close to a line of text so a card of six lines is not made a screen
 * taller by six buttons. 36dp was tried first and pushed the album list off a 320dp screen.
 *
 * The touch area is not this small: Compose widens the hit region of anything clickable to the
 * platform's 48dp minimum around its centre, so the button is easy to hit and cheap to lay out.
 */
private val HelpButtonSize = 28.dp
private val HelpIconSize = 20.dp
