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
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val useMetric by viewModel.useMetric.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 32.dp,
            bottom = 32.dp,
            start = 10.dp,
            end = 10.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Title
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.title1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Units toggle
        item {
            ToggleChip(
                checked = useMetric,
                onCheckedChange = { viewModel.setUseMetric(it) },
                label = {
                    Text(
                        text = if (useMetric) "Meters" else "Feet",
                        style = MaterialTheme.typography.body2
                    )
                },
                toggleControl = {
                    ToggleChipToggleControl(
                        checked = useMetric
                    )
                },
                colors = ToggleChipDefaults.toggleChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Current station (if selected)
        if (state is TideViewModel.TideUiState.Success) {
            item {
                Text(
                    text = "Current Station",
                    style = MaterialTheme.typography.caption1,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
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
                        val station = (state as TideViewModel.TideUiState.Success).station
                        Text(
                            text = station.name,
                            style = MaterialTheme.typography.body2
                        )
                        Text(
                            text = station.state,
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
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
