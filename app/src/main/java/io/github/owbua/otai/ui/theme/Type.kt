package io.github.owbua.otai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.owbua.otai.R

/*
 * Roboto Flex (variable, OFL). Compose wählt Fonts innerhalb einer Familie nur nach Gewicht,
 * deshalb gibt es pro Breite (wdth) eine eigene Familie – das Design nutzt für Überschriften
 * breite Schnitte (font-stretch 110–140 %).
 */
@OptIn(ExperimentalTextApi::class)
private fun flex(width: Float, vararg weights: Int) = FontFamily(
    weights.map { w ->
        Font(
            R.font.roboto_flex,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(w),
                FontVariation.width(width),
            ),
        )
    },
)

val RobotoFlex = flex(100f, 400, 500, 600, 700, 800)
val RobotoFlexWide = flex(115f, 500, 600, 700, 800)
val RobotoFlexWider = flex(125f, 700, 800, 900)
val RobotoFlexWidest = flex(140f, 800, 900)

private val base = TextStyle(fontFamily = RobotoFlex)

val OtaiTypography = Typography(
    displayLarge = base.copy(fontFamily = RobotoFlexWidest, fontWeight = FontWeight(900), fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = base.copy(fontFamily = RobotoFlexWider, fontWeight = FontWeight(800), fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.5).sp),
    displaySmall = base.copy(fontFamily = RobotoFlexWider, fontWeight = FontWeight(800), fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.3).sp),
    headlineLarge = base.copy(fontFamily = RobotoFlexWide, fontWeight = FontWeight(800), fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = base.copy(fontFamily = RobotoFlexWide, fontWeight = FontWeight(500), fontSize = 28.sp, lineHeight = 38.sp),
    headlineSmall = base.copy(fontFamily = RobotoFlexWide, fontWeight = FontWeight(800), fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = base.copy(fontFamily = RobotoFlexWide, fontWeight = FontWeight(800), fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = base.copy(fontWeight = FontWeight(700), fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = base.copy(fontWeight = FontWeight(700), fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = base.copy(fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = base.copy(fontWeight = FontWeight(400), fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = base.copy(fontWeight = FontWeight(400), fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = base.copy(fontWeight = FontWeight(600), fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = base.copy(fontWeight = FontWeight(600), fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = base.copy(fontWeight = FontWeight(600), fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
)

/** Großer Timer-Text (56 sp, tabellarische Ziffern). */
val TimerTextStyle = base.copy(
    fontFamily = RobotoFlexWide, fontWeight = FontWeight(700), fontSize = 56.sp, lineHeight = 60.sp,
    letterSpacing = (-1).sp, fontFeatureSettings = "tnum",
)

/** Kochansicht-Schritttext (28 sp). */
val CookStepTextStyle = base.copy(fontWeight = FontWeight(500), fontSize = 28.sp, lineHeight = 38.sp, letterSpacing = 0.em)
