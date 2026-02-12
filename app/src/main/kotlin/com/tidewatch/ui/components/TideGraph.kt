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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.tidewatch.data.models.TideHeight
import com.tidewatch.ui.theme.Primary
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
            .height(100.dp) // Increased height to accommodate labels below
    ) {
        val width = size.width
        val height = size.height

        // Reserve space at bottom for time labels
        val labelHeight = 20.dp.toPx()
        val graphHeight = height - labelHeight

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
            // Calculate x position based on time
            val timeOffset = (tide.time.epochSecond - startTime).toDouble()
            val x = ((timeOffset / timeRange) * width).toFloat()

            // Calculate y position based on height (using graphHeight instead of height)
            val normalizedHeight = ((tide.height - minHeight) / heightRange).toFloat()
            val y = graphHeight - (normalizedHeight * graphHeight) // Invert y-axis

            Offset(x, y)
        }

        // Draw tide curve with simple lines for debugging
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
        val zeroY = graphHeight - ((0.0 - minHeight) / heightRange).toFloat() * graphHeight
        if (zeroY in 0f..graphHeight) {
            drawLine(
                color = Color.Gray.copy(alpha = 0.5f),
                start = Offset(0f, zeroY),
                end = Offset(width, zeroY),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Draw time axis ticks and labels
        val startInstant = tideData.first().time
        val endInstant = tideData.last().time
        val zoneId = ZoneId.systemDefault()
        val timeFormatter = DateTimeFormatter.ofPattern("ha").withZone(zoneId)

        // Calculate tick positions for every 6 hours
        val startHour = startInstant.atZone(zoneId).hour
        val firstTickHour = ((startHour / 6) * 6) // Round down to nearest 6-hour mark
        var tickTime = startInstant.atZone(zoneId)
            .withHour(firstTickHour)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .toInstant()

        // If first tick is before start time, advance to next 6-hour mark
        if (tickTime.isBefore(startInstant)) {
            tickTime = tickTime.plusSeconds(6 * 3600)
        }

        // Draw ticks every 6 hours
        while (tickTime.isBefore(endInstant) || tickTime == endInstant) {
            val timeOffset = (tickTime.epochSecond - startTime).toDouble()
            val x = ((timeOffset / timeRange) * width).toFloat()

            // Draw tick mark from bottom of graph extending down
            drawLine(
                color = Color.Gray.copy(alpha = 0.7f),
                start = Offset(x, graphHeight),
                end = Offset(x, graphHeight + 6.dp.toPx()),
                strokeWidth = 1.dp.toPx()
            )

            // Draw time label below the tick mark
            val label = timeFormatter.format(tickTime).lowercase()
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = Color.Gray.copy(alpha = 0.8f).toArgb()
                    textSize = 10.dp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                // Position text below the graph, centered on the tick
                drawText(label, x, graphHeight + 16.dp.toPx(), paint)
            }

            tickTime = tickTime.plusSeconds(6 * 3600)
        }
    }
}
