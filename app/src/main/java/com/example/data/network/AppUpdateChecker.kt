package com.example.data.network

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class UpdateCheckResult {
    data class UpToDate(
        val installedVersionName: String,
        val installedVersionCode: Int
    ) : UpdateCheckResult()

    data class UpdateRequired(
        val installedVersionName: String,
        val latestVersionName: String,
        val message: String,
        val updateUrl: String? = null
    ) : UpdateCheckResult()
}

/**
 * Stage 1 startup check: verifies whether the installed APK version meets the minimum/latest requirement.
 * Uses configurable HTTPS version manifest when set; otherwise validates installed client version metadata.
 */
class AppUpdateChecker(
    private val installedVersionName: String = BuildConfig.VERSION_NAME,
    private val installedVersionCode: Int = BuildConfig.VERSION_CODE,
    private val updateManifestUrl: String? = null,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()
) {
    suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val url = updateManifestUrl?.takeIf { it.startsWith("https://") }
        if (url != null) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "NexoBattleRoyale-Client/$installedVersionName")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val minRequiredCode = json.optInt("minRequiredVersionCode", installedVersionCode)
                        val latestVersionName = json.optString("latestVersionName", installedVersionName)
                        val downloadUrl = json.optString("downloadUrl", "").ifBlank { null }

                        if (installedVersionCode < minRequiredCode) {
                            return@withContext UpdateCheckResult.UpdateRequired(
                                installedVersionName = installedVersionName,
                                latestVersionName = latestVersionName,
                                message = "A newer version of NEXO (v$latestVersionName) is required to continue.",
                                updateUrl = downloadUrl
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // If update manifest endpoint is unreachable or unconfigured, proceed to server connectivity stage
            }
        }

        // Validate installed build metadata locally when no mandatory remote update blocks launch
        val isValidVersion = installedVersionCode >= 1 && installedVersionName.isNotBlank()
        if (!isValidVersion) {
            UpdateCheckResult.UpdateRequired(
                installedVersionName = installedVersionName,
                latestVersionName = "0.1.0",
                message = "Your NEXO installation version could not be verified. Please reinstall the latest APK."
            )
        } else {
            UpdateCheckResult.UpToDate(
                installedVersionName = installedVersionName,
                installedVersionCode = installedVersionCode
            )
        }
    }
}
