package com.tidewatch.ui.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.tidewatch.BuildConfig
import com.tidewatch.TideViewModel

/**
 * Settings screen for app configuration.
 *
 * Displays:
 * - Units toggle (feet/meters)
 * - Current station
 * - About information
 */
@Composable
fun SettingsScreen(
    viewModel: TideViewModel,
    @Suppress("UNUSED_PARAMETER") onNavigateBack: () -> Unit
) {
    val useMetric by viewModel.useMetric.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 32.dp,
            bottom = 32.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Title
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.title1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }

        // Units section header
        item {
            Text(
                text = "Display Units",
                style = MaterialTheme.typography.caption1,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
        }

        // Feet option
        item {
            Chip(
                onClick = { viewModel.setUseMetric(false) },
                label = {
                    Text(
                        text = "Feet",
                        style = MaterialTheme.typography.body2
                    )
                },
                secondaryLabel = if (!useMetric) {
                    { Text("Selected", style = MaterialTheme.typography.caption2) }
                } else null,
                colors = if (!useMetric) {
                    ChipDefaults.primaryChipColors()
                } else {
                    ChipDefaults.secondaryChipColors()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Meters option
        item {
            Chip(
                onClick = { viewModel.setUseMetric(true) },
                label = {
                    Text(
                        text = "Meters",
                        style = MaterialTheme.typography.body2
                    )
                },
                secondaryLabel = if (useMetric) {
                    { Text("Selected", style = MaterialTheme.typography.caption2) }
                } else null,
                colors = if (useMetric) {
                    ChipDefaults.primaryChipColors()
                } else {
                    ChipDefaults.secondaryChipColors()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // About section
        item {
            Text(
                text = "About",
                style = MaterialTheme.typography.caption1,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
        }

        item {
            Card(
                onClick = { /* No action */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "TideWatch v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.body2
                    )
                    Text(
                        text = "Offline tide predictions",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "GPL-3.0 License",
                        style = MaterialTheme.typography.caption2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
