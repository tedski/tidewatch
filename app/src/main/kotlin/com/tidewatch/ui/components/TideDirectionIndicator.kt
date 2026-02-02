package com.tidewatch.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.tidewatch.data.models.TideHeight
import com.tidewatch.ui.theme.FallingColor
import com.tidewatch.ui.theme.RisingColor
import com.tidewatch.ui.theme.SlackColor
import kotlin.math.abs

/**
 * Displays tide direction with an arrow and rate of change.
 *
 * @param tideHeight Current tide height with direction information
 * @param modifier Modifier for the component
 * @param useMetric Whether to display rate in m/hr (true) or ft/hr (false)
 */
@Composable
fun TideDirectionIndicator(
    tideHeight: TideHeight,
    modifier: Modifier = Modifier,
    useMetric: Boolean = false
) {
    val (icon, color, label) = when (tideHeight.direction) {
        TideHeight.Direction.RISING -> Triple(
            Icons.Default.ArrowUpward,
            RisingColor,
            "Rising"
        )
        TideHeight.Direction.FALLING -> Triple(
            Icons.Default.ArrowDownward,
            FallingColor,
            "Falling"
        )
        TideHeight.Direction.SLACK -> Triple(
            null,
            SlackColor,
            "Slack"
        )
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Direction arrow
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Direction label and rate
        Column(
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.caption1,
                color = color
            )

            // Rate of change
            val rateText = formatRate(tideHeight.rateOfChange, useMetric)
            Text(
                text = rateText,
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Format rate of change for display.
 */
private fun formatRate(rate: Double, useMetric: Boolean): String {
    val displayRate = if (useMetric) rate * 0.3048 else rate
    val absRate = abs(displayRate)
    val unit = if (useMetric) "m/hr" else "ft/hr"

    return if (absRate < 0.01) {
        "~0 $unit"
    } else {
        "${if (displayRate > 0) "+" else ""}%.2f $unit".format(displayRate)
    }
}
