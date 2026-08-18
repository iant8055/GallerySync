package com.gallery.sync.data.local.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The badge is worthless if a gallery's square thumbnail crop cuts it off, so the geometry is
 * pinned here rather than eyeballed on a device.
 */
class ProxyBadgeTest {

    @Test
    fun `badge stays inside the centred square of a landscape photo`() {
        val width = 2048
        val height = 1536
        val squareLeft = (width - height) / 2f
        val squareRight = (width + height) / 2f

        val bounds = ProxyBadge.boundsFor(width, height)

        assertTrue("badge ran past the crop's right edge", bounds.right <= squareRight)
        assertTrue("badge ran past the crop's left edge", bounds.left >= squareLeft)
        assertTrue("badge ran past the bottom", bounds.bottom <= height)
    }

    @Test
    fun `badge stays inside the centred square of a portrait photo`() {
        val width = 1536
        val height = 2048
        val squareBottom = (height + width) / 2f

        val bounds = ProxyBadge.boundsFor(width, height)

        assertTrue("badge ran past the crop's bottom edge", bounds.bottom <= squareBottom)
        assertTrue("badge ran past the right edge", bounds.right <= width)
        assertTrue("badge ran past the crop's top edge", bounds.top >= (height - width) / 2f)
    }

    @Test
    fun `badge is sized against the short edge, so it looks the same on any shape`() {
        val landscape = ProxyBadge.boundsFor(2048, 1536)
        val portrait = ProxyBadge.boundsFor(1536, 2048)

        assertEquals(landscape.size, portrait.size, 0.01f)
        assertEquals(1536 * ProxyBadge.SIZE_FRACTION, landscape.size, 0.01f)
    }

    @Test
    fun `badge is square`() {
        val bounds = ProxyBadge.boundsFor(2048, 1152)

        assertEquals(bounds.right - bounds.left, bounds.bottom - bounds.top, 0.01f)
    }
}
