package dev.pgm.roadmate.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The 4dp spacing grid, named. Screens reference `Spacing.md` instead of a
 * bare `16.dp` so padding stays consistent and tweakable in one place.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 40.dp

    /** Minimum touch target (WCAG 2.5.5 / Android a11y). */
    val touchTarget = 48.dp
}
