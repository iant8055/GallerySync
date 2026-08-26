package com.gallery.sync.ui.common

import android.content.Context
import com.gallery.sync.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Locks the unit convention, which is the part that can silently drift back.
 *
 * The assembled string is the Android framework's job; what matters here is **which number and which
 * unit** a byte count resolves to. These assert decimal — 1 KB is 1000 bytes — because the app has
 * to agree with Samsung Gallery and Android's own UI about how big a file is. See [formatBytes] for
 * why that is worth a test rather than a comment.
 */
class FormattingTest {

    private val context: Context = mock<Context>().apply {
        // Renders as "<unit>:<value>" so a test can see both without depending on real resources.
        whenever(getString(any(), any())).thenAnswer { invocation ->
            val res = invocation.arguments[0] as Int
            val value = invocation.arguments[1]
            "${unitName(res)}:$value"
        }
    }

    private fun unitName(res: Int) = when (res) {
        R.string.size_bytes -> "B"
        R.string.size_kilobytes -> "KB"
        R.string.size_megabytes -> "MB"
        R.string.size_gigabytes -> "GB"
        else -> "?"
    }

    @Test
    fun `the 2 GB clip that started this reads as decimal, not binary`() {
        // 26 Aug 2026: this exact file read as 1.9 while the gallery said 2.0, and the gap looked
        // like data loss on a file later proven byte-identical by SHA-256.
        assertEquals("GB:2.0", formatBytes(context, 2_032_370_426L))
    }

    @Test
    fun `a kilobyte is a thousand bytes`() {
        assertEquals("B:999", formatBytes(context, 999L))
        assertEquals("KB:1", formatBytes(context, 1_000L))
    }

    @Test
    fun `boundaries land on the larger unit, not the smaller one`() {
        assertEquals("KB:999", formatBytes(context, 999_999L))
        assertEquals("MB:1", formatBytes(context, 1_000_000L))
        assertEquals("MB:999", formatBytes(context, 999_999_999L))
        assertEquals("GB:1.0", formatBytes(context, 1_000_000_000L))
    }

    @Test
    fun `zero does not fall through to gigabytes`() {
        assertEquals("B:0", formatBytes(context, 0L))
    }
}
