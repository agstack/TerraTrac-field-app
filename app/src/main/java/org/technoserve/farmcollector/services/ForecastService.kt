package org.technoserve.farmcollector.services

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.*

/**
 * Service for fetching weather forecasts from 3rd-party providers
 * Integrates with terrapipe.io or similar forecast services
 */
class ForecastService(private val context: Context) {
    
    private val gson = Gson()
    private val cachePreferences: SharedPreferences = 
        context.getSharedPreferences("forecast_cache", Context.MODE_PRIVATE)
    
    // API interfaces
    private interface ForecastApi {
        @GET("forecast/hourly")
        suspend fun getHourlyForecast(
            @Query("geoid") geoid: String,
            @Query("horizon") horizon: String = "0d"
        ): ForecastResponse
    }
    
    // Data classes
    data class ForecastResponse(
        val geoid: String,
        val model: String,
        val horizon: String,
        val units: ForecastUnits,
        val hours: List<ForecastHour>
    )
    
    data class ForecastUnits(
        val precip: String,
        val temp: String
    )
    
    data class ForecastHour(
        val ts: String, // ISO timestamp
        val precip: Double, // mm
        val temp: Double, // °C
        val wind: Double, // m/s
        val rh: Double // relative humidity %
    )
    
    data class CachedForecast(
        val geoid: String,
        val forecast: ForecastResponse,
        val cachedAt: Long,
        val expiresAt: Long
    )
    
    private val api: ForecastApi by lazy {
        val baseUrl = getTerrapipeBaseUrl()
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ForecastApi::class.java)
    }
    
    /**
     * Fetches hourly forecast for a specific geoid
     * @param geoid The AgStack geoid
     * @param forceRefresh Whether to bypass cache and fetch fresh data
     * @return Forecast response or null if failed
     */
    suspend fun getHourlyForecast(geoid: String, forceRefresh: Boolean = false): ForecastResponse? {
        return withContext(Dispatchers.IO) {
            try {
                // Check cache first (unless force refresh)
                if (!forceRefresh) {
                    val cached = getCachedForecast(geoid)
                    if (cached != null && System.currentTimeMillis() < cached.expiresAt) {
                        return@withContext cached.forecast
                    }
                }
                
                // Fetch fresh data
                val forecast = api.getHourlyForecast(geoid, "0d")
                
                // Cache the result
                cacheForecast(geoid, forecast)
                
                forecast
            } catch (e: Exception) {
                e.printStackTrace()
                // Return cached data if available, even if expired
                getCachedForecast(geoid)?.forecast
            }
        }
    }
    
    /**
     * Gets cached forecast for a geoid
     * @param geoid The geoid to get cached forecast for
     * @return Cached forecast or null if not found/expired
     */
    private fun getCachedForecast(geoid: String): CachedForecast? {
        val cachedJson = cachePreferences.getString("forecast_$geoid", null) ?: return null
        return try {
            gson.fromJson(cachedJson, CachedForecast::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Caches a forecast response
     * @param geoid The geoid
     * @param forecast The forecast response
     */
    private fun cacheForecast(geoid: String, forecast: ForecastResponse) {
        val cached = CachedForecast(
            geoid = geoid,
            forecast = forecast,
            cachedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (2 * 60 * 60 * 1000) // 2 hours
        )
        
        val cachedJson = gson.toJson(cached)
        cachePreferences.edit()
            .putString("forecast_$geoid", cachedJson)
            .apply()
    }
    
    /**
     * Clears all cached forecasts
     */
    fun clearCache() {
        cachePreferences.edit().clear().apply()
    }
    
    /**
     * Clears cached forecast for a specific geoid
     * @param geoid The geoid to clear cache for
     */
    fun clearCacheForGeoid(geoid: String) {
        cachePreferences.edit()
            .remove("forecast_$geoid")
            .apply()
    }
    
    /**
     * Formats a forecast hour for display
     * @param hour The forecast hour
     * @return Formatted string
     */
    fun formatForecastHour(hour: ForecastHour): String {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timestamp = try {
            val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).parse(hour.ts)
            dateFormat.format(date ?: Date())
        } catch (e: Exception) {
            "00:00"
        }
        
        return "$timestamp — precip: ${String.format("%.1f", hour.precip)} mm — temp: ${String.format("%.1f", hour.temp)}°C — wind: ${String.format("%.1f", hour.wind)} m/s"
    }
    
    /**
     * Gets the TERRAPIPE_BASE URL from settings
     * @return TERRAPIPE_BASE URL
     */
    private fun getTerrapipeBaseUrl(): String {
        val settingsPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return settingsPrefs.getString("TERRAPIPE_BASE", "https://terrapipe.io") ?: "https://terrapipe.io"
    }
}
