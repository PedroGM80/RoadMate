package dev.pgm.roadmate.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = RoadBlue80,
    onPrimary = RoadBlueOnLight,
    secondary = SignalAmber80,
    onSecondary = SignalAmberOnLight,
    tertiary = ReplyGreen80,
    onTertiary = ReplyGreenOnLight,
    background = NeutralBackgroundDark,
    onBackground = NeutralOnSurfaceDark,
    surface = NeutralSurfaceDark,
    onSurface = NeutralOnSurfaceDark,
    surfaceVariant = NeutralSurfaceVariantDark,
    onSurfaceVariant = NeutralOnSurfaceDark
)

private val LightColorScheme = lightColorScheme(
    primary = RoadBlue40,
    onPrimary = NeutralSurfaceLight,
    secondary = SignalAmber40,
    onSecondary = NeutralSurfaceLight,
    tertiary = ReplyGreen40,
    onTertiary = NeutralSurfaceLight,
    background = NeutralBackgroundLight,
    onBackground = NeutralOnSurfaceLight,
    surface = NeutralSurfaceLight,
    onSurface = NeutralOnSurfaceLight,
    surfaceVariant = NeutralSurfaceVariantLight,
    onSurfaceVariant = NeutralOnSurfaceLight
)

@Composable
fun RoadMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default: a deliberate brand palette (tuned for night-driving
    // glare and a legible mic-CTA accent) beats inheriting whatever colors
    // happen to come from the user's wallpaper. Still available to flip on.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
