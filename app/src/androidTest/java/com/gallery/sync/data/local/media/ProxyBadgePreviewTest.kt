package com.gallery.sync.data.local.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders the badge onto flat backgrounds so it can be inspected before it is ever drawn onto
 * someone's photo.
 *
 * White and black are the two cases that matter: a scrim that vanishes on a snowfield or a glyph
 * that vanishes on a night sky is the whole failure mode, and neither is visible from unit tests.
 */
@RunWith(AndroidJUnit4::class)
class ProxyBadgePreviewTest {

    @Test
    fun renderBadgePreviews() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Internal cache, because `adb shell` cannot read Android/data on this device but `run-as`
        // can reach app-private storage.
        val dir = context.cacheDir

        // Rotation 90 is the common case on a Samsung camera: the photo is stored landscape with an
        // EXIF flag, so the badge must be drawn against that turn to display upright.
        val cases = mapOf(
            "badge_on_white.png" to (Color.WHITE to 0),
            "badge_on_black.png" to (Color.BLACK to 0),
            "badge_rotated_90.png" to (Color.rgb(128, 128, 128) to 90),
            "badge_rotated_270.png" to (Color.rgb(128, 128, 128) to 270)
        )

        cases.forEach { (name, case) ->
            val (background, rotation) = case
            val bitmap = Bitmap.createBitmap(2048, 1152, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).drawColor(background)
            ProxyBadge.drawOn(bitmap, rotation)

            val file = File(dir, name)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()

            assertTrue("$name was not written", file.exists() && file.length() > 0)
        }
    }
}
