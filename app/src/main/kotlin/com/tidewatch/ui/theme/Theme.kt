package com.tidewatch.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.MaterialTheme

/**
 * TideWatch app theme.
 *
 * Applies Material You color scheme and typography optimized for WearOS.
 *
 * @param isAmbient Whether the device is in Always-On Display (ambient) mode
 * @param content Composable content to theme
 */
@Composable
fun TideWatchTheme(
    isAmbient: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (isAmbient) AodColors else TideWatchColors

    MaterialTheme(
        colors = colors,
        typography = TideWatchTypography,
        content = content
    )
}
