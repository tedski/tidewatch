package com.tidewatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
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
 */
class MainActivity : ComponentActivity() {

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

        setContent {
            TideWatchTheme {
                TideWatchApp(viewModel = viewModel)
            }
        }
    }
}

/**
 * Main app navigation setup.
 */
@Composable
fun TideWatchApp(viewModel: TideViewModel) {
    val navController = rememberSwipeDismissableNavController()

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = Routes.MAIN
    ) {
        composable(Routes.MAIN) {
            TideMainScreen(
                viewModel = viewModel,
                onNavigateToStationPicker = {
                    navController.navigate(Routes.STATION_PICKER)
                },
                onNavigateToDetail = {
                    navController.navigate(Routes.DETAIL)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(Routes.STATION_PICKER) {
            StationPickerScreen(
                viewModel = viewModel,
                onStationSelected = {
                    navController.popBackStack()
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
