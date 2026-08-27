package com.verbigem.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class VerbigemCustomColors(
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val border: Color,
    val accent: Color,
    val accentStrong: Color,
    val success: Color = SuccessGreen,
    val danger: Color = DangerRed
)

val LocalVerbigemColors = staticCompositionLocalOf {
    VerbigemCustomColors(
        bg = CalmDayBg,
        surface = CalmDaySurface,
        ink = CalmDayInk,
        muted = CalmDayMuted,
        border = CalmDayBorder,
        accent = CalmDayAccent,
        accentStrong = CalmDayAccentStrong
    )
}

object VerbigemTheme {
    val colors: VerbigemCustomColors
        @Composable
        get() = LocalVerbigemColors.current
}

@Composable
fun VerbigemAppTheme(
    themeName: String = "calm",
    modeName: String = "day",
    content: @Composable () -> Unit
) {
    val isNight = modeName == "night" || (modeName == "system" && isSystemInDarkTheme())

    val customColors = when (themeName.lowercase()) {
        "sharp" -> if (isNight) {
            VerbigemCustomColors(
                bg = SharpNightBg,
                surface = SharpNightSurface,
                ink = SharpNightInk,
                muted = SharpNightMuted,
                border = SharpNightBorder,
                accent = SharpNightAccent,
                accentStrong = SharpNightAccent
            )
        } else {
            VerbigemCustomColors(
                bg = SharpDayBg,
                surface = SharpDaySurface,
                ink = SharpDayInk,
                muted = SharpDayMuted,
                border = SharpDayBorder,
                accent = SharpDayAccent,
                accentStrong = SharpDayAccent
            )
        }
        "playful" -> if (isNight) {
            VerbigemCustomColors(
                bg = PlayfulNightBg,
                surface = PlayfulNightSurface,
                ink = PlayfulNightInk,
                muted = PlayfulNightMuted,
                border = PlayfulNightBorder,
                accent = PlayfulNightAccent,
                accentStrong = PlayfulNightAccent
            )
        } else {
            VerbigemCustomColors(
                bg = PlayfulDayBg,
                surface = PlayfulDaySurface,
                ink = PlayfulDayInk,
                muted = PlayfulDayMuted,
                border = PlayfulDayBorder,
                accent = PlayfulDayAccent,
                accentStrong = PlayfulDayAccent
            )
        }
        else -> if (isNight) { // "calm"
            VerbigemCustomColors(
                bg = CalmNightBg,
                surface = CalmNightSurface,
                ink = CalmNightInk,
                muted = CalmNightMuted,
                border = CalmNightBorder,
                accent = CalmNightAccent,
                accentStrong = CalmNightAccentStrong
            )
        } else {
            VerbigemCustomColors(
                bg = CalmDayBg,
                surface = CalmDaySurface,
                ink = CalmDayInk,
                muted = CalmDayMuted,
                border = CalmDayBorder,
                accent = CalmDayAccent,
                accentStrong = CalmDayAccentStrong
            )
        }
    }

    val materialColors = if (isNight) {
        darkColorScheme(
            primary = customColors.accent,
            onPrimary = Color.White,
            background = customColors.bg,
            surface = customColors.surface,
            onSurface = customColors.ink
        )
    } else {
        lightColorScheme(
            primary = customColors.accent,
            onPrimary = Color.White,
            background = customColors.bg,
            surface = customColors.surface,
            onSurface = customColors.ink
        )
    }

    CompositionLocalProvider(LocalVerbigemColors provides customColors) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = Typography,
            content = content
        )
    }
}
