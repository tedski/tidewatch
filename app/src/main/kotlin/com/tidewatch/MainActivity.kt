package com.tidewatch

import android.os.Bundle
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
import com.tidewatch.data.PreferencesRepository
import com.tidewatch.data.StationRepository
import com.tidewatch.tide.HarmonicCalculator
import com.tidewatch.tide.TideCache
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
            calculator = app.calculator,
            cache = app.cache,
            preferencesRepository = app.preferencesRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ambient mode support
        ambientController = AmbientModeSupport.attach(this)

        setContent {
            TideWatchTheme(isAmbient = isAmbient.value) {
                TideWatchApp(
                    viewModel = viewModel,
                    isAmbient = isAmbient.value
                )
            }
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
 * ViewModel factory for TideViewModel.
 */
class TideViewModelFactory(
    private val repository: StationRepository,
    private val calculator: HarmonicCalculator,
    private val cache: TideCache,
    private val preferencesRepository: PreferencesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TideViewModel::class.java)) {
            return TideViewModel(repository, calculator, cache, preferencesRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
