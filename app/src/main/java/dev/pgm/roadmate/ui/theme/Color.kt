package dev.pgm.roadmate.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand hues. Three roles carry meaning in the UI:
 *  - primary  = "road" blue: identity, headings.
 *  - secondary = signal amber: the voice CTA (mic) and active states.
 *  - tertiary = reply green: the assistant's answer.
 * Everything else (containers, outline, surface tones) is derived from these
 * so the whole scheme stays on-brand instead of falling back to M3 baseline
 * purple.
 */

// primary — road blue
internal val RoadBlue40 = Color(0xFF2F5FA8)
internal val RoadBlue80 = Color(0xFF9FC4FF)
internal val RoadBlue20 = Color(0xFF0B3866)
internal val RoadBlue90 = Color(0xFFD6E3FB)
internal val RoadBlue30 = Color(0xFF1E3A5F)

// secondary — signal amber
internal val Amber40 = Color(0xFFB56A00)
internal val Amber80 = Color(0xFFFFB877)
internal val Amber20 = Color(0xFF4A2800)
internal val Amber90 = Color(0xFFFFE1C2)
internal val Amber30 = Color(0xFF5A3A12)

// tertiary — reply green
internal val Green40 = Color(0xFF2E7D5B)
internal val Green80 = Color(0xFF8FD9AE)
internal val Green20 = Color(0xFF00391F)
internal val Green90 = Color(0xFFC7EED7)
internal val Green30 = Color(0xFF1C4A34)

// neutrals — cool grey (light) / near-black navy for night-driving glare (dark)
internal val GreyLightBackground = Color(0xFFF5F7FA)
internal val GreyLightSurface = Color(0xFFFFFFFF)
internal val GreyLightSurfaceVariant = Color(0xFFE7ECF2)
internal val GreyLightOnSurface = Color(0xFF1B1F24)
internal val GreyLightOnSurfaceVariant = Color(0xFF566072) // genuinely dimmed vs onSurface
internal val GreyLightOutline = Color(0xFFC0C8D2)
internal val GreyLightContainer = Color(0xFFEDF1F6)
internal val GreyLightContainerHigh = Color(0xFFE6EBF2)

internal val GreyDarkBackground = Color(0xFF10151C)
internal val GreyDarkSurface = Color(0xFF171D26)
internal val GreyDarkSurfaceVariant = Color(0xFF232B36)
internal val GreyDarkOnSurface = Color(0xFFE3E7EC)
internal val GreyDarkOnSurfaceVariant = Color(0xFFA8B2C0)
internal val GreyDarkOutline = Color(0xFF3A4452)
internal val GreyDarkContainer = Color(0xFF1F2731)
internal val GreyDarkContainerHigh = Color(0xFF2A3340)
