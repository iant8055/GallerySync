package com.gallery.sync.ui.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The wizard never writes a value the Settings tab owns. Ian, 26 Sept 2026, after finding Optimise video
 * already on in Settings on a phone that had only been through the wizard: "THE WIZARD SHOULD NEVER EVER SET A
 * DEFAULT IN SETTINGS". CLAUDE.md has carried the rule since 7 Sept; the code broke it from 31 August, and a
 * note about it sat in MILESTONES for three weeks. This reads the source, because the storage needs an Android
 * context and a rule that has been broken this quietly needs a check that fails the build.
 *
 * It finds every function of [ReconcileViewModel] that `SetupTour` calls, and fails when one of them (or the
 * tour itself) calls a setter for a value the Settings tab shows. The wizard has its own values, `setWizard...`.
 */
class WizardDoesNotWriteSettingsTest {

    /** Setters for values the Settings tab reads and writes. Adding a Settings value means adding it here. */
    private val settingsOwned = listOf(
        "setOptimiseEnabled", "setOptimisePhotos", "setOptimiseVideo", "setPhotoOptimiseMode",
        "setVideoOptimiseMode", "setVideoQuality", "setVideoOptimiseAge", "setAllowMeteredNetwork",
        "setCloudDeletionPolicy", "setAutomaticEnabled", "setBackupLocation", "setPaused"
    )

    private fun source(relative: String): String {
        // Gradle runs unit tests from the module directory; an IDE may run them from the repository root.
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.first { it.exists() }.readText()
    }

    private val tour = source("src/main/java/com/gallery/sync/ui/setup/SetupTour.kt")
    private val viewModel = source("src/main/java/com/gallery/sync/ui/setup/ReconcileViewModel.kt")

    @Test
    fun `the tour itself calls no Settings setter`() {
        val called = settingsOwned.filter { Regex("""\b$it\b""").containsMatchIn(tour) }
        assertEquals("The wizard UI calls a Settings-owned setter: $called", emptyList<String>(), called)
    }

    @Test
    fun `nothing the tour calls on its view model writes a Settings value`() {
        val calledOnViewModel = Regex("""viewModel(?:::|\.)(\w+)""").findAll(tour).map { it.groupValues[1] }.toSet()
        assertTrue("expected the tour to call its view model", calledOnViewModel.isNotEmpty())

        val offenders = mutableListOf<String>()
        for (name in calledOnViewModel) {
            val body = bodyOf(name) ?: continue
            for (setter in settingsOwned) {
                if (Regex("""settings\.$setter\b""").containsMatchIn(body)) offenders += "$name writes $setter"
            }
        }
        assertEquals("The wizard writes a value Settings owns: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun `the old shared switches are gone from the wizard's view model`() {
        val gone = listOf("setAutoOptimiseEnabled", "setOptimiseVideo", "setVideoQuality")
        val present = gone.filter { Regex("""fun $it\b""").containsMatchIn(viewModel) }
        assertEquals("These wrote Settings' own values from the wizard: $present", emptyList<String>(), present)
    }

    /** The text of `fun [name](...) { ... }` in the view model, by brace matching, or null when it has none. */
    private fun bodyOf(name: String): String? {
        val start = Regex("""fun $name\b""").find(viewModel)?.range?.first ?: return null
        val open = viewModel.indexOf('{', start).takeIf { it >= 0 } ?: return null
        var depth = 0
        for (i in open until viewModel.length) {
            when (viewModel[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return viewModel.substring(open, i + 1)
            }
        }
        return null
    }
}
