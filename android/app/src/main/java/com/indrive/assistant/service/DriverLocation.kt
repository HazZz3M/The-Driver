package com.indrive.assistant.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * The driver's own position, for the live map: it is where inDrive draws the car, so it ties
 * inDrive's map to real coordinates (see RouteLocator). Listens to GPS and network updates while
 * started; needs the location permission (asked by the app).
 */
object DriverLocation {
    private const val TAG = "DriverLocation"

    /** Last fix [lat, lng], or null. */
    @Volatile var last: DoubleArray? = null
        private set
    @Volatile var lastAt = 0L
        private set

    private var manager: LocationManager? = null
    private val listener = LocationListener { loc -> accept(loc) }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun accept(loc: Location) {
        // A fresh network fix shouldn't replace a recent, more precise GPS one.
        if (last != null && System.currentTimeMillis() - lastAt < 10_000 && loc.provider == LocationManager.NETWORK_PROVIDER) return
        last = doubleArrayOf(loc.latitude, loc.longitude)
        lastAt = System.currentTimeMillis()
    }

    /** Starts listening (main thread). Safe to call again; does nothing without the permission. */
    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        if (manager != null || !hasPermission(context)) return
        val lm = context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        try {
            for (p in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
                if (!lm.isProviderEnabled(p)) continue
                lm.getLastKnownLocation(p)?.let { if (last == null) accept(it) }
                lm.requestLocationUpdates(p, 3000L, 5f, listener, Looper.getMainLooper())
            }
            manager = lm
        } catch (e: Exception) {
            Log.w(TAG, "Location updates unavailable", e)
        }
    }

    fun stop() {
        try { manager?.removeUpdates(listener) } catch (_: Exception) {}
        manager = null
    }

    /** A fix recent enough to place what inDrive's map shows now (the driver may be driving). */
    fun recent(maxAgeMs: Long = 60_000): DoubleArray? =
        last?.takeIf { System.currentTimeMillis() - lastAt <= maxAgeMs }
}
