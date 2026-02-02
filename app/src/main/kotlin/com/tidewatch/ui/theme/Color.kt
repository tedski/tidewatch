package com.tidewatch.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors

/**
 * Color palette for TideWatch app.
 *
 * Uses Material You dynamic colors with fallbacks for WearOS.
 */

// Primary colors (blue tones for water/ocean theme)
val Primary = Color(0xFF1E88E5)
val PrimaryVariant = Color(0xFF1565C0)
val Secondary = Color(0xFF26C6DA)
val SecondaryVariant = Color(0xFF00ACC1)

// Background colors
val BackgroundDark = Color(0xFF000000)
val SurfaceDark = Color(0xFF1A1A1A)

// Status colors
val HighTideColor = Color(0xFF4CAF50)  // Green for high tide
val LowTideColor = Color(0xFFFF9800)   // Orange for low tide
val RisingColor = Color(0xFF2196F3)     // Blue for rising tide
val FallingColor = Color(0xFFF44336)    // Red for falling tide
val SlackColor = Color(0xFF9E9E9E)      // Gray for slack tide

// AOD colors (high contrast)
val AodForeground = Color.White
val AodBackground = Color.Black

/**
 * WearOS color scheme (dark theme optimized).
 */
val TideWatchColors = Colors(
    primary = Primary,
    primaryVariant = PrimaryVariant,
    secondary = Secondary,
    secondaryVariant = SecondaryVariant,
    background = BackgroundDark,
    surface = SurfaceDark,
    error = Color(0xFFCF6679),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onError = Color.Black
)

/**
 * AOD-specific colors (minimal, high contrast).
 */
val AodColors = Colors(
    primary = Color.White,
    primaryVariant = Color.White,
    secondary = Color.White,
    secondaryVariant = Color.White,
    background = Color.Black,
    surface = Color.Black,
    error = Color.White,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onError = Color.Black
)
