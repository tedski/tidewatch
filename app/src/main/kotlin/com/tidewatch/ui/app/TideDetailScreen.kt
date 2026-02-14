package com.tidewatch.ui.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.tidewatch.TideViewModel
import com.tidewatch.data.models.TideExtremum
import com.tidewatch.ui.components.ExtremumCard
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Detail screen showing extended tide information.
 *
 * Displays:
 * - 7-day extrema grouped by day
 * - Station metadata
 */
@Composable
fun TideDetailScreen(
    viewModel: TideViewModel
) {
    val state by viewModel.state.collectAsState()
    val useMetric by viewModel.useMetric.collectAsState()

    when (val currentState = state) {
        is TideViewModel.TideUiState.Success -> {
            DetailContent(
                state = currentState,
                useMetric = useMetric
            )
        }

        is TideViewModel.TideUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        else -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No data available",
                    style = MaterialTheme.typography.body1
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    state: TideViewModel.TideUiState.Success,
    useMetric: Boolean
) {
    // Group extrema by day
    val groupedExtrema = state.extrema7d.groupBy {
        it.time.atZone(ZoneId.systemDefault()).toLocalDate()
    }

    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = {
            TimeText()
        },
        positionIndicator = {
            PositionIndicator(scalingLazyListState = listState)
        }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                top = 40.dp,
                bottom = 32.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        // Title
        item {
            Text(
                text = "7-Day Tides",
                style = MaterialTheme.typography.title1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }

        groupedExtrema.forEach { (date, extremaForDay) ->
            // Day header
            item {
                Text(
                    text = formatDate(date),
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Extrema cards for this day
            items(extremaForDay) { extremum ->
                ExtremumCard(
                    extremum = extremum,
                    modifier = Modifier.fillMaxWidth(),
                    useMetric = useMetric
                )
            }
        }

        // Station metadata
        item {
            Text(
                text = "Station Info",
                style = MaterialTheme.typography.title3,
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
                    MetadataRow(label = "ID", value = state.station.id)
                    MetadataRow(
                        label = "Type",
                        value = if (state.station.isHarmonic()) "Harmonic" else "Subordinate"
                    )
                    MetadataRow(
                        label = "Location",
                        value = "%.4f, %.4f".format(
                            state.station.latitude,
                            state.station.longitude
                        )
                    )
                    if (state.station.isSubordinate() && state.station.referenceStationId != null) {
                        MetadataRow(
                            label = "Reference",
                            value = state.station.referenceStationId
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.body2
        )
    }
}

/**
 * Format date for display.
 */
private fun formatDate(date: java.time.LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("EEE, MMM d")
    return formatter.format(date)
}
