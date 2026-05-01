package com.eduspecial.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val EduBlue = Color(0xFF050E3C)
val EduBlueDark = Color(0xFF002455)
val EduBlueLight = Color(0xFFDCE5FF)
val EduRoyal = Color(0xFF3E5CFF)
val EduAmber = Color(0xFFF4B942)
val EduScarlet = Color(0xFFFF3838)
val EduCrimson = Color(0xFFDC0000)
val EduTeal = Color(0xFF0D5C63)
val EduMulberry = Color(0xFF431A36)
val EduIndigo = Color(0xFF16295D)
val EduRubyMuted = Color(0xFFC42A40)

@Immutable
data class EduThemeTokens(
    val heroGradient: List<Color>,
    val brandGlow: List<Color>,
    val brandCoreGradient: List<Color>,
    val brandSparkGradient: List<Color>,
    val splashBottom: Color,
    val splashOrbital: Color,
    val splashWarm: Color
)

private data class ThemePalette(
    val light: ColorScheme,
    val dark: ColorScheme,
    val tokens: EduThemeTokens
)

val LocalEduThemeTokens = staticCompositionLocalOf {
    EduThemeTokens(
        heroGradient = listOf(EduBlue, EduBlueDark, EduCrimson, EduScarlet),
        brandGlow = listOf(EduScarlet.copy(alpha = 0.20f), EduCrimson.copy(alpha = 0.14f), Color.Transparent),
        brandCoreGradient = listOf(EduBlue, EduBlueDark),
        brandSparkGradient = listOf(EduCrimson, EduScarlet),
        splashBottom = Color(0xFF02071F),
        splashOrbital = EduBlueLight,
        splashWarm = EduScarlet
    )
}

object EduThemeExtras {
    val tokens: EduThemeTokens
        @Composable get() = LocalEduThemeTokens.current
}

private val ClassicPalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF050E3C),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE7ECFF),
        onPrimaryContainer = Color(0xFF02071F),
        secondary = Color(0xFF002455),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE1EAFF),
        onSecondaryContainer = Color(0xFF001733),
        tertiary = Color(0xFFFF3838),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFE1DF),
        onTertiaryContainer = Color(0xFF4A0000),
        error = Color(0xFFDC0000),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410001),
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF050E3C),
        surface = Color.White,
        onSurface = Color(0xFF050E3C),
        surfaceVariant = Color(0xFFF1F4FA),
        onSurfaceVariant = Color(0xFF42506F),
        outline = Color(0xFF6A7692),
        outlineVariant = Color(0xFFD5DBE8),
        scrim = Color.Black,
        inverseSurface = Color(0xFF111A35),
        inverseOnSurface = Color(0xFFF5F7FC),
        inversePrimary = Color(0xFFC7D1FF),
        surfaceTint = Color(0xFF050E3C)
    ),
    dark = darkColorScheme(
        primary = Color(0xFFD9E1FF),
        onPrimary = Color(0xFF02071F),
        primaryContainer = Color(0xFF002455),
        onPrimaryContainer = Color(0xFFE7ECFF),
        secondary = Color(0xFFBED0FF),
        onSecondary = Color(0xFF001733),
        secondaryContainer = Color(0xFF16386D),
        onSecondaryContainer = Color(0xFFE1EAFF),
        tertiary = Color(0xFFFF9A94),
        onTertiary = Color(0xFF560001),
        tertiaryContainer = Color(0xFF7B0004),
        onTertiaryContainer = Color(0xFFFFE1DF),
        error = Color(0xFFFFB4B4),
        onError = Color(0xFF690002),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF02071F),
        onBackground = Color(0xFFF5F7FC),
        surface = Color(0xFF07102C),
        onSurface = Color(0xFFF5F7FC),
        surfaceVariant = Color(0xFF122043),
        onSurfaceVariant = Color(0xFFC8D0E2),
        outline = Color(0xFF95A0BA),
        outlineVariant = Color(0xFF2E3C60),
        scrim = Color.Black,
        inverseSurface = Color(0xFFF5F7FC),
        inverseOnSurface = Color(0xFF111A35),
        inversePrimary = Color(0xFF050E3C),
        surfaceTint = Color(0xFFD9E1FF)
    ),
    tokens = EduThemeTokens(
        heroGradient = listOf(
            EduMulberry,
            EduIndigo,
            EduBlueDark,
            EduBlue
        ),
        brandGlow = listOf(
            EduRubyMuted.copy(alpha = 0.12f),
            EduMulberry.copy(alpha = 0.10f),
            Color.Transparent
        ),
        brandCoreGradient = listOf(EduBlue, EduBlueDark),
        brandSparkGradient = listOf(EduRubyMuted, Color(0xFFE24A5A)),
        splashBottom = Color(0xFF02071F),
        splashOrbital = Color(0xFFDCE5FF),
        splashWarm = EduRubyMuted
    )
)

