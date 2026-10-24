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
 * The app's own font: Noto Sans, bundled (Ian, 24 Oct 2026), under the SIL Open Font License
 * (assets/licenses/NotoSans-OFL.txt). One variable file holds every weight. It covers the Latin languages; for
 * Japanese and Hindi Android draws the characters in its own Noto Sans JP and Noto Sans Devanagari, the same family,
 * so all seven languages match without bundling those (5-10 MB more).
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val NotoSans = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(
            R.font.noto_sans,
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

/** Every Material text style in [family]: Noto Sans for the app's own font, the phone's default otherwise. */
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
