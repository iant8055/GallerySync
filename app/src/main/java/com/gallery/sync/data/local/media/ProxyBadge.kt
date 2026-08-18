package com.gallery.sync.data.local.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** Where the badge sits, in pixels. Plain floats so the geometry is testable off-device. */
data class BadgeBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val size: Float get() = right - left
}

/**
 * Draws the "this is an optimised copy" cloud into a proxy's pixels.
 *
 * Baked into the image because that is the only mechanism there is: GallerySync cannot draw inside
 * Samsung Gallery, CapCut, or any other app's grid, and MediaStore has no field for a badge. The
 * cost is accepted deliberately — an editor that imports this photo imports the badge with it. The
 * full-size original in OneDrive is never touched, so retrieving it is the way back to a clean
 * image.
 *
 * **The colours here are intentionally fixed, and the dark-mode rule does not apply.** They are
 * written into a JPEG that other apps render however they like; there is no theme to follow and no
 * opportunity to re-draw. Legibility comes from the badge carrying its own contrast — a dark scrim
 * under a white glyph reads on a snowfield and on a night sky alike.
 */
object ProxyBadge {

    /**
     * Anchors the badge inside the largest centred square rather than the true corner.
     *
     * Gallery grids crop thumbnails to squares, so on a 4:3 photo the actual corner is precisely
     * the part that gets cropped away — the badge would be invisible in the one place it exists to
     * be seen.
     */
    fun boundsFor(width: Int, height: Int): BadgeBounds {
        val square = minOf(width, height)
        val size = square * SIZE_FRACTION
        val margin = square * MARGIN_FRACTION

        // Right and bottom edges of the centred square, which is the visible area of a cropped
        // thumbnail.
        val right = (width + square) / 2f - margin
        val bottom = (height + square) / 2f - margin

        return BadgeBounds(right - size, bottom - size, right, bottom)
    }

    /**
     * The size the photo appears at once EXIF rotation is applied. A quarter turn swaps the axes.
     */
    fun displaySizeFor(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> =
        if (normalise(rotationDegrees) % 180 == 90) height to width else width to height

    /**
     * Draws onto [bitmap] in place. The bitmap must be mutable.
     *
     * [rotationDegrees] is the photo's EXIF rotation — what every gallery applies before showing
     * it. Without accounting for it the badge is drawn into the stored buffer and then rotated
     * along with the photo, so it lands on its side in a corner nobody chose. Everything below is
     * therefore positioned in *display* space and mapped back onto the stored pixels.
     */
    fun drawOn(bitmap: Bitmap, rotationDegrees: Int = 0) {
        val rotation = normalise(rotationDegrees)
        val (displayWidth, displayHeight) =
            displaySizeFor(bitmap.width, bitmap.height, rotation)

        val b = boundsFor(displayWidth, displayHeight)
        val rect = RectF(b.left, b.top, b.right, b.bottom)

        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val canvas = Canvas(bitmap)

        canvas.save()
        // The inverse of the rotation the gallery is about to apply, so the two cancel out and the
        // cloud ends up upright in the bottom-right of what the user actually sees.
        when (rotation) {
            90 -> {
                canvas.translate(0f, h)
                canvas.rotate(-90f)
            }

            180 -> {
                canvas.translate(w, h)
                canvas.rotate(180f)
            }

            270 -> {
                canvas.translate(w, 0f)
                canvas.rotate(90f)
            }
        }

        val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SCRIM_COLOR }
        val corner = b.size * CORNER_FRACTION
        canvas.drawRoundRect(rect, corner, corner, scrim)

        val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GLYPH_COLOR }
        canvas.drawPath(cloudPath(rect), glyph)

        canvas.restore()
    }

    /** Folds any reported rotation onto one of 0, 90, 180, 270. */
    private fun normalise(degrees: Int): Int = ((degrees % 360) + 360) % 360

    /**
     * The familiar cloud: a wide rounded base, one large dome left of centre, a small bump to its
     * right. Proportions follow the icon Samsung uses, because recognition is the entire job — a
     * shape the user has to interpret has already failed.
     *
     * Drawn filled rather than outlined. Samsung strokes theirs because it sits in their own UI
     * over a controlled background; this one is baked into a photograph and shrunk to a grid
     * thumbnail, where a thin outline breaks up against foliage or a crowd.
     */
    private fun cloudPath(rect: RectF): Path {
        val inset = rect.width() * GLYPH_INSET_FRACTION
        val area = RectF(
            rect.left + inset,
            rect.top + inset,
            rect.right - inset,
            rect.bottom - inset
        )

        val w = area.width()
        // A cloud is markedly wider than it is tall; centred so the square scrim stays balanced.
        val h = w * CLOUD_ASPECT
        val top = area.top + (area.height() - h) / 2f

        return Path().apply {
            addRoundRect(
                RectF(area.left, top + h * 0.42f, area.right, top + h),
                h * 0.29f,
                h * 0.29f,
                Path.Direction.CW
            )
            addCircle(area.left + w * 0.38f, top + h * 0.375f, h * 0.37f, Path.Direction.CW)
            addCircle(area.left + w * 0.66f, top + h * 0.375f, h * 0.22f, Path.Direction.CW)
        }
    }

    /** Fraction of the short edge the badge occupies. Big enough to survive a grid thumbnail. */
    const val SIZE_FRACTION = 0.13f

    /** Height as a fraction of width, measured off Samsung's icon. */
    private const val CLOUD_ASPECT = 0.64f

    private const val MARGIN_FRACTION = 0.025f
    private const val CORNER_FRACTION = 0.26f
    private const val GLYPH_INSET_FRACTION = 0.17f

    /** Opaque enough to separate the glyph from a bright photo, light enough not to punch a hole. */
    private const val SCRIM_COLOR = 0xB3101010.toInt()
    private const val GLYPH_COLOR = 0xFFFFFFFF.toInt()
}