private val OasisPalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF0D5C63),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC5F2F5),
        onPrimaryContainer = Color(0xFF002023),
        secondary = Color(0xFF1D7874),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD4F4F2),
        onSecondaryContainer = Color(0xFF00201E),
        tertiary = Color(0xFFF4B942),
        onTertiary = Color(0xFF3B2A00),
        tertiaryContainer = Color(0xFFFFE7B4),
        onTertiaryContainer = Color(0xFF251800),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF4FBFB),
        onBackground = Color(0xFF151C1D),
        surface = Color.White,
        onSurface = Color(0xFF151C1D),
        surfaceVariant = Color(0xFFDDE4E5),
        onSurfaceVariant = Color(0xFF41484A),
        outline = Color(0xFF71787A),
        outlineVariant = Color(0xFFC1C8CA),
        scrim = Color.Black,
        inverseSurface = Color(0xFF2A3132),
        inverseOnSurface = Color(0xFFECF2F2),
        inversePrimary = Color(0xFF8ADFE5),
        surfaceTint = Color(0xFF0D5C63)
    ),
    dark = darkColorScheme(
        primary = Color(0xFF8ADFE5),
        onPrimary = Color(0xFF00373C),
        primaryContainer = Color(0xFF004F56),
        onPrimaryContainer = Color(0xFFC5F2F5),
        secondary = Color(0xFF9AD9D4),
        onSecondary = Color(0xFF003734),
        secondaryContainer = Color(0xFF00504D),
        onSecondaryContainer = Color(0xFFD4F4F2),
        tertiary = Color(0xFFFFCA63),
        onTertiary = Color(0xFF3E2E00),
        tertiaryContainer = Color(0xFF5A4300),
        onTertiaryContainer = Color(0xFFFFE7B4),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0E1415),
        onBackground = Color(0xFFDCE3E4),
        surface = Color(0xFF0E1415),
        onSurface = Color(0xFFDCE3E4),
        surfaceVariant = Color(0xFF41484A),
        onSurfaceVariant = Color(0xFFC1C8CA),
        outline = Color(0xFF8B9294),
        outlineVariant = Color(0xFF41484A),
        scrim = Color.Black,
        inverseSurface = Color(0xFFDCE3E4),
        inverseOnSurface = Color(0xFF2A3132),
        inversePrimary = Color(0xFF0D5C63),
        surfaceTint = Color(0xFF8ADFE5)
    ),
    tokens = EduThemeTokens(
        heroGradient = listOf(
            Color(0xFF0D5C63),
            Color(0xFF1D7874),
            Color(0xFFF4B942)
        ),
        brandGlow = listOf(
            Color(0xFFF4B942).copy(alpha = 0.20f),
            Color(0xFF8ADFE5).copy(alpha = 0.14f),
            Color.Transparent
        ),
        brandCoreGradient = listOf(Color(0xFF0D5C63), Color(0xFF1D7874)),
        brandSparkGradient = listOf(Color(0xFFF4B942), Color(0xFFFFCA63)),
        splashBottom = Color(0xFF07292C),
        splashOrbital = Color(0xFFC5F2F5),
        splashWarm = Color(0xFFF4B942)
    )
)

