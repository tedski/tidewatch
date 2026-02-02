package com.tidewatch.ui.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.tidewatch.TideViewModel
import com.tidewatch.ui.components.ExtremumCard
import com.tidewatch.ui.components.TideDirectionIndicator
import com.tidewatch.ui.components.TideGraph
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Main screen showing current tide conditions.
 *
 * Displays:
 * - Station name (clickable to change)
 * - Current tide height (large)
 * - Direction indicator
 * - Next high/low cards
 * - Mini 24-hour graph (clickable for detail)
 *
 * Auto-refreshes current height every minute.
 */
@Composable
fun TideMainScreen(
    viewModel: TideViewModel,
    onNavigateToStationPicker: () -> Unit,
    onNavigateToDetail: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val useMetric by viewModel.useMetric.collectAsState()

    when (val currentState = state) {
        is TideViewModel.TideUiState.Loading -> {
            LoadingScreen()
        }

        is TideViewModel.TideUiState.NoStationSelected -> {
            NoStationScreen(onSelectStation = onNavigateToStationPicker)
        }

        is TideViewModel.TideUiState.Success -> {
            SuccessScreen(
                state = currentState,
                useMetric = useMetric,
                onStationClick = onNavigateToStationPicker,
                onGraphClick = onNavigateToDetail,
                onSettingsClick = onNavigateToSettings,
                onRefresh = { viewModel.refreshCurrentHeight() }
            )
        }

        is TideViewModel.TideUiState.Error -> {
            ErrorScreen(
                message = currentState.message,
                onRetry = onNavigateToStationPicker
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NoStationScreen(onSelectStation: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "No Station Selected",
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center
            )
            Chip(
                onClick = onSelectStation,
                label = { Text("Select Station") },
                colors = ChipDefaults.primaryChipColors()
            )
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center
            )
            Chip(
                onClick = onRetry,
                label = { Text("Try Another Station") },
                colors = ChipDefaults.primaryChipColors()
            )
        }
    }
}

@Composable
private fun SuccessScreen(
    state: TideViewModel.TideUiState.Success,
    useMetric: Boolean,
    onStationClick: () -> Unit,
    onGraphClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRefresh: () -> Unit
) {
    // Auto-refresh every minute
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000) // 1 minute
            onRefresh()
        }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 32.dp,
            bottom = 32.dp,
            start = 10.dp,
            end = 10.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Station name (clickable)
        item {
            Text(
                text = state.station.name,
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onStationClick)
                    .padding(vertical = 8.dp)
            )
        }

        // Current tide height (LARGE)
        item {
            Text(
                text = formatHeight(state.currentHeight.height, useMetric),
                style = MaterialTheme.typography.display1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Direction indicator
        item {
            TideDirectionIndicator(
                tideHeight = state.currentHeight,
                modifier = Modifier.fillMaxWidth(),
                useMetric = useMetric
            )
        }

        // Next high tide
        if (state.nextHigh != null) {
            item {
                ExtremumCard(
                    extremum = state.nextHigh,
                    modifier = Modifier.fillMaxWidth(),
                    useMetric = useMetric
                )
            }
        }

        // Next low tide
        if (state.nextLow != null) {
            item {
                ExtremumCard(
                    extremum = state.nextLow,
                    modifier = Modifier.fillMaxWidth(),
                    useMetric = useMetric
                )
            }
        }

        // Mini 24-hour graph (clickable)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onGraphClick)
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "24 Hour Forecast",
                    style = MaterialTheme.typography.caption1,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                TideGraph(
                    tideData = state.curve24h,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                )
            }
        }

        // Settings button
        item {
            Chip(
                onClick = onSettingsClick,
                label = { Text("Settings") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Format height for display with units.
 */
private fun formatHeight(height: Double, useMetric: Boolean): String {
    return if (useMetric) {
        val meters = height * 0.3048 // Convert feet to meters
        "%.1f m".format(meters)
    } else {
        "%.1f ft".format(height)
    }
}
