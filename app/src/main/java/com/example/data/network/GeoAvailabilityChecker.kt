package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GeoResult(
    val countryCode: String,
    val countryName: String,
    val isAllowed: Boolean,
    val verifiedByServer: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Architectural contract for future authoritative server-side region validation.
 * Client IP pre-check never trusts user-selected countries, and the game server
 * must independently validate the incoming public IP / signed session token.
 */
interface RegionVerificationService {
    suspend fun checkPublicIpCountry(): GeoResult
}

interface ServerRegionAuthority {
    suspend fun validateAccessOnServer(clientCountryCode: String): GeoResult
}

class GeoAvailabilityChecker(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build(),
    private val serverAuthority: ServerRegionAuthority? = null
) : RegionVerificationService {

    // Initial launch countries: Netherlands (NL) and Indonesia (ID)
    val allowedCountryCodes: Set<String> = setOf("NL", "ID")

    fun evaluateCountryEligibility(countryCode: String): Boolean {
        val normalized = countryCode.trim().uppercase()
        return normalized.isNotEmpty() && allowedCountryCodes.contains(normalized)
    }

    /**
     * Checks ONLY the apparent public internet IP country via HTTPS IP geolocation.
     * - Does NOT use GPS, device Locale, SIM country, Wi-Fi location, or cell location.
     * - Allows VPNs and proxies; eligibility depends strictly on the public IP's country.
     * - Never accepts any client-side simulation or manual country override.
     */
    override suspend fun checkPublicIpCountry(): GeoResult = withContext(Dispatchers.IO) {
        val endpoints = listOf(
            "https://ipapi.co/json/" to { json: JSONObject ->
                val code = json.optString("country_code", "").trim().uppercase()
                val name = json.optString("country_name", code.ifEmpty { "Unknown Region" })
                code to name
            },
            "https://api.country.is/" to { json: JSONObject ->
                val code = json.optString("country", "").trim().uppercase()
                val name = when (code) {
                    "NL" -> "Netherlands"
                    "ID" -> "Indonesia"
                    else -> code.ifEmpty { "Unknown Region" }
                }
                code to name
            },
            "https://ipwho.is/" to { json: JSONObject ->
                val success = json.optBoolean("success", true)
                val code = if (success) json.optString("country_code", "").trim().uppercase() else ""
                val name = json.optString("country", code.ifEmpty { "Unknown Region" })
                code to name
            }
        )

        for ((url, parser) in endpoints) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "NexoBattleRoyale-Client/0.1")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            val json = JSONObject(body)
                            val (code, name) = parser(json)
                            if (code.length == 2) {
                                // Future server-side hook: delegate authoritative decision if wired
                                serverAuthority?.let { authority ->
                                    return@withContext authority.validateAccessOnServer(code)
                                }
                                return@withContext GeoResult(
                                    countryCode = code,
                                    countryName = name,
                                    isAllowed = evaluateCountryEligibility(code),
                                    verifiedByServer = false
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Fall through to next HTTPS IP lookup endpoint
            }
        }

        GeoResult(
            countryCode = "UNKNOWN",
            countryName = "Unavailable",
            isAllowed = false,
            verifiedByServer = false,
            errorMessage = "Unable to verify service availability for your network. Please try again."
        )
    }
}