private val SunsetPalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF7B2CBF),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF0DBFF),
        onPrimaryContainer = Color(0xFF290049),
        secondary = Color(0xFFE85D04),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFDBC7),
        onSecondaryContainer = Color(0xFF2F1400),
        tertiary = Color(0xFFFF006E),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFD9E2),
        onTertiaryContainer = Color(0xFF3E001F),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFFF8FB),
        onBackground = Color(0xFF201A1D),
        surface = Color.White,
        onSurface = Color(0xFF201A1D),
        surfaceVariant = Color(0xFFEDDEE7),
        onSurfaceVariant = Color(0xFF4D444C),
        outline = Color(0xFF7F747D),
        outlineVariant = Color(0xFFD1C2CB),
        scrim = Color.Black,
        inverseSurface = Color(0xFF352F32),
        inverseOnSurface = Color(0xFFFAEDF2),
        inversePrimary = Color(0xFFE0B6FF),
        surfaceTint = Color(0xFF7B2CBF)
    ),
    dark = darkColorScheme(
        primary = Color(0xFFE0B6FF),
        onPrimary = Color(0xFF47006F),
        primaryContainer = Color(0xFF63239E),
        onPrimaryContainer = Color(0xFFF0DBFF),
        secondary = Color(0xFFFFB689),
        onSecondary = Color(0xFF4E2600),
        secondaryContainer = Color(0xFF713800),
        onSecondaryContainer = Color(0xFFFFDBC7),
        tertiary = Color(0xFFFFB1C8),
        onTertiary = Color(0xFF650035),
        tertiaryContainer = Color(0xFF90004D),
        onTertiaryContainer = Color(0xFFFFD9E2),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF171114),
        onBackground = Color(0xFFECDFE4),
        surface = Color(0xFF171114),
        onSurface = Color(0xFFECDFE4),
        surfaceVariant = Color(0xFF4D444C),
        onSurfaceVariant = Color(0xFFD1C2CB),
        outline = Color(0xFF9A8D96),
        outlineVariant = Color(0xFF4D444C),
        scrim = Color.Black,
        inverseSurface = Color(0xFFECDFE4),
        inverseOnSurface = Color(0xFF352F32),
        inversePrimary = Color(0xFF7B2CBF),
        surfaceTint = Color(0xFFE0B6FF)
    ),
    tokens = EduThemeTokens(
        heroGradient = listOf(
            Color(0xFF7B2CBF),
            Color(0xFFE85D04),
            Color(0xFFFF006E)
        ),
        brandGlow = listOf(
            Color(0xFFFF006E).copy(alpha = 0.18f),
            Color(0xFFE0B6FF).copy(alpha = 0.12f),
            Color.Transparent
        ),
        brandCoreGradient = listOf(Color(0xFF7B2CBF), Color(0xFFE85D04)),
        brandSparkGradient = listOf(Color(0xFFE85D04), Color(0xFFFF006E)),
        splashBottom = Color(0xFF20101E),
        splashOrbital = Color(0xFFF0DBFF),
        splashWarm = Color(0xFFFF006E)
    )
)

private fun resolvePalette(palette: String): ThemePalette {
    return when (palette) {
        "oasis" -> OasisPalette
        "sunset" -> SunsetPalette
        else -> ClassicPalette
    }
}

@Composable
fun EduSpecialTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: String = "qusasa",
    content: @Composable () -> Unit
) {
    val paletteValues = resolvePalette(palette)
    val colorScheme = if (darkTheme) paletteValues.dark else paletteValues.light

    CompositionLocalProvider(
        LocalEduSpacing provides EduSpacing,
        LocalEduThemeTokens provides paletteValues.tokens
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = EduTypography,
            shapes = EduShapes,
            content = content
        )
    }
}
