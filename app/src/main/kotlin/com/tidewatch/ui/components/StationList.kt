package com.tidewatch.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.tidewatch.data.StationRepository
import com.tidewatch.data.models.Station

/**
 * Displays a scrollable list of tide stations.
 *
 * @param stations List of stations to display
 * @param onStationSelected Callback when a station is selected
 * @param modifier Modifier for the list
 */
@Composable
fun StationList(
    stations: List<StationRepository.StationWithDistance>,
    onStationSelected: (Station) -> Unit,
    modifier: Modifier = Modifier
) {
    ScalingLazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            top = 32.dp,
            bottom = 32.dp
        )
    ) {
        items(stations) { stationWithDistance ->
            StationChip(
                stationWithDistance = stationWithDistance,
                onClick = { onStationSelected(stationWithDistance.station) }
            )
        }
    }
}

/**
 * A chip displaying a single station.
 */
@Composable
private fun StationChip(
    stationWithDistance: StationRepository.StationWithDistance,
    onClick: () -> Unit
) {
    val station = stationWithDistance.station
    val distance = stationWithDistance.distanceMiles

    Chip(
        onClick = onClick,
        label = {
            Text(
                text = station.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.body2
            )
        },
        secondaryLabel = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = station.state,
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = "%.1f mi".format(distance),
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        colors = ChipDefaults.primaryChipColors(),
        modifier = Modifier.fillMaxWidth()
    )
}
