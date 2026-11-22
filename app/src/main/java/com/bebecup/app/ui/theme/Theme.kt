package com.bebecup.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/** 설정 > 화면 테마. Light is the default: a dark system theme no longer turns BebeCup dark by itself. */
enum class ThemeMode(val storageKey: String, val label: String) {
    LIGHT("light", "밝게"),
    DARK("dark", "어둡게"),
    SYSTEM("system", "휴대폰 설정 따르기");

    companion object {
        val DEFAULT = LIGHT
        fun fromStorageKey(key: String?): ThemeMode = entries.firstOrNull { it.storageKey == key } ?: DEFAULT
    }
}

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

private val DarkColorScheme = darkColorScheme(
    primary = NightSkyDarkPrimary,
    onPrimary = NightSkyDarkBackground,
    primaryContainer = NightPeachContainer,
    onPrimaryContainer = NightText,
    secondary = NightSkyDarkPrimary,
    onSecondary = NightSkyDarkBackground,
    secondaryContainer = NightSandContainer,
    onSecondaryContainer = NightText,
    tertiary = ButterGoldTertiary,
    background = NightSkyDarkBackground,
    onBackground = NightText,
    surface = NightSkyDarkSurface,
    onSurface = NightText,
    surfaceVariant = NightVariant,
    onSurfaceVariant = NightText,
    surfaceContainerLowest = NightSkyDarkBackground,
    surfaceContainerLow = NightSkyDarkSurface,
    surfaceContainer = NightSkyDarkSurface,
    surfaceContainerHigh = NightVariant,
    surfaceContainerHighest = NightVariant,
    outline = NightSkyOutline,
    outlineVariant = NightSkyOutline,
)

private val LightColorScheme = lightColorScheme(
    primary = SoftPeachPrimary,
    onPrimary = CardWhite,
    primaryContainer = PeachContainer,
    onPrimaryContainer = CozyBrownText,
    secondary = WarmSandSecondary,
    onSecondary = CozyBrownText,
    secondaryContainer = SandContainer,
    onSecondaryContainer = CozyBrownText,
    tertiary = ButterGoldTertiary,
    onTertiary = CozyBrownText,
    tertiaryContainer = ButterContainer,
    onTertiaryContainer = CozyBrownText,
    background = SoftCreamBackground,
    onBackground = CozyBrownText,
    surface = CardWhite,
    onSurface = CozyBrownText,
    surfaceVariant = CreamVariant,
    onSurfaceVariant = SoftMutedText,
    // Material's defaults here are lavender greys; keep dialogs, menus and sheets in the cream family.
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = SoftCreamBackground,
    surfaceContainer = SoftCreamBackground,
    surfaceContainerHigh = CreamVariant,
    surfaceContainerHighest = CreamVariant,
    surfaceTint = SoftPeachPrimary,
    outline = OutlineWarm,
    outlineVariant = OutlineWarm,
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
