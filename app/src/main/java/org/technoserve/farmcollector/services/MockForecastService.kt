package org.technoserve.farmcollector.services

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Mock forecast service for development and testing
 * Provides realistic 10-day hourly weather data with precipitation focus
 */
class MockForecastService(private val context: Context) {
    
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
    
    // Data classes matching the real API
    data class MockForecastResponse(
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
    
    /**
     * Generates mock 10-day hourly forecast data
     * @param geoid The geoid to generate forecast for
     * @return Mock forecast response
     */
    suspend fun getMockForecast(geoid: String): MockForecastResponse {
        return withContext(Dispatchers.IO) {
            // Simulate network delay
            delay(500)
            
            val now = Calendar.getInstance()
            val hours = mutableListOf<ForecastHour>()
            
            // Generate 10 days of hourly data (240 hours)
            for (i in 0 until 240) {
                val hour = Calendar.getInstance()
                hour.time = now.time
                hour.add(Calendar.HOUR_OF_DAY, i)
                
                val timestamp = dateFormat.format(hour.time)
                val precip = generateRealisticPrecipitation(i)
                val temp = generateRealisticTemperature(i)
                val wind = generateRealisticWind(i)
                val humidity = generateRealisticHumidity(i)
                
                hours.add(
                    ForecastHour(
                        ts = timestamp,
                        precip = precip,
                        temp = temp,
                        wind = wind,
                        rh = humidity
                    )
                )
            }
            
            MockForecastResponse(
                geoid = geoid,
                model = "mock-wrf",
                horizon = "10d",
                units = ForecastUnits(
                    precip = "mm",
                    temp = "C"
                ),
                hours = hours
            )
        }
    }
    
    /**
     * Generates realistic precipitation patterns
     * Simulates daily cycles and weather patterns
     */
    private fun generateRealisticPrecipitation(hourIndex: Int): Double {
        val dayOfForecast = hourIndex / 24
        val hourOfDay = hourIndex % 24
        
        // Base precipitation probability (higher in early morning and evening)
        val baseProb = when {
            hourOfDay in 4..8 -> 0.3  // Early morning
            hourOfDay in 18..22 -> 0.25 // Evening
            else -> 0.1
        }
        
        // Add some daily variation
        val dailyVariation = when (dayOfForecast % 7) {
            0, 6 -> 0.4  // Weekend "stormy" days
            1, 2 -> 0.2  // Mid-week moderate
            else -> 0.1  // Other days
        }
        
        val totalProb = (baseProb + dailyVariation).coerceAtMost(0.8)
        
        return if (Math.random() < totalProb) {
            // Generate precipitation amount (0.1 to 15mm)
            val intensity = when {
                Math.random() < 0.1 -> Math.random() * 15.0  // Heavy rain
                Math.random() < 0.3 -> Math.random() * 5.0   // Moderate rain
                else -> Math.random() * 2.0                  // Light rain
            }
            String.format("%.1f", intensity).toDouble()
        } else {
            0.0
        }
    }
    
    /**
     * Generates realistic temperature patterns
     * Simulates daily temperature cycles
     */
    private fun generateRealisticTemperature(hourIndex: Int): Double {
        val hourOfDay = hourIndex % 24
        val dayOfForecast = hourIndex / 24
        
        // Base temperature (warmer in later days of forecast)
        val baseTemp = 20.0 + (dayOfForecast * 0.5)
        
        // Daily temperature cycle (cooler at night, warmer during day)
        val dailyCycle = when (hourOfDay) {
            in 0..5 -> -5.0 + (hourOfDay * 0.5)  // Night to dawn
            in 6..11 -> -2.0 + ((hourOfDay - 6) * 1.5)  // Morning warming
            in 12..17 -> 4.0 + ((hourOfDay - 12) * 0.2)  // Afternoon peak
            in 18..23 -> 6.0 - ((hourOfDay - 18) * 1.0)  // Evening cooling
            else -> 0.0
        }
        
        // Add some random variation
        val randomVariation = (Math.random() - 0.5) * 4.0
        
        val finalTemp = baseTemp + dailyCycle + randomVariation
        return String.format("%.1f", finalTemp.coerceIn(-10.0, 45.0)).toDouble()
    }
    
    /**
     * Generates realistic wind patterns
     */
    private fun generateRealisticWind(hourIndex: Int): Double {
        val hourOfDay = hourIndex % 24
        
        // Wind is generally stronger during day and calmer at night
        val baseWind = when {
            hourOfDay in 6..18 -> 3.0 + Math.random() * 4.0  // Daytime
            else -> 1.0 + Math.random() * 2.0  // Nighttime
        }
        
        return String.format("%.1f", baseWind).toDouble()
    }
    
    /**
     * Generates realistic humidity patterns
     */
    private fun generateRealisticHumidity(hourIndex: Int): Double {
        val hourOfDay = hourIndex % 24
        
        // Humidity is higher at night and lower during day
        val baseHumidity = when {
            hourOfDay in 0..6 -> 70.0 + Math.random() * 20.0  // Night/early morning
            hourOfDay in 7..17 -> 40.0 + Math.random() * 30.0  // Daytime
            else -> 60.0 + Math.random() * 25.0  // Evening
        }
        
        return String.format("%.1f", baseHumidity.coerceIn(20.0, 95.0)).toDouble()
    }
    
    /**
     * Formats a forecast hour for display
     * @param hour The forecast hour
     * @return Formatted string
     */
    fun formatForecastHour(hour: ForecastHour): String {
        val displayFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timestamp = try {
            val date = dateFormat.parse(hour.ts)
            displayFormat.format(date ?: Date())
        } catch (e: Exception) {
            "00:00"
        }
        
        return "$timestamp — precip: ${String.format("%.1f", hour.precip)} mm — temp: ${String.format("%.1f", hour.temp)}°C — wind: ${String.format("%.1f", hour.wind)} m/s"
    }
    
    /**
     * Gets a summary of precipitation for the next 24 hours
     * @param forecast The forecast response
     * @return Summary string
     */
    fun getPrecipitationSummary(forecast: MockForecastResponse): String {
        val next24Hours = forecast.hours.take(24)
        val totalPrecip = next24Hours.sumOf { it.precip }
        val maxPrecip = next24Hours.maxOfOrNull { it.precip } ?: 0.0
        val precipHours = next24Hours.count { it.precip > 0.1 }
        
        return "Next 24h: ${String.format("%.1f", totalPrecip)}mm total, max ${String.format("%.1f", maxPrecip)}mm, ${precipHours}h with rain"
    }
}
