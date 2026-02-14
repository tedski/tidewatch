package com.tidewatch.ui.app

import android.Manifest
import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.material.*
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Scaffold
import com.google.android.gms.location.LocationServices
import com.tidewatch.TideViewModel
import com.tidewatch.data.StationRepository
import com.tidewatch.data.models.Station
import com.tidewatch.ui.components.StationList

/**
 * Station selection screen.
 *
 * Two modes:
 * - Nearby: Find stations near current GPS location
 * - Browse: Browse stations by state
 */
@Composable
fun StationPickerScreen(
    viewModel: TideViewModel,
    onStationSelected: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    // Restore saved picker state
    val savedMode by viewModel.pickerMode.collectAsState()
    val savedSelectedState by viewModel.pickerSelectedState.collectAsState()

    var currentScreen by remember {
        mutableStateOf<PickerScreen>(
            when (savedMode) {
                "nearby" -> PickerScreen.Nearby
                "browse" -> PickerScreen.Browse
                else -> PickerScreen.Start
            }
        )
    }

    Scaffold(
        timeText = { TimeText() }
    ) {
        when (currentScreen) {
            is PickerScreen.Start -> StartScreen(
                onNearbyClick = {
                    currentScreen = PickerScreen.Nearby
                    viewModel.savePickerState("nearby", null)
                },
                onBrowseClick = {
                    currentScreen = PickerScreen.Browse
                    viewModel.savePickerState("browse", null)
                },
                onSettingsClick = onNavigateToSettings
            )
            is PickerScreen.Nearby -> NearbyMode(
                viewModel = viewModel,
                onStationSelected = { station ->
                    viewModel.selectStation(station.id)
                    viewModel.savePickerState("nearby", null)
                    onStationSelected()
                },
                onBackClick = {
                    currentScreen = PickerScreen.Start
                    viewModel.clearPickerState()
                }
            )
            is PickerScreen.Browse -> BrowseMode(
                viewModel = viewModel,
                initialSelectedState = savedSelectedState,
                onStationSelected = { station ->
                    viewModel.selectStation(station.id)
                    viewModel.savePickerState("browse", savedSelectedState)
                    onStationSelected()
                },
                onBackClick = {
                    currentScreen = PickerScreen.Start
                    viewModel.clearPickerState()
                }
            )
        }
    }
}

private sealed class PickerScreen {
    object Start : PickerScreen()
    object Nearby : PickerScreen()
    object Browse : PickerScreen()
}

@Composable
private fun StartScreen(
    onNearbyClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        autoCentering = AutoCenteringParams(itemIndex = 1) // Center on the Nearby button
    ) {
        item {
            Text(
                text = "Find Tide Stations",
                style = MaterialTheme.typography.title2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        item {
            Chip(
                onClick = onNearbyClick,
                label = { Text("Nearby Stations") },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Chip(
                onClick = onBrowseClick,
                label = { Text("Browse All Stations") },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
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

@Composable
private fun NearbyMode(
    viewModel: TideViewModel,
    onStationSelected: (Station) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val nearbyStations by viewModel.nearbyStations.collectAsState()
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var isLoadingLocation by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
        if (isGranted) {
            loadLocationAndStations(context, viewModel) { isLoadingLocation = false }
            isLoadingLocation = true
        }
    }

    if (!hasLocationPermission) {
        // Handle back press to return to start screen
        BackHandler(onBack = onBackClick)

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
                    text = "Location Permission Needed",
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Grant location permission to find nearby stations, or use Browse mode",
                    style = MaterialTheme.typography.body2,
                    textAlign = TextAlign.Center
                )
                Chip(
                    onClick = {
                        try {
                            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        } catch (e: Exception) {
                            // Permission request failed (common on emulators)
                            // User should use Browse mode instead
                            android.util.Log.e("StationPicker", "Permission request failed", e)
                        }
                    },
                    label = { Text("Grant Permission") },
                    colors = ChipDefaults.primaryChipColors()
                )
                Text(
                    text = "Note: On emulator, use Browse mode",
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    } else if (isLoadingLocation) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (nearbyStations.isEmpty()) {
        // Load location on first display
        LaunchedEffect(Unit) {
            loadLocationAndStations(context, viewModel) { isLoadingLocation = false }
            isLoadingLocation = true
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        StationList(
            stations = nearbyStations,
            onStationSelected = onStationSelected,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@SuppressLint("MissingPermission")
private fun loadLocationAndStations(
    context: android.content.Context,
    viewModel: TideViewModel,
    onComplete: () -> Unit
) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
        if (location != null) {
            viewModel.loadNearbyStations(location.latitude, location.longitude)
        }
        onComplete()
    }.addOnFailureListener {
        onComplete()
    }
}

@Composable
private fun BrowseMode(
    viewModel: TideViewModel,
    initialSelectedState: String?,
    onStationSelected: (Station) -> Unit,
    onBackClick: () -> Unit
) {
    val allStates by viewModel.allStates.collectAsState()
    val browseStations by viewModel.browseStations.collectAsState()
    var selectedState by remember { mutableStateOf(initialSelectedState) }

    // Load stations if returning to a previously selected state
    LaunchedEffect(initialSelectedState) {
        if (initialSelectedState != null) {
            viewModel.loadStationsByState(initialSelectedState)
        }
    }

    if (selectedState == null) {
        // Handle back press to return to start screen
        BackHandler(onBack = onBackClick)

        // Show state list
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 40.dp,
                start = 8.dp,
                end = 8.dp,
                bottom = 40.dp
            )
        ) {
            item {
                Text(
                    text = "Select State",
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }

            items(allStates) { state ->
                Chip(
                    onClick = {
                        selectedState = state
                        viewModel.loadStationsByState(state)
                        viewModel.savePickerState("browse", state)
                    },
                    label = {
                        Text(
                            text = state,
                            style = MaterialTheme.typography.body2
                        )
                    },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        // Handle back press to return to state list
        BackHandler(onBack = {
            selectedState = null
            viewModel.savePickerState("browse", null)
        })

        // Show stations for selected state
        if (browseStations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 40.dp,
                    start = 8.dp,
                    end = 8.dp,
                    bottom = 40.dp
                )
            ) {
                item {
                    Text(
                        text = selectedState!!,
                        style = MaterialTheme.typography.title3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

                items(browseStations) { station ->
                    Chip(
                        onClick = { onStationSelected(station) },
                        label = {
                            Text(
                                text = station.name,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.body2
                            )
                        },
                        secondaryLabel = {
                            Text(
                                text = station.state,
                                style = MaterialTheme.typography.caption2
                            )
                        },
                        colors = ChipDefaults.primaryChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
