package com.gallery.sync.data.local.media

import androidx.exifinterface.media.ExifInterface
import com.gallery.sync.data.local.media.ProxyGenerator.Companion.TARGET_LONG_EDGE_PX
import com.gallery.sync.data.local.media.ProxyGenerator.Companion.sampleSizeFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the proxy sizing decisions.
 *
 * Bitmap work needs a device, so the arithmetic that decides how much to decode is extracted and
 * tested here. It matters in both directions: too large a sample size gives a blurrier proxy than
 * intended, too small decodes a 50 MP image whole and kills the process on someone's phone.
 */
class ProxyGeneratorTest {

    @Test
    fun `an image already at the target is not sampled down`() {
        assertEquals(1, sampleSizeFor(longEdge = TARGET_LONG_EDGE_PX, target = TARGET_LONG_EDGE_PX))
    }

    @Test
    fun `an image smaller than the target is not sampled down`() {
        assertEquals(1, sampleSizeFor(longEdge = 800, target = TARGET_LONG_EDGE_PX))
    }

    @Test
    fun `just over double the target samples by two`() {
        // 4096 / 2 = 2048, exactly the target, so 2 is the largest safe step.
        assertEquals(2, sampleSizeFor(longEdge = 4096, target = TARGET_LONG_EDGE_PX))
    }

    @Test
    fun `a large photo samples down without going under the target`() {
        // 8000 / 2 = 4000, / 4 = 2000 which is below 2048, so 2 is correct.
        assertEquals(2, sampleSizeFor(longEdge = 8000, target = TARGET_LONG_EDGE_PX))
    }

    @Test
    fun `sampling never leaves the image below the target`() {
        // The property that matters: decoding must not undershoot, or the proxy is blurrier than
        // asked for and the original is already gone by the time anyone notices.
        (2049..12000 step 137).forEach { longEdge ->
            val sample = sampleSizeFor(longEdge, TARGET_LONG_EDGE_PX)
            assertTrue(
                "sample $sample for $longEdge decoded to ${longEdge / sample}, under target",
                longEdge / sample >= TARGET_LONG_EDGE_PX
            )
        }
    }

    @Test
    fun `sample size is always a power of two`() {
        // BitmapFactory rounds to a power of two anyway; returning one keeps the decoded size
        // predictable rather than whatever it silently rounds to.
        (2049..20000 step 311).forEach { longEdge ->
            val sample = sampleSizeFor(longEdge, TARGET_LONG_EDGE_PX)
            assertEquals("sample $sample for $longEdge is not a power of two", 0, sample and (sample - 1))
        }
    }

    @Test
    fun `a nonsense target does not produce a divide by zero`() {
        assertEquals(1, sampleSizeFor(longEdge = 4000, target = 0))
    }

    @Test
    fun `orientation and date are preserved, or portraits display sideways`() {
        // Dropping orientation is the most visible possible failure: every portrait in the
        // library rotates. Date grouping breaks without the timestamps.
        val tags = ProxyGenerator.PRESERVED_EXIF_TAGS

        assertTrue(ExifInterface.TAG_ORIENTATION in tags)
        assertTrue(ExifInterface.TAG_DATETIME_ORIGINAL in tags)
        assertTrue(ExifInterface.TAG_GPS_LATITUDE in tags)
        assertTrue(ExifInterface.TAG_GPS_LONGITUDE in tags)
    }
}
