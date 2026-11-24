package com.example.ui

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AndroidNexoAudioManager
import com.example.audio.NexoAudioController
import com.example.data.downloader.DownloadState
import com.example.data.downloader.PackageDownloadManager
import com.example.data.model.PackageManifest
import com.example.data.network.AppUpdateChecker
import com.example.data.network.GeoAvailabilityChecker
import com.example.data.network.GeoResult
import com.example.data.network.NetworkMonitor
import com.example.data.network.RegionVerificationService
import com.example.data.network.UpdateCheckResult
import com.example.data.security.OverlayDetector
import com.example.data.security.OverlaySecurityStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class NexoScreenState {
    object Splash : NexoScreenState()

    data class UpdateRequired(
        val latestVersionName: String,
        val message: String,
        val updateUrl: String? = null
    ) : NexoScreenState()

    data class InternetError(
        val message: String = "Please connect to the internet to continue playing NEXO."
    ) : NexoScreenState()

    data class RegionError(
        val countryCode: String,
        val countryName: String,
        val message: String = "This game is not available in your region yet."
    ) : NexoScreenState()

    data class PackagePrompt(
        val manifest: PackageManifest,
        val initialOnly: Boolean
    ) : NexoScreenState()

    data class Downloading(
        val downloadState: DownloadState.Progress
    ) : NexoScreenState()

    data class PackageError(
        val message: String,
        val canRetry: Boolean = true
    ) : NexoScreenState()

    data class LoadingScreen(
        val progressPercent: Int,
        val statusMessage: String,
        val isFinalLoading: Boolean = false
    ) : NexoScreenState()

    data class SecurityWarning(
        val status: OverlaySecurityStatus
    ) : NexoScreenState()

    object MainLogin : NexoScreenState()
}

class NexoViewModel(application: Application) : AndroidViewModel(application) {

    private var networkConnectivityCheck: suspend () -> Boolean = {
        NetworkMonitor(application).verifyActualInternetAccess()
    }
    private var updateChecker: suspend () -> UpdateCheckResult = {
        AppUpdateChecker().checkForUpdates()
    }
    private var regionVerifier: RegionVerificationService = GeoAvailabilityChecker()
    private var audioController: NexoAudioController = AndroidNexoAudioManager(application)
    private val overlayDetector = OverlayDetector(application)
    var downloadManager = PackageDownloadManager(application)
        private set
    val manifest = PackageManifest.defaultManifest()

    private val _screenState = MutableStateFlow<NexoScreenState>(NexoScreenState.Splash)
    val screenState: StateFlow<NexoScreenState> = _screenState.asStateFlow()

    private val _lastGeoResult = MutableStateFlow<GeoResult?>(null)
    val lastGeoResult: StateFlow<GeoResult?> = _lastGeoResult.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private var startupDelayMs: Long = 2200L
    private var stepDelayMs: Long = 350L
    private var startupJob: Job? = null
    private var downloadJob: Job? = null
    private var onStateTransition: ((NexoScreenState) -> Unit)? = null

    init {
        startStartupSequence()
    }

    private fun updateState(newState: NexoScreenState) {
        if (newState !is NexoScreenState.MainLogin) {
            audioController.stopBackgroundMusic()
        }
        _screenState.value = newState
        onStateTransition?.invoke(newState)
    }

    @VisibleForTesting
    internal fun configureForTesting(
        connectivityCheck: suspend () -> Boolean,
        updateCheck: suspend () -> UpdateCheckResult,
        regionService: RegionVerificationService,
        audio: NexoAudioController,
        customDownloadManager: PackageDownloadManager? = null,
        splashDelayMs: Long = 0L,
        loadingStepDelayMs: Long = 0L,
        stateObserver: ((NexoScreenState) -> Unit)? = null
    ) {
        startupJob?.cancel()
        downloadJob?.cancel()
        networkConnectivityCheck = connectivityCheck
        updateChecker = updateCheck
        regionVerifier = regionService
        audioController = audio
        if (customDownloadManager != null) {
            downloadManager = customDownloadManager
        }
        startupDelayMs = splashDelayMs
        stepDelayMs = loadingStepDelayMs
        onStateTransition = stateObserver
    }

