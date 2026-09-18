package com.gallery.sync.ui.help

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pop-up's reader. It has to read the guide's tiny format exactly as the generator does, or a
 * pop-up would lay out differently from the page it was written alongside.
 */
class HelpTextTest {

    @Test
    fun headingsParagraphsAndListsComeOutInOrder() {
        val blocks = HelpText.parse(
            """
            ## What it is
            A line of words
            that wraps.

            • first
            • second

            1. one
            2. two
            """.trimIndent()
        )

        assertEquals(
            listOf(
                HelpBlock.Heading("What it is"),
                HelpBlock.Paragraph("A line of words that wraps."),
                HelpBlock.Bullets(listOf("first", "second")),
                HelpBlock.Numbered(listOf("one", "two"))
            ),
            blocks
        )
    }

    /** The generator's parser splits these too, so a lead-in sentence needs no blank line before its list. */
    @Test
    fun aListRightAfterASentenceIsItsOwnBlock() {
        val blocks = HelpText.parse("The choices:\n• one\n• two\nAnd then a sentence.")

        assertEquals(
            listOf(
                HelpBlock.Paragraph("The choices:"),
                HelpBlock.Bullets(listOf("one", "two")),
                HelpBlock.Paragraph("And then a sentence.")
            ),
            blocks
        )
    }

    @Test
    fun aNumberInsideAParagraphIsNotAList() {
        assertEquals(
            listOf(HelpBlock.Paragraph("About 2,000 files at a time, 1. Not a list.")),
            HelpText.parse("About 2,000 files at a time, 1. Not a list.")
        )
    }

    @Test
    fun boldRunsLoseTheirMarkersAndKeepTheText() {
        val text = HelpText.styled("Tap **Sync now** to start")

        assertEquals("Tap Sync now to start", text.text)
        assertEquals(1, text.spanStyles.size)
        assertEquals(4, text.spanStyles.single().start)
        assertEquals(12, text.spanStyles.single().end)
    }

    @Test
    fun emptyBodyIsNoBlocks() {
        assertEquals(emptyList<HelpBlock>(), HelpText.parse(""))
    }
}
