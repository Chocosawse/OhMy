package com.ohmy.zfsync.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraWifiException(message: String) : Exception(message)

/**
 * Joins the Wi-Fi access point hosted by the camera itself (the SSID/password shown on the
 * camera's screen), without touching the phone's default Wi-Fi network or requiring internet
 * capability. Uses [WifiNetworkSpecifier] (Android 10+) so the connection is scoped to this app.
 */
class CameraWifiManager(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var activeCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Uses the 2-argument [ConnectivityManager.requestNetwork] overload deliberately: the
     * 3-argument (timeout) overload requires the privileged CHANGE_NETWORK_STATE/WRITE_SETTINGS
     * permission, which a normal third-party app cannot hold. The timeout below is enforced in
     * app code instead via [withTimeout].
     */
    suspend fun connect(ssid: String, password: String, timeoutMs: Long = 30_000): Network {
        try {
            return withTimeout(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    val specifier = WifiNetworkSpecifier.Builder()
                        .setSsid(ssid)
                        .setWpa2Passphrase(password)
                        .build()

                    val request = NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .setNetworkSpecifier(specifier)
                        .build()

                    val callback = object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            if (cont.isActive) cont.resume(network)
                        }

                        override fun onUnavailable() {
                            if (cont.isActive) {
                                cont.resumeWithException(CameraWifiException("Could not join Wi-Fi network \"$ssid\""))
                            }
                        }
                    }
                    activeCallback = callback
                    cont.invokeOnCancellation {
                        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
                    }
                    connectivityManager.requestNetwork(request, callback)
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw CameraWifiException("Timed out joining Wi-Fi network \"$ssid\"")
        }
    }

    /** The camera's IP address on its own Wi-Fi network, resolved from the default route's gateway. */
    fun getCameraIpAddress(network: Network): String? {
        val linkProperties = connectivityManager.getLinkProperties(network) ?: return null
        val defaultRoute = linkProperties.routes.firstOrNull { it.isDefaultRoute }
        return defaultRoute?.gateway?.hostAddress
    }

    fun disconnect() {
        activeCallback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        activeCallback = null
    }
}
