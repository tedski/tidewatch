package com.tidewatch.ui.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
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
 * Auto-refreshes current height:
 * - Every 1 minute in active mode
 * - Every 15 minutes in ambient (AOD) mode for battery optimization
 *
 * @param viewModel The TideViewModel
 * @param isAmbient Whether the device is in ambient (AOD) mode
 * @param onNavigateToStationPicker Callback to navigate to station picker
 * @param onNavigateToDetail Callback to navigate to detail screen
 * @param onNavigateToSettings Callback to navigate to settings
 */
@Composable
fun TideMainScreen(
    viewModel: TideViewModel,
    isAmbient: Boolean = false,
    onNavigateToStationPicker: () -> Unit,
    onNavigateToDetail: () -> Unit
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
                isAmbient = isAmbient,
                onStationClick = onNavigateToStationPicker,
                onGraphClick = onNavigateToDetail,
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
    isAmbient: Boolean,
    onStationClick: () -> Unit,
    onGraphClick: () -> Unit,
    onRefresh: () -> Unit
) {
    // Auto-refresh based on ambient mode:
    // - Active mode: 1 minute (60,000 ms)
    // - Ambient mode: 15 minutes (900,000 ms) for battery optimization
    val refreshInterval = if (isAmbient) 900_000L else 60_000L

    // Lifecycle-aware auto-refresh - only refreshes when app is in foreground
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(isAmbient, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(refreshInterval)
                onRefresh()
            }
        }
    }

    // Scaling lazy list state for scroll tracking
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = {
            // Standard WearOS TimeText component - curved on round screens,
            // stays fixed at top while content scrolls underneath
            TimeText()
        },
        positionIndicator = {
            // Shows scroll position on the edge of the screen
            PositionIndicator(scalingLazyListState = listState)
        }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                top = 40.dp, // Extra padding for TimeText at top
                bottom = 32.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            autoCentering = AutoCenteringParams(itemIndex = 1) // Center on current tide height
        ) {
            // Station name (clickable in active mode only)
        item {
            Text(
                text = state.station.name,
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (!isAmbient) {
                            Modifier.clickable(onClick = onStationClick)
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 32.dp, vertical = 8.dp)
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

        // Next 2 tides (in chronological order)
        state.nextExtrema.forEach { extremum ->
            item {
                ExtremumCard(
                    extremum = extremum,
                    modifier = Modifier.fillMaxWidth(),
                    useMetric = useMetric
                )
            }
        }

        // Mini 24-hour graph
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
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
                        .height(100.dp)
                )
            }
        }

        // View detailed tides button (hidden in ambient mode)
        if (!isAmbient) {
            item {
                Chip(
                    onClick = onGraphClick,
                    label = { Text("View 7-Day Tides") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
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
