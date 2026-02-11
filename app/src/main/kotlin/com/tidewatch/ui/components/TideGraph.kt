package com.tidewatch.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tidewatch.data.models.TideHeight
import com.tidewatch.ui.theme.Primary

/**
 * Displays a tide curve graph.
 *
 * Shows tide heights over time as a line graph.
 *
 * @param tideData List of tide heights to plot
 * @param modifier Modifier for the graph
 * @param lineColor Color of the tide curve line
 */
@Composable
fun TideGraph(
    tideData: List<TideHeight>,
    modifier: Modifier = Modifier,
    lineColor: Color = Primary
) {
    if (tideData.isEmpty()) return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        val width = size.width
        val height = size.height

        // Find min/max heights for scaling
        val minHeight = tideData.minOf { it.height }
        val maxHeight = tideData.maxOf { it.height }
        val heightRange = maxHeight - minHeight

        if (heightRange == 0.0) return@Canvas // Avoid division by zero

        // Find time range for proper x-axis scaling
        val startTime = tideData.first().time.epochSecond
        val endTime = tideData.last().time.epochSecond
        val timeRange = (endTime - startTime).toDouble()

        if (timeRange == 0.0) return@Canvas // Avoid division by zero

        // Calculate points using time-based x-positioning
        val points = tideData.map { tide ->
            // Calculate x position based on time (not index)
            val timeOffset = (tide.time.epochSecond - startTime).toDouble()
            val x = ((timeOffset / timeRange) * width).toFloat()

            // Calculate y position based on height
            val normalizedHeight = ((tide.height - minHeight) / heightRange).toFloat()
            val y = height - (normalizedHeight * height) // Invert y-axis

            Offset(x, y)
        }

        // Draw tide curve
        if (points.size >= 2) {
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Draw baseline (MLLW = 0)
        val zeroY = height - ((0.0 - minHeight) / heightRange).toFloat() * height
        if (zeroY in 0f..height) {
            drawLine(
                color = Color.Gray.copy(alpha = 0.5f),
                start = Offset(0f, zeroY),
                end = Offset(width, zeroY),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}
