package com.example.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class NetworkMonitor(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Checks if device network interface is enabled.
     */
    fun hasNetworkInterface(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Step 2: Actively tests actual internet accessibility (not just Wi-Fi or mobile data toggle).
     * Connects via raw socket to reliable public DNS (8.8.8.8:53 or 1.1.1.1:53) with a short timeout.
     */
    suspend fun verifyActualInternetAccess(): Boolean = withContext(Dispatchers.IO) {
        if (!hasNetworkInterface()) {
            return@withContext false
        }
        try {
            val socket = Socket()
            val socketAddress = InetSocketAddress("8.8.8.8", 53)
            socket.connect(socketAddress, 3000)
            socket.close()
            true
        } catch (e: IOException) {
            // Fallback probe to alternate address
            try {
                val fallbackSocket = Socket()
                val fallbackAddress = InetSocketAddress("1.1.1.1", 53)
                fallbackSocket.connect(fallbackAddress, 3000)
                fallbackSocket.close()
                true
            } catch (fallbackEx: IOException) {
                false
            }
        }
    }
}
