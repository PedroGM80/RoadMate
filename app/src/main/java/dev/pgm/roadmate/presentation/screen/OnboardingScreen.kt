package dev.pgm.roadmate.presentation.screen

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.pgm.roadmate.R
import dev.pgm.roadmate.ui.theme.Spacing

/**
 * First-run screen: the "100% offline y privado" value prop (the chosen
 * differentiator against Android Auto's built-in Gemini, which does use the
 * cloud) plus the driving-distraction disclaimer required before anyone
 * starts talking to this app in a moving car. Shown once — see
 * OnboardingRepository — then never again.
 */
@Composable
fun OnboardingScreen(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.home_tagline),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.xl),
        )

        ValuePropRow(R.drawable.lucide_ic_cloud_off, R.string.onboarding_value_privacy)
        ValuePropRow(R.drawable.lucide_ic_moon_star, R.string.onboarding_value_rest)

        Spacer(Modifier.height(Spacing.xl))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
                .padding(Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painter = painterResource(R.drawable.lucide_ic_triangle_alert),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.onboarding_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(start = Spacing.sm + Spacing.xs),
            )
        }

        Spacer(Modifier.height(Spacing.xl))

        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_continue))
        }
    }
}

@Composable
private fun ValuePropRow(@DrawableRes iconRes: Int, @StringRes textRes: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = Spacing.sm + Spacing.xs),
        )
    }
}
