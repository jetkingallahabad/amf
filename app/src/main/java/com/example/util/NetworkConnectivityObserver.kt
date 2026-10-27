package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Observes real Android network connectivity via ConnectivityManager.NetworkCallback
 * so offline-queued collection entries automatically synchronize when internet returns.
 */
interface ConnectivityObserver {
  val isOnline: Flow<Boolean>
  fun isCurrentlyOnline(): Boolean
}

class NetworkConnectivityObserver(context: Context) : ConnectivityObserver {
  private val connectivityManager =
    context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

  override fun isCurrentlyOnline(): Boolean {
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
      caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
  }

  override val isOnline: Flow<Boolean> = callbackFlow {
    trySend(isCurrentlyOnline())

    val callback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        val caps = connectivityManager.getNetworkCapabilities(network)
        val validated = caps != null &&
          caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
          caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        if (validated) {
          trySend(true)
        } else {
          trySend(isCurrentlyOnline())
        }
      }

      override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        val online = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
          networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        trySend(online || isCurrentlyOnline())
      }

      override fun onLost(network: Network) {
        trySend(isCurrentlyOnline())
      }

      override fun onUnavailable() {
        trySend(false)
      }
    }

    val request = NetworkRequest.Builder()
      .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
      .build()

    runCatching {
      connectivityManager.registerNetworkCallback(request, callback)
    }

    awaitClose {
      runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }
  }.distinctUntilChanged()
}
