package com.gallery.sync.ui.help

import com.gallery.sync.ui.settings.SupportLinks
import com.gallery.sync.ui.settings.SupportPage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The (?) pop-ups, the published guide and the screens must agree, and this is what makes them.
 *
 * ### Why a test and not a promise
 *
 * The guide is written once, in `tools/guide/content/`, and generated into the two web pages and into
 * `help_topics.xml`. Generation makes drift unlikely; it does not make it impossible. Someone can
 * edit a generated file by hand, add a (?) for a topic that was never written, or write a topic no
 * screen ever opens. Each of those is a shipped defect that compiles, so each is checked here.
 *
 * The files are read from the working tree, not from the published site, so this proves the
 * repository is consistent. Whether GitHub Pages has been updated is a separate fact: `docs/` only
 * reaches the app once it is pushed.
 */
class HowToGuideConsistencyTest {

    private val flatGuide by lazy { read("../docs/how-to-guide.html") }
    private val accordionGuide by lazy { read("../docs/how-to-guide-accordion.html") }
    private val helpXml by lazy { readXmlStrings("src/main/res/values/help_topics.xml") }

    @Test
    fun everyTopicHasATitleAndABodyInTheStringResources() {
        for (topic in HelpTopic.entries) {
            val name = topic.slug.replace('-', '_')
            assertTrue("${topic.slug}: no title string", !helpXml["help_${name}_title"].isNullOrBlank())
            assertTrue("${topic.slug}: no body string", !helpXml["help_${name}_body"].isNullOrBlank())
        }
    }

    @Test
    fun everyTopicIsAnAnchorInBothPublishedPages() {
        for (topic in HelpTopic.entries) {
            assertTrue("${topic.slug} missing from how-to-guide.html", "id=\"${topic.slug}\"" in flatGuide)
            assertTrue(
                "${topic.slug} missing from how-to-guide-accordion.html",
                "id=\"${topic.slug}\"" in accordionGuide
            )
        }
    }

    @Test
    fun theTwoPagesHoldTheSameTopics() {
        assertEquals(idsIn(flatGuide), idsIn(accordionGuide))
    }

    /** The core promise: nothing a pop-up says is missing from the guide. */
    @Test
    fun everyLineOfEveryPopUpIsInTheGuide() {
        val flat = squash(textOf(flatGuide))
        val accordion = squash(textOf(accordionGuide))

        for (topic in HelpTopic.entries) {
            val name = topic.slug.replace('-', '_')
            val title = helpXml.getValue("help_${name}_title")
            assertTrue("${topic.slug}: title '$title' not in the guide", squash(title) in flat)

            for (piece in HelpText.parse(helpXml.getValue("help_${name}_body")).flatMap(::linesOf)) {
                val wanted = squash(piece.replace("**", ""))
                assertTrue("${topic.slug}: '$piece' is in the pop-up but not in how-to-guide.html", wanted in flat)
                assertTrue(
                    "${topic.slug}: '$piece' is in the pop-up but not in how-to-guide-accordion.html",
                    wanted in accordion
                )
            }
        }
    }

    /** The other direction: a (?) that opens nothing, or a topic no screen offers. */
    @Test
    fun everyTopicIsOfferedByAScreen() {
        val used = uiSources()
            .flatMap { file -> TOPIC_REFERENCE.findAll(file.readText()).map { it.groupValues[1] } }
            .toSet()

        val unused = HelpTopic.entries.map { it.name }.filter { it !in used }
        assertTrue("topics with a pop-up but no (?) on any screen: $unused", unused.isEmpty())
    }

    @Test
    fun everyLinkInsideTheGuideLandsSomewhere() {
        for ((name, page) in listOf("how-to-guide.html" to flatGuide, "how-to-guide-accordion.html" to accordionGuide)) {
            val ids = idsIn(page)
            val dead = Regex("href=\"#([^\"]+)\"").findAll(page).map { it.groupValues[1] }.filter { it !in ids }.toList()
            assertTrue("$name links to anchors that do not exist: $dead", dead.isEmpty())
        }
    }

    @Test
    fun theSettingsCardOpensAPageThatExistsAndStaysInsideTheApp() {
        val url = SupportPage.HOW_TO_GUIDE.url
        assertTrue(SupportLinks.staysInApp(url))
        assertTrue("$url is not a file in docs/", File("../docs/" + url.substringAfterLast('/')).exists())
        // "Read more" appends the topic's anchor, and that must not send the page to the browser.
        assertTrue(SupportLinks.staysInApp("$url#albums-filter"))
    }

    @Test
    fun theGuideCoversEveryTabAndTheSetup() {
        val chapters = Regex("id=\"ch-([a-z0-9-]+)\"").findAll(flatGuide).map { it.groupValues[1] }.toList()
        assertEquals(listOf("start", "setup", "albums", "restore", "archive", "settings", "concepts", "help"), chapters)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun linesOf(block: HelpBlock): List<String> = when (block) {
        is HelpBlock.Heading -> listOf(block.text)
        is HelpBlock.Paragraph -> listOf(block.text)
        is HelpBlock.Bullets -> block.items
        is HelpBlock.Numbered -> block.items
    }

    private fun idsIn(page: String): Set<String> =
        Regex("id=\"([^\"]+)\"").findAll(page).map { it.groupValues[1] }.toSet()

    private fun uiSources(): List<File> =
        File("src/main/java/com/gallery/sync/ui").walkTopDown()
            .filter { it.extension == "kt" && it.name != "HelpTopic.kt" }
            .toList()

    private fun read(path: String): String {
        val file = File(path)
        assertTrue("${file.absolutePath} not found: run tools/guide/build_guide.py", file.exists())
        return file.readText()
    }

    /** Android's string escapes undone, so the text compares with what a person reads. */
    private fun readXmlStrings(path: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to unescapeAndroid(node.textContent)
        }
    }

    private fun unescapeAndroid(text: String): String = buildString {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                when (val next = text[i + 1]) {
                    'n' -> append('\n')
                    else -> append(next)
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }

    /** The words on a page, without its markup. */
    private fun textOf(html: String): String = html
        .replace(Regex("<style.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    /** Whitespace removed, so a paragraph that the page wraps differently still matches. */
    private fun squash(text: String): String = text.filterNot { it.isWhitespace() }

    private companion object {
        val TOPIC_REFERENCE = Regex("HelpTopic\\.([A-Z0-9_]+)")
    }
}
