package dev.pgm.roadmate.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when the user has animations turned off (Developer options / a11y
 * "Remove animations"). Decorative loops — the mic pulse, the waveform —
 * check this and hold still, which also matters for a driving app where
 * peripheral motion is a distraction.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
