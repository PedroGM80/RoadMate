package dev.pgm.roadmate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = RoadBlue40,
    onPrimary = GreyLightSurface,
    primaryContainer = RoadBlue90,
    onPrimaryContainer = RoadBlue20,
    secondary = Amber40,
    onSecondary = GreyLightSurface,
    secondaryContainer = Amber90,
    onSecondaryContainer = Amber20,
    tertiary = Green40,
    onTertiary = GreyLightSurface,
    tertiaryContainer = Green90,
    onTertiaryContainer = Green20,
    background = GreyLightBackground,
    onBackground = GreyLightOnSurface,
    surface = GreyLightSurface,
    onSurface = GreyLightOnSurface,
    surfaceVariant = GreyLightSurfaceVariant,
    onSurfaceVariant = GreyLightOnSurfaceVariant,
    surfaceContainer = GreyLightContainer,
    surfaceContainerHigh = GreyLightContainerHigh,
    outline = GreyLightOutline,
    outlineVariant = GreyLightSurfaceVariant,
)

private val DarkColors = darkColorScheme(
    primary = RoadBlue80,
    onPrimary = RoadBlue20,
    primaryContainer = RoadBlue30,
    onPrimaryContainer = RoadBlue90,
    secondary = Amber80,
    onSecondary = Amber20,
    secondaryContainer = Amber30,
    onSecondaryContainer = Amber90,
    tertiary = Green80,
    onTertiary = Green20,
    tertiaryContainer = Green30,
    onTertiaryContainer = Green90,
    background = GreyDarkBackground,
    onBackground = GreyDarkOnSurface,
    surface = GreyDarkSurface,
    onSurface = GreyDarkOnSurface,
    surfaceVariant = GreyDarkSurfaceVariant,
    onSurfaceVariant = GreyDarkOnSurfaceVariant,
    surfaceContainer = GreyDarkContainer,
    surfaceContainerHigh = GreyDarkContainerHigh,
    outline = GreyDarkOutline,
    outlineVariant = GreyDarkSurfaceVariant,
)

/**
 * A fixed brand palette — no dynamic/Material You colour. The scheme is tuned
 * for night-driving glare and a legible mic CTA; inheriting wallpaper colours
 * would undo that.
 */
@Composable
fun RoadMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}

/** White that works on both the amber and the (red) error mic-button fills. */
val OnSignal = Color.White