    /**
     * Startup entry point:
     * Splash -> Stage 1 (Checking for updates) -> Stage 2 (Loading game servers) -> Stage 3 (Live IP region check)
     */
    fun startStartupSequence() {
        startupJob?.cancel()
        downloadJob?.cancel()
        startupJob = viewModelScope.launch {
            audioController.stopBackgroundMusic()
            updateState(NexoScreenState.Splash)
            if (startupDelayMs > 0L) {
                delay(startupDelayMs)
            }
            executeStartupStages(isRegionRetry = false)
        }
    }

    /**
     * Unlimited retry from Region Restriction screen when user clicks OK.
     * Immediately returns to the loading screen with "Checking game availability...",
     * rechecks server connectivity and live public IP country without limits or cooldowns.
     */
    fun retryFromRegionRestriction() {
        audioController.playUiClick()
        startupJob?.cancel()
        startupJob = viewModelScope.launch {
            audioController.stopBackgroundMusic()
            executeStartupStages(isRegionRetry = true)
        }
    }

    fun retryFromInternetError() {
        audioController.playUiClick()
        startupJob?.cancel()
        startupJob = viewModelScope.launch {
            audioController.stopBackgroundMusic()
            executeStartupStages(isRegionRetry = false)
        }
    }

    fun retryFromUpdateScreen() {
        audioController.playUiClick()
        startupJob?.cancel()
        startupJob = viewModelScope.launch {
            audioController.stopBackgroundMusic()
            executeStartupStages(isRegionRetry = false)
        }
    }

    private suspend fun executeStartupStages(isRegionRetry: Boolean) {
        audioController.stopBackgroundMusic()

        if (!isRegionRetry) {
            // Stage 1: Checking for updates
            updateState(
                NexoScreenState.LoadingScreen(
                    progressPercent = 18,
                    statusMessage = "Checking for updates...",
                    isFinalLoading = false
                )
            )
            if (stepDelayMs > 0L) delay(stepDelayMs)

            when (val updateResult = updateChecker()) {
                is UpdateCheckResult.UpdateRequired -> {
                    updateState(
                        NexoScreenState.UpdateRequired(
                            latestVersionName = updateResult.latestVersionName,
                            message = updateResult.message,
                            updateUrl = updateResult.updateUrl
                        )
                    )
                    return
                }
                is UpdateCheckResult.UpToDate -> Unit
            }

            // Stage 2: Loading game servers & checking server connectivity
            updateState(
                NexoScreenState.LoadingScreen(
                    progressPercent = 48,
                    statusMessage = "Loading game servers...",
                    isFinalLoading = false
                )
            )
            if (stepDelayMs > 0L) delay(stepDelayMs)
        } else {
            // Region retry flow returns immediately to loading screen with exact status
            updateState(
                NexoScreenState.LoadingScreen(
                    progressPercent = 35,
                    statusMessage = "Checking game availability...",
                    isFinalLoading = false
                )
            )
            if (stepDelayMs > 0L) delay(stepDelayMs)
        }

        val hasServerConnectivity = networkConnectivityCheck()
        if (!hasServerConnectivity) {
            updateState(NexoScreenState.InternetError())
            return
        }

        // Stage 3: Live public IP-based region detection (Netherlands & Indonesia)
        updateState(
            NexoScreenState.LoadingScreen(
                progressPercent = 78,
                statusMessage = "Checking game availability...",
                isFinalLoading = false
            )
        )
        val geoResult = regionVerifier.checkPublicIpCountry()
        _lastGeoResult.value = geoResult

        if (!geoResult.isAllowed) {
            updateState(
                NexoScreenState.RegionError(
                    countryCode = geoResult.countryCode,
                    countryName = geoResult.countryName,
                    message = "This game is not available in your region yet."
                )
            )
            return
        }

        proceedAfterRegionPass()
    }

    private fun proceedAfterRegionPass() {
        audioController.stopBackgroundMusic()
        val initialPackage = manifest.initialPackage
        val allDownloaded = downloadManager.areAllPackagesDownloaded(manifest)

        if (allDownloaded) {
            startFinalLoadingScreen()
        } else if (initialPackage != null && !downloadManager.isPackageDownloaded(initialPackage)) {
            updateState(
                NexoScreenState.PackagePrompt(
                    manifest = manifest,
                    initialOnly = true
                )
            )
        } else {
            updateState(
                NexoScreenState.PackagePrompt(
                    manifest = manifest,
                    initialOnly = false
                )
            )
        }
    }

