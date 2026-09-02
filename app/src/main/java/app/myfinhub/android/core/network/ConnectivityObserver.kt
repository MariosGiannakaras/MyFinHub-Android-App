package app.myfinhub.android.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class NetworkStatus {
    ONLINE,
    OFFLINE,
    UNKNOWN,
}

interface ConnectivityObserver {
    val status: Flow<NetworkStatus>
    fun current(): NetworkStatus
}

class AndroidConnectivityObserver(context: Context) : ConnectivityObserver {
    private val connectivityManager = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override val status: Flow<NetworkStatus> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(current())
            }

            override fun onLost(network: Network) {
                trySend(current())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(statusOf(networkCapabilities))
            }

            override fun onUnavailable() {
                trySend(NetworkStatus.OFFLINE)
            }
        }

        trySend(current())
        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { runCatching { connectivityManager.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    override fun current(): NetworkStatus {
        val network = connectivityManager.activeNetwork ?: return NetworkStatus.OFFLINE
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkStatus.UNKNOWN
        return statusOf(capabilities)
    }

    private fun statusOf(capabilities: NetworkCapabilities): NetworkStatus = when {
        !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkStatus.OFFLINE
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> NetworkStatus.ONLINE
        else -> NetworkStatus.UNKNOWN
    }
}
