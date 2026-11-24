package com.example

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.NexoScreenState
import com.example.ui.NexoViewModel
import com.example.ui.screens.ErrorScreen
import com.example.ui.screens.ErrorType
import com.example.ui.screens.LoadingScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.PackageDownloadPromptDialog
import com.example.ui.screens.PackageDownloadingDialog
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.NexoTheme

class MainActivity : ComponentActivity() {

    private val nexoViewModel: NexoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enforce landscape orientation for NEXO Battle Royale
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        enableEdgeToEdge()

        setContent {
            NexoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NexoApp(viewModel = nexoViewModel)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        nexoViewModel.onAppPaused()
    }

    override fun onResume() {
        super.onResume()
        nexoViewModel.onAppResumed()
    }
}

@Composable
fun NexoApp(viewModel: NexoViewModel = viewModel()) {
    val screenState by viewModel.screenState.collectAsState()
    val geoResult by viewModel.lastGeoResult.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()

    BackHandler(enabled = screenState != NexoScreenState.Splash) {
        if (screenState is NexoScreenState.MainLogin) {
            // Already at root login
        } else {
            viewModel.startStartupSequence()
        }
    }

    when (val state = screenState) {
        is NexoScreenState.Splash -> {
            SplashScreen()
        }

        is NexoScreenState.UpdateRequired -> {
            ErrorScreen(
                errorType = ErrorType.UPDATE_REQUIRED,
                title = "Update Required",
                message = state.message,
                primaryButtonText = "Retry",
                onPrimaryAction = { viewModel.retryFromUpdateScreen() }
            )
        }

        is NexoScreenState.InternetError -> {
            ErrorScreen(
                errorType = ErrorType.INTERNET_REQUIRED,
                title = "Internet Connection Required",
                message = state.message,
                primaryButtonText = "Retry",
                onPrimaryAction = { viewModel.retryFromInternetError() }
            )
        }

        is NexoScreenState.RegionError -> {
            ErrorScreen(
                errorType = ErrorType.REGION_UNAVAILABLE,
                title = "Download Failed",
                message = state.message,
                primaryButtonText = "OK",
                onPrimaryAction = { viewModel.retryFromRegionRestriction() }
            )
        }

        is NexoScreenState.PackagePrompt -> {
            PackageDownloadPromptDialog(
                manifest = state.manifest,
                initialOnly = state.initialOnly,
                onStartDownload = { viewModel.onDownloadConfirmed() },
                onCancel = { viewModel.onDownloadCancelled() }
            )
        }

        is NexoScreenState.Downloading -> {
            PackageDownloadingDialog(
                progress = state.downloadState
            )
        }

        is NexoScreenState.PackageError -> {
            ErrorScreen(
                errorType = ErrorType.DOWNLOAD_FAILED,
                title = "Download Failed",
                message = state.message,
                primaryButtonText = "Retry Download",
                onPrimaryAction = { viewModel.onDownloadConfirmed() }
            )
        }

        is NexoScreenState.LoadingScreen -> {
            LoadingScreen(
                progressPercent = state.progressPercent,
                statusMessage = state.statusMessage
            )
        }

        is NexoScreenState.SecurityWarning -> {
            ErrorScreen(
                errorType = ErrorType.SECURITY_OVERLAY,
                title = "Security Alert",
                message = state.status.warningMessage ?: "Suspicious overlay detected.",
                primaryButtonText = "Dismiss & Continue",
                onPrimaryAction = { viewModel.dismissSecurityWarningAndContinue() },
                secondaryButtonText = "Re-check",
                onSecondaryAction = { viewModel.recheckSecurity() }
            )
        }

        is NexoScreenState.MainLogin -> {
            LoginScreen(
                geoResult = geoResult,
                onLoginAction = { provider -> viewModel.onLoginButtonClicked(provider) },
                onUiClick = { viewModel.onUiClick() },
                toastMessage = toastMessage,
                onDismissToast = { viewModel.clearToast() }
            )
        }
    }
}
