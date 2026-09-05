package dev.pgm.roadmate.ui.theme

import androidx.compose.ui.unit.dp

/** Standard icon box sizes. Screens use these instead of a bare `20.dp`. */
object IconSize {
    val sm = 16.dp
    val md = 20.dp
    val lg = 24.dp
    val xl = 32.dp
}

/** Surface elevation steps, named so cards and sheets stay consistent. */
object Elevation {
    val low = 2.dp
    val medium = 3.dp
}

/** One-off sizes that don't belong to a scale but shouldn't be inline literals. */
object Dimens {
    val progressStroke = 2.dp
    val listeningDot = 10.dp
    val micButton = 80.dp
    val micPulseRing = 96.dp
    val statusLabelMaxWidth = 220.dp
    val routeDividerWidth = 24.dp
}
