package dev.pgm.roadmate.ui.theme

import androidx.compose.ui.graphics.Color

// "Road" blue — the brand/primary color. Confident, legible against both
// asphalt-dark and daylight contexts.
val RoadBlue40 = Color(0xFF2F5FA8)
val RoadBlue80 = Color(0xFF9FC4FF)
val RoadBlueOnLight = Color(0xFF0B3866)

// Amber — the voice/CTA accent (mic button, active states). High contrast
// against RoadBlue, reads as "listening/signal" without feeling alarming.
val SignalAmber40 = Color(0xFFB56A00)
val SignalAmber80 = Color(0xFFFFB877)
val SignalAmberOnLight = Color(0xFF4A2800)

// Muted green — reserved for the assistant's response (tertiary), a calm
// counterpoint to the amber CTA.
val ReplyGreen40 = Color(0xFF2E7D5B)
val ReplyGreen80 = Color(0xFF8FD9AE)
val ReplyGreenOnLight = Color(0xFF00391F)

// Neutrals — background skews toward near-black navy in dark mode (glare
// reduction for night driving), cool light gray in light mode.
val NeutralBackgroundLight = Color(0xFFF5F7FA)
val NeutralSurfaceLight = Color(0xFFFFFFFF)
val NeutralSurfaceVariantLight = Color(0xFFE7ECF2)
val NeutralOnSurfaceLight = Color(0xFF1B1F24)

val NeutralBackgroundDark = Color(0xFF10151C)
val NeutralSurfaceDark = Color(0xFF171D26)
val NeutralSurfaceVariantDark = Color(0xFF232B36)
val NeutralOnSurfaceDark = Color(0xFFE3E7EC)
