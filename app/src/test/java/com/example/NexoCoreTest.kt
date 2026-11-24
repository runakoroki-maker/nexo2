package com.example

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.audio.NexoAudioController
import com.example.data.downloader.PackageDownloadManager
import com.example.data.model.PackageManifest
import com.example.data.network.AppUpdateChecker
import com.example.data.network.GeoAvailabilityChecker
import com.example.data.network.GeoResult
import com.example.data.network.RegionVerificationService
import com.example.data.network.UpdateCheckResult
import com.example.ui.NexoScreenState
import com.example.ui.NexoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NexoCoreTest {

    private lateinit var application: Application

    private class FakeAudioController : NexoAudioController {
        var uiClickCount = 0
        var downloadStartCount = 0
        var downloadSuccessCount = 0
        var downloadErrorCount = 0
        var loginBgmStartedCount = 0
        var stopBgmCount = 0
        var bgmAllowedInStage = false
        override var isBgmPlaying: Boolean = false
            private set
        override val isBgmAssetPresent: Boolean = true

        override fun playUiClick() {
            uiClickCount++
        }

        override fun playDownloadStart() {
            downloadStartCount++
        }

        override fun playDownloadSuccess() {
            downloadSuccessCount++
        }

        override fun playDownloadError() {
            downloadErrorCount++
        }

        override fun startLoginBackgroundMusic() {
            loginBgmStartedCount++
            bgmAllowedInStage = true
            isBgmPlaying = true
        }

        override fun stopBackgroundMusic() {
            stopBgmCount++
            bgmAllowedInStage = false
            isBgmPlaying = false
        }

        override fun pauseBackgroundMusic() {
            isBgmPlaying = false
        }

        override fun resumeBackgroundMusic() {
            if (bgmAllowedInStage) {
                isBgmPlaying = true
            }
        }

        override fun release() {
            bgmAllowedInStage = false
            isBgmPlaying = false
        }
    }

    private fun fastOfflineHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(Interceptor { throw IOException("Offline test stub") })
            .build()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        application = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial package is 2MB`() {
        val manifest = PackageManifest.defaultManifest()
        val initial = manifest.initialPackage
        assertNotNull("Initial package must exist", initial)
        assertEquals("Initial package must be 2MB", 2 * 1024 * 1024L, initial!!.sizeBytes)
        assertTrue("isInitial flag must be true", initial.isInitial)
    }

    @Test
    fun `permitted regions are strictly Netherlands and Indonesia`() {
        val checker = GeoAvailabilityChecker()
        assertEquals(setOf("NL", "ID"), checker.allowedCountryCodes)
        assertTrue(checker.evaluateCountryEligibility("NL"))
        assertTrue(checker.evaluateCountryEligibility("nl"))
        assertTrue(checker.evaluateCountryEligibility("ID"))
        assertTrue(checker.evaluateCountryEligibility("id"))
        assertFalse(checker.evaluateCountryEligibility("US"))
        assertFalse(checker.evaluateCountryEligibility("GB"))
        assertFalse(checker.evaluateCountryEligibility("UNKNOWN"))
        assertFalse(checker.evaluateCountryEligibility(""))
    }

    @Test
    fun `manifest has no location permissions and bundled audio and icon assets exist`() {
        val packageInfo = application.packageManager.getPackageInfo(
            application.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requested = packageInfo.requestedPermissions?.toSet().orEmpty()
        assertFalse(requested.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertFalse(requested.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertTrue(requested.contains(Manifest.permission.INTERNET))
        assertTrue(requested.contains(Manifest.permission.ACCESS_NETWORK_STATE))

        // Sound effects and login background music exist in res/raw
        val res = application.resources
        assertTrue(res.getIdentifier("sfx_ui_click", "raw", application.packageName) != 0)
        assertTrue(res.getIdentifier("sfx_download_start", "raw", application.packageName) != 0)
        assertTrue(res.getIdentifier("sfx_download_success", "raw", application.packageName) != 0)
        assertTrue(res.getIdentifier("sfx_download_error", "raw", application.packageName) != 0)
        assertTrue(res.getIdentifier("bgm_main_theme", "raw", application.packageName) != 0)

        // Launcher icon resources exist
        assertTrue(res.getIdentifier("ic_launcher", "mipmap", application.packageName) != 0)
        assertTrue(res.getIdentifier("ic_launcher_round", "mipmap", application.packageName) != 0)
        assertTrue(res.getIdentifier("ic_nexo_launcher_artwork", "drawable", application.packageName) != 0)
    }

    @Test
    fun `startup sequence runs updates then servers then live region check in order`() = runBlocking {
        val audio = FakeAudioController()
        val observedStates = mutableListOf<NexoScreenState>()
        val viewModel = NexoViewModel(application)
        viewModel.configureForTesting(
            connectivityCheck = { true },
            updateCheck = { UpdateCheckResult.UpToDate("2.0", 2) },
            regionService = object : RegionVerificationService {
                override suspend fun checkPublicIpCountry() = GeoResult(
                    countryCode = "NL",
                    countryName = "Netherlands",
                    isAllowed = true
                )
            },
            audio = audio,
            customDownloadManager = PackageDownloadManager(application, fastOfflineHttpClient(), simulatedChunkDelayMs = 0L),
            splashDelayMs = 0L,
            loadingStepDelayMs = 0L,
            stateObserver = { observedStates.add(it) }
        )
        viewModel.downloadManager.clearDownloadedPackages()

        viewModel.startStartupSequence()
        awaitState(viewModel) { it is NexoScreenState.PackagePrompt }

        val loadingStatuses = observedStates
            .filterIsInstance<NexoScreenState.LoadingScreen>()
            .map { it.statusMessage }

        val updateIndex = loadingStatuses.indexOf("Checking for updates...")
        val serversIndex = loadingStatuses.indexOf("Loading game servers...")
        val regionIndex = loadingStatuses.indexOf("Checking game availability...")

        assertTrue("Expected Stage 1 update check message", updateIndex >= 0)
        assertTrue("Expected Stage 2 server check after update check", serversIndex > updateIndex)
        assertTrue("Expected Stage 3 region check after server check", regionIndex > serversIndex)
        assertEquals(0, audio.loginBgmStartedCount)
        assertFalse(audio.isBgmPlaying)
        assertTrue(audio.stopBgmCount > 0)
    }

    @Test
    fun `update required stops launch at UpdateRequired screen`() = runBlocking {
        val audio = FakeAudioController()
        val viewModel = NexoViewModel(application)
        viewModel.configureForTesting(
            connectivityCheck = { true },
            updateCheck = {
                UpdateCheckResult.UpdateRequired(
                    installedVersionName = "0.1",
                    latestVersionName = "0.2",
                    message = "Please update NEXO to v0.2"
                )
            },
            regionService = object : RegionVerificationService {
                override suspend fun checkPublicIpCountry() = GeoResult("NL", "Netherlands", true)
            },
            audio = audio
        )

        viewModel.startStartupSequence()
        val state = awaitState(viewModel) { it is NexoScreenState.UpdateRequired }
        assertTrue(state is NexoScreenState.UpdateRequired)
        assertEquals("0.2", (state as NexoScreenState.UpdateRequired).latestVersionName)
        assertEquals(0, audio.loginBgmStartedCount)
        assertFalse(audio.isBgmPlaying)
    }

    @Test
    fun `region restriction allows unlimited OK retries without limit or cooldown`() = runBlocking {
        val audio = FakeAudioController()
        var currentCountry = GeoResult("US", "United States", isAllowed = false)
        var geoChecks = 0
        val retryStatuses = mutableListOf<String>()
        var recordRetryStatuses = false

        val viewModel = NexoViewModel(application)
        viewModel.configureForTesting(
            connectivityCheck = { true },
            updateCheck = { UpdateCheckResult.UpToDate("2.0", 2) },
            regionService = object : RegionVerificationService {
                override suspend fun checkPublicIpCountry(): GeoResult {
                    geoChecks++
                    return currentCountry
                }
            },
            audio = audio,
            customDownloadManager = PackageDownloadManager(application, fastOfflineHttpClient(), simulatedChunkDelayMs = 0L),
            stateObserver = { state ->
                if (recordRetryStatuses && state is NexoScreenState.LoadingScreen) {
                    retryStatuses.add(state.statusMessage)
                }
            }
        )
        viewModel.downloadManager.clearDownloadedPackages()

        viewModel.startStartupSequence()
        awaitState(viewModel) { it is NexoScreenState.RegionError }
        recordRetryStatuses = true

        // Perform 6 consecutive retries while still in unsupported region
        repeat(6) { attempt ->
            val beforeChecks = geoChecks
            viewModel.retryFromRegionRestriction()
            awaitCondition { geoChecks > beforeChecks }
            val state = awaitState(viewModel) { it is NexoScreenState.RegionError }
            assertEquals("US", (state as NexoScreenState.RegionError).countryCode)
            assertEquals(attempt + 1, audio.uiClickCount)
        }

        assertTrue(
            "Retry must show Checking game availability...",
            retryStatuses.contains("Checking game availability...")
        )

        // Now user IP changes to Indonesia -> retry succeeds immediately
        currentCountry = GeoResult("ID", "Indonesia", isAllowed = true)
        viewModel.retryFromRegionRestriction()
        val passedState = awaitState(viewModel) { it is NexoScreenState.PackagePrompt }

        assertTrue(passedState is NexoScreenState.PackagePrompt)
        assertEquals(7, audio.uiClickCount)
        assertEquals(0, audio.loginBgmStartedCount)
        assertFalse(audio.isBgmPlaying)
    }

    @Test
    fun `package download triggers start and success SFX once and plays BGM only on MainLogin`() = runBlocking {
        val audio = FakeAudioController()
        val preLoginBgmStates = mutableListOf<Pair<NexoScreenState, Boolean>>()
        val viewModel = NexoViewModel(application)
        viewModel.configureForTesting(
            connectivityCheck = { true },
            updateCheck = { UpdateCheckResult.UpToDate("2.0", 2) },
            regionService = object : RegionVerificationService {
                override suspend fun checkPublicIpCountry() = GeoResult("ID", "Indonesia", true)
            },
            audio = audio,
            customDownloadManager = PackageDownloadManager(application, fastOfflineHttpClient(), simulatedChunkDelayMs = 0L),
            stateObserver = { state ->
                if (state !is NexoScreenState.MainLogin) {
                    preLoginBgmStates.add(state to audio.isBgmPlaying)
                }
            }
        )
        viewModel.downloadManager.clearDownloadedPackages()

        viewModel.startStartupSequence()
        awaitState(viewModel) { it is NexoScreenState.PackagePrompt }

        // No BGM during startup or package prompt, even on pause/resume
        viewModel.onAppPaused()
        viewModel.onAppResumed()
        assertEquals(0, audio.loginBgmStartedCount)
        assertFalse(audio.isBgmPlaying)
        assertEquals(0, audio.downloadStartCount)
        assertEquals(0, audio.downloadSuccessCount)

        viewModel.onDownloadConfirmed()
        assertEquals(1, audio.downloadStartCount)

        val loginState = awaitState(viewModel, timeoutMs = 10_000L) {
            it is NexoScreenState.MainLogin
        }
        assertTrue(loginState is NexoScreenState.MainLogin)
        assertEquals(1, audio.downloadSuccessCount)
        assertEquals(0, audio.downloadErrorCount)

        // Ensure BGM was never playing on any pre-login screen (including final loading 100%)
        assertTrue(preLoginBgmStates.isNotEmpty())
        assertTrue(preLoginBgmStates.none { (_, playing) -> playing })

        // BGM plays only once MainLogin is reached
        assertEquals(1, audio.loginBgmStartedCount)
        assertTrue(audio.isBgmPlaying)

        // Pause and resume while on MainLogin keeps BGM lifecycle synced
        viewModel.onAppPaused()
        assertFalse(audio.isBgmPlaying)
        viewModel.onAppResumed()
        assertTrue(audio.isBgmPlaying)

        // Leaving MainLogin immediately stops BGM and stays stopped through startup/package prompt
        viewModel.downloadManager.clearDownloadedPackages()
        preLoginBgmStates.clear()
        viewModel.startStartupSequence()
        awaitState(viewModel) { it is NexoScreenState.PackagePrompt }
        assertTrue(preLoginBgmStates.none { (_, playing) -> playing })
        assertFalse(audio.isBgmPlaying)
    }

    @Test
    fun `app update checker returns UpToDate for valid build metadata`() = runBlocking {
        assertEquals("2.0", BuildConfig.VERSION_NAME)
        assertEquals(2, BuildConfig.VERSION_CODE)
        val checker = AppUpdateChecker()
        val result = checker.checkForUpdates()
        assertTrue(result is UpdateCheckResult.UpToDate)
    }

    private suspend fun awaitState(
        viewModel: NexoViewModel,
        timeoutMs: Long = 5_000L,
        predicate: (NexoScreenState) -> Boolean
    ): NexoScreenState {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val current = viewModel.screenState.value
            if (predicate(current)) return current
            delay(10)
        }
        throw AssertionError("Timed out waiting for expected state; last state=${viewModel.screenState.value}")
    }

    private suspend fun awaitCondition(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            delay(10)
        }
        throw AssertionError("Timed out waiting for condition")
    }
}
