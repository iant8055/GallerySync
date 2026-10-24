package com.gallery.sync.ui.common

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * One line of text that shrinks to fit its space instead of being cut off. Ian, 23–24 Oct 2026: on the Fold 8's cover
 * screen with large text the Albums filters read "Back" and "Arch" and the buttons "Sync" and "Checkin". At normal
 * sizes it draws exactly as a plain one-line Text would; it only shrinks when the words do not fit.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    textAlign: TextAlign? = null
) {
    val full = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(color = LocalContentColor.current, textAlign = textAlign ?: style.textAlign),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = full)
    )
}
