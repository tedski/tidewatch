package com.tidewatch.ui.app

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.wear.compose.material.*
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
    onStationSelected: () -> Unit
) {
    var mode by remember { mutableStateOf(PickerMode.NEARBY) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Mode selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Chip(
                onClick = { mode = PickerMode.NEARBY },
                label = { Text("Nearby") },
                colors = if (mode == PickerMode.NEARBY) {
                    ChipDefaults.primaryChipColors()
                } else {
                    ChipDefaults.secondaryChipColors()
                },
                modifier = Modifier.weight(1f)
            )
            Chip(
                onClick = { mode = PickerMode.BROWSE },
                label = { Text("Browse") },
                colors = if (mode == PickerMode.BROWSE) {
                    ChipDefaults.primaryChipColors()
                } else {
                    ChipDefaults.secondaryChipColors()
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Content based on mode
        when (mode) {
            PickerMode.NEARBY -> NearbyMode(
                viewModel = viewModel,
                onStationSelected = { station ->
                    viewModel.selectStation(station.id)
                    onStationSelected()
                }
            )
            PickerMode.BROWSE -> BrowseMode(
                viewModel = viewModel,
                onStationSelected = { station ->
                    viewModel.selectStation(station.id)
                    onStationSelected()
                }
            )
        }
    }
}

private enum class PickerMode {
    NEARBY,
    BROWSE
}

@Composable
private fun NearbyMode(
    viewModel: TideViewModel,
    onStationSelected: (Station) -> Unit
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
                    text = "Grant location permission to find nearby stations",
                    style = MaterialTheme.typography.body2,
                    textAlign = TextAlign.Center
                )
                Chip(
                    onClick = {
                        permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                    label = { Text("Grant Permission") },
                    colors = ChipDefaults.primaryChipColors()
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
    onStationSelected: (Station) -> Unit
) {
    val allStates by viewModel.allStates.collectAsState()
    val browseStations by viewModel.browseStations.collectAsState()
    var selectedState by remember { mutableStateOf<String?>(null) }

    if (selectedState == null) {
        // Show state list
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 32.dp,
                bottom = 32.dp
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
                    top = 32.dp,
                    bottom = 32.dp
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
