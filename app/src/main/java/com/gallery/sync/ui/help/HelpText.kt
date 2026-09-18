package com.gallery.sync.ui.help

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** One piece of a help pop-up, in the order it is read. */
sealed interface HelpBlock {
    data class Heading(val text: String) : HelpBlock
    data class Paragraph(val text: String) : HelpBlock
    data class Bullets(val items: List<String>) : HelpBlock
    data class Numbered(val items: List<String>) : HelpBlock
}

/**
 * Reads the plain-text body of a help topic.
 *
 * The body is written once, in `tools/guide/content/`, and the same text becomes both the published
 * guide and this pop-up. Its format is deliberately tiny so this and the generator's parser cannot
 * disagree: `## ` starts a heading, `• ` a bullet, `1. ` a numbered item, `**` toggles bold, and
 * anything else is a paragraph. Consecutive lines of the same kind belong together.
 */
object HelpText {

    fun parse(body: String): List<HelpBlock> {
        val blocks = mutableListOf<HelpBlock>()
        var kind: Kind? = null
        val buffer = mutableListOf<String>()

        fun flush() {
            if (buffer.isNotEmpty()) {
                blocks += when (kind) {
                    Kind.PARAGRAPH -> HelpBlock.Paragraph(buffer.joinToString(" "))
                    Kind.BULLET -> HelpBlock.Bullets(buffer.toList())
                    Kind.NUMBER -> HelpBlock.Numbered(buffer.toList())
                    null -> error("a buffered line always has a kind")
                }
            }
            buffer.clear()
            kind = null
        }

        for (raw in body.split("\n")) {
            val line = raw.trim()
            if (line.isEmpty()) {
                flush()
                continue
            }
            if (line.startsWith(HEADING)) {
                flush()
                blocks += HelpBlock.Heading(line.removePrefix(HEADING).trim())
                continue
            }
            val (lineKind, text) = when {
                line.startsWith(BULLET) -> Kind.BULLET to line.removePrefix(BULLET).trim()
                NUMBERED.containsMatchIn(line) -> Kind.NUMBER to line.replace(NUMBERED, "").trim()
                else -> Kind.PARAGRAPH to line
            }
            if (lineKind != kind) {
                flush()
                kind = lineKind
            }
            buffer += text
        }
        flush()
        return blocks
    }

    /** [text] with every `**bold**` run made bold and the markers removed. */
    fun styled(text: String): AnnotatedString = buildAnnotatedString {
        text.split(BOLD_MARK).forEachIndexed { index, part ->
            if (index % 2 == 1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) }
            } else {
                append(part)
            }
        }
    }

    private enum class Kind { PARAGRAPH, BULLET, NUMBER }

    private const val HEADING = "## "
    private const val BULLET = "• "
    private const val BOLD_MARK = "**"
    private val NUMBERED = Regex("^\\d+\\. ")
}
