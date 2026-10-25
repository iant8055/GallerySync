package com.gallery.sync.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.gallery.sync.R

/**
 * The app's own font: Roboto, bundled under the SIL Open Font License (assets/licenses/Roboto-OFL.txt). One variable
 * file holds every weight. Japanese and Hindi are drawn in the phone's own fonts for those scripts.
 *
 * Noto Sans for its first day (24 Oct 2026). Ian found it larger than his own large font on the Fold 8, and with the
 * phone's bold text on it went much heavier than Samsung's font does (reproduced on the Moto). Roboto is narrower and
 * is the face most Android apps use, so at the same size it reads smaller (Ian, 25 Oct 2026).
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val AppFont = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(
            R.font.roboto,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
        )
    }
)

private val Base = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

/** Every Material text style in [family]: Roboto for the app's own font, the phone's default otherwise. */
fun typographyIn(family: FontFamily): Typography = Base.copy(
    displayLarge = Base.displayLarge.copy(fontFamily = family),
    displayMedium = Base.displayMedium.copy(fontFamily = family),
    displaySmall = Base.displaySmall.copy(fontFamily = family),
    headlineLarge = Base.headlineLarge.copy(fontFamily = family),
    headlineMedium = Base.headlineMedium.copy(fontFamily = family),
    headlineSmall = Base.headlineSmall.copy(fontFamily = family),
    titleLarge = Base.titleLarge.copy(fontFamily = family),
    titleMedium = Base.titleMedium.copy(fontFamily = family),
    titleSmall = Base.titleSmall.copy(fontFamily = family),
    bodyLarge = Base.bodyLarge.copy(fontFamily = family),
    bodyMedium = Base.bodyMedium.copy(fontFamily = family),
    bodySmall = Base.bodySmall.copy(fontFamily = family),
    labelLarge = Base.labelLarge.copy(fontFamily = family),
    labelMedium = Base.labelMedium.copy(fontFamily = family),
    labelSmall = Base.labelSmall.copy(fontFamily = family)
)

/** The phone's own font, as the app drew before 24 Oct 2026. */
val Typography = typographyIn(FontFamily.Default)
