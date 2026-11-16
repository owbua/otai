package io.github.owbua.otai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Farben aus dem Design (Claude Design · „Rezept-App · Material 3 Expressive“), Seed #9A4521
private val LightColors = lightColorScheme(
    primary = Color(0xFF9A4521),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3A0B00),
    inversePrimary = Color(0xFFFFB59E),
    secondary = Color(0xFF77574C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF7DED3),
    onSecondaryContainer = Color(0xFF2C160E),
    tertiary = Color(0xFF6A5E2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF4E2A7),
    onTertiaryContainer = Color(0xFF221B00),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231917),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231917),
    surfaceVariant = Color(0xFFF5DED6),
    onSurfaceVariant = Color(0xFF53433E),
    surfaceTint = Color(0xFF9A4521),
    inverseSurface = Color(0xFF392E2B),
    inverseOnSurface = Color(0xFFFFEDE8),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF85736D),
    outlineVariant = Color(0xFFD8C2BA),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F6),
    surfaceDim = Color(0xFFE8D6D1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EC),
    surfaceContainer = Color(0xFFFCEAE4),
    surfaceContainerHigh = Color(0xFFF6E4DE),
    surfaceContainerHighest = Color(0xFFF1DFD8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB59E),
    onPrimary = Color(0xFF5D1900),
    primaryContainer = Color(0xFF7B2E0C),
    onPrimaryContainer = Color(0xFFFFDBCF),
    inversePrimary = Color(0xFF9A4521),
    secondary = Color(0xFFE7BDB1),
    onSecondary = Color(0xFF442A22),
    secondaryContainer = Color(0xFF5D4037),
    onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFD7C68D),
    onTertiary = Color(0xFF3A3005),
    tertiaryContainer = Color(0xFF524619),
    onTertiaryContainer = Color(0xFFF4E2A7),
    background = Color(0xFF1A110F),
    onBackground = Color(0xFFF1DFDA),
    surface = Color(0xFF1A110F),
    onSurface = Color(0xFFF1DFDA),
    surfaceVariant = Color(0xFF53433E),
    onSurfaceVariant = Color(0xFFD8C2BA),
    surfaceTint = Color(0xFFFFB59E),
    inverseSurface = Color(0xFFF1DFDA),
    inverseOnSurface = Color(0xFF392E2B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA08C86),
    outlineVariant = Color(0xFF53433E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF423733),
    surfaceDim = Color(0xFF1A110F),
    surfaceContainerLowest = Color(0xFF140C0A),
    surfaceContainerLow = Color(0xFF231917),
    surfaceContainer = Color(0xFF271D1B),
    surfaceContainerHigh = Color(0xFF322825),
    surfaceContainerHighest = Color(0xFF3D3230),
)

/** Farbtöne der Rezept-Kacheln (Hintergrund, Form, Initiale) – wie im Design „TONES“. */
@Immutable
data class TileTone(val bg: Color, val shape: Color, val fg: Color)

@Immutable
data class OtaiColors(val tones: List<TileTone>)

private val LightExtra = OtaiColors(
    listOf(
        TileTone(Color(0xFFFFDBCF), Color(0xFFFFB59E), Color(0xFF3A0B00)), // primary
        TileTone(Color(0xFFF4E2A7), Color(0xFFDCC77F), Color(0xFF221B00)), // tertiary
        TileTone(Color(0xFFF1DFD8), Color(0xFFDDC5BB), Color(0xFF2C160E)), // neutral
    ),
)

private val DarkExtra = OtaiColors(
    listOf(
        TileTone(Color(0xFF7B2E0C), Color(0xFF9A4521), Color(0xFFFFDBCF)),
        TileTone(Color(0xFF524619), Color(0xFF6A5E2F), Color(0xFFF4E2A7)),
        TileTone(Color(0xFF3D3230), Color(0xFF53433E), Color(0xFFF1DFDA)),
    ),
)

private val LocalOtaiColors = staticCompositionLocalOf { LightExtra }

object Otai {
    val colors: OtaiColors
        @Composable @ReadOnlyComposable get() = LocalOtaiColors.current
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OtaiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme: ColorScheme = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalOtaiColors provides if (darkTheme) DarkExtra else LightExtra) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = OtaiTypography,
        ) {
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface, content = content)
        }
    }
}
