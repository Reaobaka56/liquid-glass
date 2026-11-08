package dev.glass.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class WeatherSnapshot(
    val temperatureC: Double,
    val apparentTemperatureC: Double,
    val weatherCode: Int,
    val humidityPercent: Int,
    val observedAt: String,
)

object WeatherRepository {
    suspend fun current(context: Context): WeatherSnapshot {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) throw SecurityException("Location access was not granted.")

        val location = currentLocation(context)
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val url = URL(
                String.format(
                    Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f" +
                        "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code" +
                        "&timezone=auto",
                    location.latitude,
                    location.longitude,
                ),
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
            }
            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw WeatherRequestException("Weather service returned HTTP $responseCode.")
                }
                val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                val current = json.getJSONObject("current")
                WeatherSnapshot(
                    temperatureC = current.getDouble("temperature_2m"),
                    apparentTemperatureC = current.getDouble("apparent_temperature"),
                    weatherCode = current.getInt("weather_code"),
                    humidityPercent = current.getInt("relative_humidity_2m"),
                    observedAt = current.getString("time"),
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(context: Context): Location {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) throw SecurityException("Location access was not granted.")
        val manager = context.getSystemService(LocationManager::class.java)
            ?: throw WeatherRequestException("Location service is unavailable.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return suspendCancellableCoroutine { continuation ->
                val cancellation = CancellationSignal()
                continuation.invokeOnCancellation { cancellation.cancel() }
                try {
                    manager.getCurrentLocation(
                        LocationManager.NETWORK_PROVIDER,
                        cancellation,
                        context.mainExecutor,
                    ) { location ->
                        if (location != null) {
                            continuation.resume(location)
                        } else {
                            continuation.resumeWithException(
                                WeatherRequestException("Could not determine an approximate location."),
                            )
                        }
                    }
                } catch (e: IllegalArgumentException) {
                    continuation.resumeWithException(
                        WeatherRequestException("Network location is unavailable.", e),
                    )
                } catch (e: SecurityException) {
                    continuation.resumeWithException(e)
                }
            }
        }
        @Suppress("DEPRECATION")
        val location = try {
            manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (e: IllegalArgumentException) {
            throw WeatherRequestException("Location provider is unavailable.", e)
        }
        return location ?: throw WeatherRequestException("No recent location is available.")
    }

    fun summary(code: Int): String = when (code) {
        0 -> "Clear sky"
        1, 2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Rain showers"
        in 85..86 -> "Snow showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Current conditions"
    }

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000
}

class WeatherRequestException(message: String, cause: Throwable? = null) : Exception(message, cause)
