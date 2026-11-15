package com.example.data.local

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.data.model.UserLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

object LocationHelper {

  fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
    return fine == android.content.pm.PackageManager.PERMISSION_GRANTED ||
      coarse == android.content.pm.PackageManager.PERMISSION_GRANTED
  }

  fun isLocationEnabled(context: Context): Boolean {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        lm.isLocationEnabled
      } else {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
          lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
      }
    } catch (_: Exception) {
      false
    }
  }

  @SuppressLint("MissingPermission")
  suspend fun detectCurrentLocation(context: Context): Result<UserLocation> {
    if (!hasLocationPermission(context)) {
      return Result.failure(Exception("Location permission was not given"))
    }
    if (!isLocationEnabled(context)) {
      return Result.failure(Exception("Please turn on GPS / Location services"))
    }

    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
      ?: return Result.failure(Exception("Location service unavailable"))

    val location: Location? = withTimeoutOrNull(10_000L) {
      requestFreshLocationWithContext(context, lm)
    } ?: getBestLastKnownLocation(lm)

    if (location == null) {
      return Result.failure(Exception("Could not get GPS fix. Please check GPS signal or enter your area manually."))
    }

    val geocoded = reverseGeocode(context, location.latitude, location.longitude)
    return Result.success(geocoded)
  }

  @SuppressLint("MissingPermission")
  private suspend fun requestFreshLocationWithContext(context: Context, lm: LocationManager): Location? {
    val provider = when {
      lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
      lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
      else -> return null
    }

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      suspendCancellableCoroutine { cont ->
        val cancellationSignal = android.os.CancellationSignal()
        cont.invokeOnCancellation { cancellationSignal.cancel() }
        try {
          lm.getCurrentLocation(
            provider,
            cancellationSignal,
            ContextCompat.getMainExecutor(context)
          ) { loc ->
            if (cont.isActive) cont.resume(loc)
          }
        } catch (_: Exception) {
          if (cont.isActive) cont.resume(getBestLastKnownLocation(lm))
        }
      }
    } else {
      suspendCancellableCoroutine { cont ->
        val listener = object : LocationListener {
          override fun onLocationChanged(location: Location) {
            try { lm.removeUpdates(this) } catch (_: Exception) {}
            if (cont.isActive) cont.resume(location)
          }

          @Deprecated("Deprecated in Java")
          override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
          override fun onProviderEnabled(provider: String) {}
          override fun onProviderDisabled(provider: String) {}
        }
        cont.invokeOnCancellation {
          try { lm.removeUpdates(listener) } catch (_: Exception) {}
        }
        try {
          @Suppress("DEPRECATION")
          lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        } catch (_: Exception) {
          if (cont.isActive) cont.resume(getBestLastKnownLocation(lm))
        }
      }
    }
  }

  @SuppressLint("MissingPermission")
  private fun getBestLastKnownLocation(lm: LocationManager): Location? {
    val providers = listOf(
      LocationManager.GPS_PROVIDER,
      LocationManager.NETWORK_PROVIDER,
      LocationManager.PASSIVE_PROVIDER
    )
    var best: Location? = null
    for (p in providers) {
      val loc = try {
        lm.getLastKnownLocation(p)
      } catch (_: Exception) {
        null
      } ?: continue
      if (best == null || loc.time > best.time) {
        best = loc
      }
    }
    return best
  }

  private suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): UserLocation =
    withContext(Dispatchers.IO) {
      try {
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          suspendCancellableCoroutine { cont ->
            try {
              geocoder.getFromLocation(lat, lng, 5) { list ->
                if (cont.isActive) cont.resume(list)
              }
            } catch (_: Exception) {
              if (cont.isActive) cont.resume(emptyList())
            }
          }
        } else {
          @Suppress("DEPRECATION")
          geocoder.getFromLocation(lat, lng, 5) ?: emptyList()
        }

        val addr = addresses.firstOrNull {
          (!it.subLocality.isNullOrBlank() || !it.thoroughfare.isNullOrBlank()) &&
            (!it.locality.isNullOrBlank() || !it.subAdminArea.isNullOrBlank())
        } ?: addresses.firstOrNull()

        if (addr == null) {
          return@withContext UserLocation(
            area = String.format(Locale.ROOT, "%.4f, %.4f", lat, lng),
            fullAddress = "",
            lat = lat,
            lng = lng
          )
        }

        val city = addr.locality ?: addr.subAdminArea ?: ""
        val primaryArea = addr.subLocality
          ?: addr.thoroughfare
          ?: addr.featureName
          ?: city
          ?: addr.adminArea
          ?: "Current location"
        val areaLabel = if (city.isNotBlank() && !primaryArea.equals(city, ignoreCase = true)) {
          "$primaryArea, $city"
        } else {
          primaryArea
        }

        val seen = mutableSetOf<String>()
        val parts = listOfNotNull(
          addr.featureName,
          addr.thoroughfare,
          addr.subLocality,
          addr.locality ?: addr.subAdminArea,
          addr.adminArea,
          addr.postalCode
        ).map { it.trim() }
          .filter { it.isNotBlank() && seen.add(it.lowercase(Locale.ROOT)) }

        UserLocation(
          area = areaLabel,
          fullAddress = parts.joinToString(", "),
          lat = lat,
          lng = lng
        )
      } catch (_: Exception) {
        UserLocation(
          area = String.format(Locale.ROOT, "%.4f, %.4f", lat, lng),
          fullAddress = "",
          lat = lat,
          lng = lng
        )
      }
    }
}
