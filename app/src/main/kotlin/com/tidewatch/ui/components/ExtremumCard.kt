package com.tidewatch.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.tidewatch.data.models.TideExtremum
import com.tidewatch.ui.theme.HighTideColor
import com.tidewatch.ui.theme.LowTideColor
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Displays a tide extremum (high or low) in a card.
 *
 * @param extremum The tide extremum to display
 * @param modifier Modifier for the card
 * @param useMetric Whether to display height in meters (true) or feet (false)
 */
@Composable
fun ExtremumCard(
    extremum: TideExtremum,
    modifier: Modifier = Modifier,
    useMetric: Boolean = false
) {
    val color = if (extremum.isHigh()) HighTideColor else LowTideColor
    val label = if (extremum.isHigh()) "High" else "Low"

    Card(
        onClick = { /* Future: Navigate to detail view */ },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type and time
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.caption1,
                    color = color
                )
                Text(
                    text = formatTime(extremum),
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface
                )
            }

            // Height
            Text(
                text = formatHeight(extremum.height, useMetric),
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.onSurface,
                textAlign = TextAlign.End
            )
        }
    }
}

/**
 * Format extremum time for display.
 */
private fun formatTime(extremum: TideExtremum): String {
    val formatter = DateTimeFormatter.ofPattern("h:mm a")
    val localTime = extremum.time.atZone(ZoneId.systemDefault())
    return formatter.format(localTime)
}

/**
 * Format height for display.
 */
private fun formatHeight(height: Double, useMetric: Boolean): String {
    return if (useMetric) {
        val meters = height * 0.3048 // Convert feet to meters
        "%.1f m".format(meters)
    } else {
        "%.1f ft".format(height)
    }
}
