package com.example.magneticcamera.app

import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.magneticcamera.core.sensors.SensorSamplingMode
import com.example.magneticcamera.data.db.SessionWithCells
import com.example.magneticcamera.ui.gallery.GalleryScreen
import com.example.magneticcamera.ui.gallery.SessionDetailScreen
import com.example.magneticcamera.ui.home.HomeScreen
import com.example.magneticcamera.ui.live.LiveMeterScreen
import com.example.magneticcamera.ui.live.LiveMeterViewModel
import com.example.magneticcamera.ui.result.HeatmapResultScreen
import com.example.magneticcamera.ui.scan.CameraCaptureScreen
import com.example.magneticcamera.ui.scan.ScanCaptureScreen
import com.example.magneticcamera.ui.scan.ScanSetupScreen
import com.example.magneticcamera.ui.scan.ScanWorkflowViewModel
import com.example.magneticcamera.ui.settings.SettingsScreen

private object Routes {
    const val Home = "home"
    const val Live = "live"
    const val ScanSetup = "scan_setup"
    const val CameraCapture = "camera_capture"
    const val ScanCapture = "scan_capture"
    const val Result = "result"
    const val Gallery = "gallery"
    const val Settings = "settings"
    const val SessionDetail = "session/{id}"
}

@Composable
fun AppNavHost(container: AppContainer) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val sensorInfo by container.sensorReader.sensorInfo.collectAsStateWithLifecycle()
    val sessions by container.repository.observeSessions().collectAsStateWithLifecycle(initialValue = emptyList())
    var showFirstLaunch by remember { mutableStateOf(container.firstLaunchStore.shouldShowExplanation()) }
    var filterAlpha by rememberSaveable { mutableFloatStateOf(0.25f) }
    var samplingMode by rememberSaveable { mutableStateOf(SensorSamplingMode.Game) }

    val liveViewModel: LiveMeterViewModel = viewModel(
        factory = SimpleViewModelFactory { LiveMeterViewModel(container.sensorReader) }
    )
    val scanViewModel: ScanWorkflowViewModel = viewModel(
        factory = SimpleViewModelFactory {
            ScanWorkflowViewModel(
                context = context,
                sensorReader = container.sensorReader,
                repository = container.repository,
                fileStore = container.fileStore,
                draftStore = container.scanDraftStore
            )
        }
    )

    LaunchedEffect(filterAlpha) {
        liveViewModel.updateFilterAlpha(filterAlpha)
        scanViewModel.updateFilterAlpha(filterAlpha)
    }
    LaunchedEffect(samplingMode) {
        liveViewModel.updateSamplingMode(samplingMode)
        scanViewModel.updateSamplingMode(samplingMode)
    }

    val liveState by liveViewModel.uiState.collectAsStateWithLifecycle()
    val scanState by scanViewModel.uiState.collectAsStateWithLifecycle()
    val cameraAvailable = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    NavHost(navController = navController, startDestination = Routes.Home) {
        composable(Routes.Home) {
            HomeScreen(
                sensorInfo = sensorInfo,
                cameraAvailable = cameraAvailable,
                showFirstLaunchExplanation = showFirstLaunch,
                onDismissFirstLaunch = {
                    showFirstLaunch = false
                    container.firstLaunchStore.markExplanationSeen()
                },
                onLiveMeter = { navController.navigate(Routes.Live) },
                onNewScan = { navController.navigate(Routes.ScanSetup) },
                onGallery = { navController.navigate(Routes.Gallery) },
                onSettings = { navController.navigate(Routes.Settings) },
                partialScanLabel = scanState.takeIf { it.isScanStarted && !it.isComplete }?.let {
                    "${it.cells.size} of ${it.totalCells} cells captured"
                },
                onResumeScan = { navController.navigate(Routes.ScanCapture) },
                onDiscardPartialScan = scanViewModel::discardScan
            )
        }

        composable(Routes.Live) {
            LiveMeterScreen(
                state = liveState,
                onStart = liveViewModel::start,
                onStop = liveViewModel::stop,
                onBack = { navController.popBackStack() },
                onSetBaseline = liveViewModel::setBaseline,
                onFreeze = liveViewModel::toggleFreeze,
                onSaveSnapshot = liveViewModel::saveSnapshot,
                onStartScan = {
                    scanViewModel.adoptBaseline(liveState.baseline)
                    navController.navigate(Routes.ScanSetup)
                }
            )
        }

        composable(Routes.ScanSetup) {
            ScanSetupScreen(
                state = scanState,
                onStartSensor = scanViewModel::startSensor,
                onStopSensor = scanViewModel::stopSensor,
                onBack = { navController.popBackStack() },
                onNameChange = scanViewModel::updateName,
                onGridSizeChange = scanViewModel::updateGridSize,
                onGridDimensionsChange = scanViewModel::updateGridDimensions,
                onPhotoChoiceChange = scanViewModel::updatePhotoChoice,
                onCaptureModeChange = scanViewModel::updateCaptureMode,
                onCalibrateBaseline = scanViewModel::calibrateBaseline,
                onTakePhoto = { navController.navigate(Routes.CameraCapture) },
                onImportPhoto = scanViewModel::importPhoto,
                onOverlayAreaChange = scanViewModel::updateOverlayArea,
                onBeginScan = {
                    if (scanViewModel.beginScan()) {
                        navController.navigate(Routes.ScanCapture)
                    }
                }
            )
        }

        composable(Routes.CameraCapture) {
            CameraCaptureScreen(
                photoFileProvider = scanViewModel::preparePhotoFile,
                onPhotoSaved = { uri ->
                    scanViewModel.setPhotoUri(uri)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
                onSkipPhoto = {
                    scanViewModel.updatePhotoChoice(false)
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.ScanCapture) {
            ScanCaptureScreen(
                state = scanState,
                onStartSensor = scanViewModel::startSensor,
                onStopSensor = scanViewModel::stopSensor,
                onBack = { navController.popBackStack() },
                onCapture = scanViewModel::captureCurrentCell,
                onRedo = scanViewModel::redoPreviousCell,
                onSkip = scanViewModel::skipCurrentCell,
                onDiscard = {
                    scanViewModel.discardScan()
                    navController.popBackStack(Routes.ScanSetup, inclusive = false)
                },
                onViewResult = { navController.navigate(Routes.Result) }
            )
        }

        composable(Routes.Result) {
            HeatmapResultScreen(
                state = scanState,
                onBack = { navController.popBackStack() },
                onPaletteChange = scanViewModel::updatePalette,
                onNormalizationChange = scanViewModel::updateNormalization,
                onOpacityChange = scanViewModel::updateOpacity,
                onShowLegendChange = scanViewModel::updateShowLegend,
                onSave = scanViewModel::saveResult,
                onGallery = { navController.navigate(Routes.Gallery) }
            )
        }

        composable(Routes.Gallery) {
            GalleryScreen(
                sessions = sessions,
                onBack = { navController.popBackStack() },
                onNewScan = { navController.navigate(Routes.ScanSetup) },
                onOpenSession = { id -> navController.navigate("session/$id") }
            )
        }

        composable(
            route = Routes.SessionDetail,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id").orEmpty()
            var detail by remember(id) { mutableStateOf<SessionWithCells?>(null) }
            LaunchedEffect(id) {
                detail = container.repository.getSession(id)
            }
            SessionDetailScreen(
                sessionWithCells = detail,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.Settings) {
            SettingsScreen(
                sensorInfo = sensorInfo,
                filterAlpha = filterAlpha,
                onFilterAlphaChange = { filterAlpha = it },
                samplingMode = samplingMode,
                onSamplingModeChange = { samplingMode = it },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
