package com.gallery.sync.data.repository

import com.gallery.sync.data.remote.cloud.previewLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** TASK-028: a temporary copy never leaves `cacheDir/open/`, and the Dropbox link is built from Dropbox's path. */
class OpenFileNamesTest {

    @Test
    fun `a display name can never become a path out of the cache folder`() {
        listOf("../../shared_prefs/x.xml", "a/b.jpg", "..\\x.jpg", "C:x.jpg", ".hidden.jpg", "   ", "").forEach { name ->
            val safe = OpenFileRepository.safeName(name)
            assertFalse(name, safe.contains('/') || safe.contains('\\') || safe.contains(':'))
            assertFalse(name, safe.startsWith('.'))
            assertFalse(name, safe.isEmpty())
        }
        assertEquals("IMG_0001.jpg", OpenFileRepository.safeName("IMG_0001.jpg"))
    }

    @Test
    fun `the Dropbox link previews the file in its folder`() {
        assertEquals(
            "https://www.dropbox.com/home/Camera?preview=IMG_1.jpg",
            previewLink("/Camera/IMG_1.jpg")
        )
        assertEquals(
            "https://www.dropbox.com/home/Moto%20G/Car%20Show?preview=My+car.jpg",
            previewLink("/Moto G/Car Show/My car.jpg")
        )
        assertEquals("https://www.dropbox.com/home?preview=top.jpg", previewLink("/top.jpg"))
    }
}
