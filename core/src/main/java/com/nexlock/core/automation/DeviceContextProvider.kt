package com.nexlock.core.automation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.nexlock.common.util.NexLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal, permission-aware device context used by automation rules.
 *
 * Wi-Fi SSID and location are only read when the user explicitly enabled the corresponding
 * automation rule **and** granted the runtime permission; every failure degrades to `null`
 * instead of throwing.
 */
@Singleton
class DeviceContextProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    fun currentSsid(): String? {
        if (!hasLocationPermission()) return null
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return null
            val ssid = wifiManager.connectionInfo?.ssid ?: return null
            ssid.removeSurrounding("\"").takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        } catch (e: SecurityException) {
            NexLog.w(TAG, "Location permission required to read the Wi-Fi SSID")
            null
        } catch (e: Exception) {
            NexLog.w(TAG, "Unable to read Wi-Fi state", e)
            null
        }
    }

    fun lastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null
        return try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            providers.asSequence()
                .filter { manager.isProviderEnabled(it) }
                .mapNotNull { provider ->
                    runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
                }
                .maxByOrNull { it.time }
        } catch (e: SecurityException) {
            NexLog.w(TAG, "Location permission missing")
            null
        } catch (e: Exception) {
            NexLog.w(TAG, "Unable to read last known location", e)
            null
        }
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val result = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, result)
        return result[0]
    }

    companion object {
        private const val TAG = "DeviceContext"
    }
}
