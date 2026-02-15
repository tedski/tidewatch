package com.tidewatch

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.wear.ambient.AmbientModeSupport
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.tidewatch.data.PreferencesRepository
import com.tidewatch.data.StationRepository
import com.tidewatch.ui.app.SettingsScreen
import com.tidewatch.ui.app.StationPickerScreen
import com.tidewatch.ui.app.TideDetailScreen
import com.tidewatch.ui.app.TideMainScreen
import com.tidewatch.ui.theme.TideWatchTheme

/**
 * Main activity for TideWatch app.
 *
 * Sets up navigation and initializes the ViewModel.
 * Supports Always-On Display (ambient mode) for battery optimization.
 */
class MainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider {

    private lateinit var ambientController: AmbientModeSupport.AmbientController
    private val isAmbient = mutableStateOf(false)

    private val viewModel: TideViewModel by viewModels {
        val app = application as TideWatchApplication
        TideViewModelFactory(
            repository = app.repository,
            application = app,
            preferencesRepository = app.preferencesRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ambient mode support
        ambientController = AmbientModeSupport.attach(this)

        // Request Play Integrity token for app authentication
        requestIntegrityToken()

        setContent {
            TideWatchTheme(isAmbient = isAmbient.value) {
                TideWatchApp(
                    viewModel = viewModel,
                    isAmbient = isAmbient.value
                )
            }
        }
    }

    /**
     * Request integrity token from Google Play to verify app authenticity.
     * This runs asynchronously and logs results without blocking app startup.
     */
    private fun requestIntegrityToken() {
        // Get Cloud Project Number from BuildConfig or use placeholder
        // TODO: Set CLOUD_PROJECT_NUMBER in build.gradle.kts after configuring Google Cloud project
        val cloudProjectNumber = CLOUD_PROJECT_NUMBER

        if (cloudProjectNumber == 0L) {
            Log.w(TAG, "Cloud Project Number not configured. Integrity checks disabled.")
            return
        }

        try {
            val integrityManager = IntegrityManagerFactory.create(this)
            val integrityTokenRequest = IntegrityTokenRequest.builder()
                .setCloudProjectNumber(cloudProjectNumber)
                .build()

            integrityManager.requestIntegrityToken(integrityTokenRequest)
                .addOnSuccessListener { response ->
                    val integrityToken = response.token()
                    Log.d(TAG, "Integrity token obtained successfully")
                    // Store or send token to backend for verification
                    // In production, send this to your backend to verify with attestation key
                }
                .addOnFailureListener { exception ->
                    Log.w(TAG, "Failed to obtain integrity token: ${exception.message}")
                    // App continues normally even if integrity check fails
                    // This allows offline usage on WearOS
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting integrity token", e)
            // App continues normally
        }
    }

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback =
        object : AmbientModeSupport.AmbientCallback() {
            override fun onEnterAmbient(ambientDetails: Bundle?) {
                super.onEnterAmbient(ambientDetails)
                isAmbient.value = true
            }

            override fun onExitAmbient() {
                super.onExitAmbient()
                isAmbient.value = false
            }

            override fun onUpdateAmbient() {
                super.onUpdateAmbient()
                // Called when ambient display needs to be updated
            }
        }
}

/**
 * Main app navigation setup.
 *
 * @param viewModel The shared TideViewModel
 * @param isAmbient Whether the device is in ambient (AOD) mode
 */
@Composable
fun TideWatchApp(
    viewModel: TideViewModel,
    isAmbient: Boolean
) {
    val navController = rememberSwipeDismissableNavController()

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = Routes.STATION_PICKER
    ) {
        composable(Routes.MAIN) {
            TideMainScreen(
                viewModel = viewModel,
                isAmbient = isAmbient,
                onNavigateToStationPicker = {
                    navController.navigate(Routes.STATION_PICKER)
                },
                onNavigateToDetail = {
                    navController.navigate(Routes.DETAIL)
                }
            )
        }

        composable(Routes.STATION_PICKER) {
            StationPickerScreen(
                viewModel = viewModel,
                onStationSelected = {
                    navController.navigate(Routes.MAIN)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(Routes.DETAIL) {
            TideDetailScreen(
                viewModel = viewModel
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

/**
 * Navigation routes.
 */
object Routes {
    const val MAIN = "main"
    const val STATION_PICKER = "station_picker"
    const val DETAIL = "detail"
    const val SETTINGS = "settings"
}

/**
 * Constants for MainActivity.
 */
private const val TAG = "MainActivity"

// TODO: Replace with your Google Cloud Project Number from Google Cloud Console
// Get it from: Google Cloud Console → Project settings → Project number
private const val CLOUD_PROJECT_NUMBER = 0L

/**
 * ViewModel factory for TideViewModel.
 */
class TideViewModelFactory(
    private val repository: StationRepository,
    private val application: TideWatchApplication,
    private val preferencesRepository: PreferencesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TideViewModel::class.java)) {
            return TideViewModel(repository, application, preferencesRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