    /**
     * Triggered when user clicks the Download button on the Package Prompt or retries a failed download.
     */
    fun onDownloadConfirmed() {
        audioController.playDownloadStart()
        startPackageDownload()
    }

    fun onDownloadCancelled() {
        audioController.playUiClick()
        startFinalLoadingScreen()
    }

    fun startPackageDownload() {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            audioController.stopBackgroundMusic()
            val packagesToDownload = manifest.packages.filterNot { downloadManager.isPackageDownloaded(it) }
            val overallBytes = packagesToDownload.sumOf { it.sizeBytes }

            downloadManager.downloadPackages(packagesToDownload, overallBytes).collect { state ->
                when (state) {
                    is DownloadState.Progress -> {
                        // Never play continuous audio during package downloading
                        updateState(NexoScreenState.Downloading(state))
                    }
                    is DownloadState.Verifying -> {
                        updateState(
                            NexoScreenState.LoadingScreen(
                                progressPercent = 95,
                                statusMessage = "Verifying package integrity for ${state.currentPackage.name}...",
                                isFinalLoading = false
                            )
                        )
                    }
                    is DownloadState.Completed -> {
                        // Play distinct success sound once after validation passes, then enter login screen
                        audioController.playDownloadSuccess()
                        onPackagesValidatedNavigateToLogin()
                    }
                    is DownloadState.Error -> {
                        // Play distinct error sound once on failure
                        audioController.playDownloadError()
                        updateState(
                            NexoScreenState.PackageError(
                                message = state.errorMessage,
                                canRetry = state.canRetry
                            )
                        )
                    }
                    else -> Unit
                }
            }
        }
    }

    private suspend fun onPackagesValidatedNavigateToLogin() {
        updateState(
            NexoScreenState.LoadingScreen(
                progressPercent = 100,
                statusMessage = "Ready for Battle Royale!",
                isFinalLoading = true
            )
        )
        if (stepDelayMs > 0L) delay(stepDelayMs)
        proceedPastSecurityToLogin()
    }

    /**
     * Final resource initialization loading screen (0% -> 100%) before Main Login Screen.
     */
    fun startFinalLoadingScreen() {
        startupJob?.cancel()
        startupJob = viewModelScope.launch {
            audioController.stopBackgroundMusic()
            val steps = listOf(
                "Initializing Unreal Graphics Pipeline..." to 25,
                "Loading High-Resolution Battle Shaders..." to 55,
                "Configuring Spatial Battlefield Audio..." to 85,
                "Ready for Battle Royale!" to 100
            )

            for ((index, pair) in steps.withIndex()) {
                val (task, percent) = pair
                val isFinal = index == steps.lastIndex
                updateState(
                    NexoScreenState.LoadingScreen(
                        progressPercent = percent,
                        statusMessage = task,
                        isFinalLoading = isFinal
                    )
                )
                if (stepDelayMs > 0L) delay(stepDelayMs)
            }

            proceedPastSecurityToLogin()
        }
    }

    private fun proceedPastSecurityToLogin() {
        val securityStatus = overlayDetector.checkOverlaySecurity()
        if (!securityStatus.isSecure) {
            updateState(NexoScreenState.SecurityWarning(securityStatus))
        } else {
            updateState(NexoScreenState.MainLogin)
            audioController.startLoginBackgroundMusic()
        }
    }

    fun dismissSecurityWarningAndContinue() {
        audioController.playUiClick()
        updateState(NexoScreenState.MainLogin)
        audioController.startLoginBackgroundMusic()
    }

    fun recheckSecurity() {
        audioController.playUiClick()
        proceedPastSecurityToLogin()
    }

    fun onLoginButtonClicked(provider: String) {
        audioController.playUiClick()
        _toastMessage.value = "$provider: Coming Soon in NEXO Next Update"
    }

    fun onUiClick() {
        audioController.playUiClick()
    }

    fun clearToast() {
        audioController.playUiClick()
        _toastMessage.value = null
    }

    fun onAppPaused() {
        audioController.pauseBackgroundMusic()
    }

    fun onAppResumed() {
        if (_screenState.value is NexoScreenState.MainLogin) {
            audioController.resumeBackgroundMusic()
        } else {
            audioController.stopBackgroundMusic()
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioController.release()
    }
}
