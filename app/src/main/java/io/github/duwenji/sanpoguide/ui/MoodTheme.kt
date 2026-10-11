package io.github.duwenji.sanpoguide.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import io.github.duwenji.sanpoguide.mood.Mood
import io.github.duwenji.sanpoguide.mood.Place
import io.github.duwenji.sanpoguide.mood.Season
import io.github.duwenji.sanpoguide.mood.Sky
import io.github.duwenji.sanpoguide.mood.TimeOfDay

/** One look: the main color, a soft container for cards and banners, and the page background. */
private class Palette(
    val primary: Color,
    val container: Color,
    val onContainer: Color,
    val background: Color,
    val dark: Boolean = false,
)

private val Forest = Palette(Color(0xFF2E7D32), Color(0xFFD7EED5), Color(0xFF0B2E0F), Color(0xFFFBFDF8))
private val ForestDark = Palette(Color(0xFF81C784), Color(0xFF1F3A21), Color(0xFFD7EED5), Color(0xFF111411), dark = true)

private val Night = Palette(Color(0xFFA9B4F0), Color(0xFF262B52), Color(0xFFDDE1FF), Color(0xFF0F1124), dark = true)
private val Rain = Palette(Color(0xFF37617D), Color(0xFFD6E6F2), Color(0xFF0B2233), Color(0xFFF4F7FA))
private val RainDark = Palette(Color(0xFF9EC3DB), Color(0xFF1D3444), Color(0xFFD6E6F2), Color(0xFF0F1418), dark = true)
private val Evening = Palette(Color(0xFFA8521C), Color(0xFFFFDCC7), Color(0xFF361300), Color(0xFFFFF8F3))
private val EveningDark = Palette(Color(0xFFFFB68A), Color(0xFF4A2610), Color(0xFFFFDCC7), Color(0xFF19120D), dark = true)
private val Morning = Palette(Color(0xFF00796B), Color(0xFFCDEEE7), Color(0xFF00201B), Color(0xFFF4FBF9))
private val MorningDark = Palette(Color(0xFF80CBC4), Color(0xFF113832), Color(0xFFCDEEE7), Color(0xFF0E1513), dark = true)

private fun dayPalette(season: Season, dark: Boolean) = when (season) {
    Season.SPRING -> if (dark) Palette(Color(0xFFFFB0C8), Color(0xFF4F2434), Color(0xFFFFD9E3), Color(0xFF191113), true)
    else Palette(Color(0xFFA8435F), Color(0xFFFFD9E3), Color(0xFF3E001D), Color(0xFFFFF8F8))
    Season.SUMMER -> if (dark) Palette(Color(0xFF9ECAFF), Color(0xFF15345A), Color(0xFFD3E4FF), Color(0xFF0F1318), true)
    else Palette(Color(0xFF1B62B0), Color(0xFFD3E4FF), Color(0xFF001C3A), Color(0xFFF8F9FF))
    Season.AUTUMN -> if (dark) Palette(Color(0xFFF2BD6E), Color(0xFF4A3212), Color(0xFFFFDDB0), Color(0xFF17130E), true)
    else Palette(Color(0xFF8F5410), Color(0xFFFFDDB0), Color(0xFF2D1600), Color(0xFFFFF8F2))
    Season.WINTER -> if (dark) Palette(Color(0xFFB4C8E6), Color(0xFF2A3748), Color(0xFFD6E3F7), Color(0xFF111418), true)
    else Palette(Color(0xFF4A6079), Color(0xFFD6E3F7), Color(0xFF0B1D31), Color(0xFFF7F9FC))
}

/**
 * The look for [mood]: dark at night, cool when it's wet or foggy, warm in the evening, fresh in
 * the morning, and the season's color on a clear day. Null (moods turned off) is the app's green.
 */
private fun paletteFor(mood: Mood?, systemDark: Boolean): Palette = when {
    mood == null -> if (systemDark) ForestDark else Forest
    mood.time.isDark -> Night
    mood.sky?.isWet == true || mood.sky == Sky.FOG -> if (systemDark) RainDark else Rain
    mood.time == TimeOfDay.EVENING -> if (systemDark) EveningDark else Evening
    mood.time == TimeOfDay.MORNING -> if (systemDark) MorningDark else Morning
    else -> dayPalette(mood.season, systemDark)
}

private fun Palette.toScheme(): ColorScheme = if (dark) {
    darkColorScheme(
        primary = primary, onPrimary = background,
        primaryContainer = container, onPrimaryContainer = onContainer,
        secondary = primary, secondaryContainer = container, onSecondaryContainer = onContainer,
        background = background, surface = background,
    )
} else {
    lightColorScheme(
        primary = primary, onPrimary = Color.White,
        primaryContainer = container, onPrimaryContainer = onContainer,
        secondary = primary, secondaryContainer = container, onSecondaryContainer = onContainer,
        background = background, surface = background,
    )
}

/** Old-style places read better in a serif (Mincho) face. */
private fun typographyFor(mood: Mood?): Typography {
    val base = Typography()
    if (mood?.place != Place.SHRINE_TEMPLE && mood?.place != Place.HISTORIC) return base
    return base.copy(
        headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif),
        titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif),
        bodyLarge = base.bodyLarge.copy(fontFamily = FontFamily.Serif),
        bodyMedium = base.bodyMedium.copy(fontFamily = FontFamily.Serif),
    )
}

/** The app theme, following [mood] (null for the plain theme). Colors fade over a few seconds. */
@Composable
fun MoodTheme(mood: Mood?, systemDark: Boolean, content: @Composable () -> Unit) {
    val target = paletteFor(mood, systemDark).toScheme()
    MaterialTheme(colorScheme = target.animated(), typography = typographyFor(mood), content = content)
}

@Composable
private fun ColorScheme.animated(): ColorScheme {
    @Composable
    fun Color.fade(): Color = animateColorAsState(this, tween(FADE_MS), label = "mood").value

    return copy(
        primary = primary.fade(),
        onPrimary = onPrimary.fade(),
        primaryContainer = primaryContainer.fade(),
        onPrimaryContainer = onPrimaryContainer.fade(),
        secondary = secondary.fade(),
        secondaryContainer = secondaryContainer.fade(),
        onSecondaryContainer = onSecondaryContainer.fade(),
        background = background.fade(),
        onBackground = onBackground.fade(),
        surface = surface.fade(),
        onSurface = onSurface.fade(),
        surfaceContainerLow = surfaceContainerLow.fade(),
        surfaceContainer = surfaceContainer.fade(),
        surfaceContainerHigh = surfaceContainerHigh.fade(),
        onSurfaceVariant = onSurfaceVariant.fade(),
    )
}

private const val FADE_MS = 2_000
