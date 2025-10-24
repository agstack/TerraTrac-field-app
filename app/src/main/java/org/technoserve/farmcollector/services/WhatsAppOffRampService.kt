package org.technoserve.farmcollector.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.technoserve.farmcollector.utils.S2Utils

/**
 * Service for handling WhatsApp off-ramp functionality
 * Enables users to share rain gauge data via WhatsApp with automatic geoid encoding
 */
class WhatsAppOffRampService(private val context: Context) {
    
    private val agStackAuthService = AgStackAuthService(context)
    private val geoidService = GeoidService(context, null) // Will be injected properly
    private val messagingRepository = MessagingRepository(context)
    
    // Data classes
    data class RainGaugeData(
        val latitude: Double,
        val longitude: Double,
        val rainfallMm: Double,
        val notes: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    data class WhatsAppShareData(
        val message: String,
        val deepLink: String,
        val geoid: String? = null,
        val s2Token: String? = null
    )
    
    /**
     * Handles WhatsApp off-ramp for rain gauge data
     * @param rainData The rain gauge data to share
     * @return WhatsAppShareData with formatted message and deep link
     */
    suspend fun handleRainGaugeOffRamp(rainData: RainGaugeData): WhatsAppShareData {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Register point with AgStack asset-registry to get geoid
                val geoid = geoidService.registerPoint(
                    latitude = rainData.latitude,
                    longitude = rainData.longitude,
                    name = "Rain Gauge - ${rainData.timestamp}"
                )
                
                // 2. Generate S2-L10 token for location
                val s2Token = S2Utils.s2CellL10Safe(rainData.latitude, rainData.longitude)
                
                // 3. Create deep link to rain form
                val deepLink = createRainFormDeepLink(rainData)
                
                // 4. Format WhatsApp message
                val message = formatRainGaugeMessage(rainData, geoid, s2Token)
                
                WhatsAppShareData(
                    message = message,
                    deepLink = deepLink,
                    geoid = geoid,
                    s2Token = s2Token
                )
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback without geoid
                val deepLink = createRainFormDeepLink(rainData)
                val message = formatRainGaugeMessage(rainData, null, null)
                
                WhatsAppShareData(
                    message = message,
                    deepLink = deepLink,
                    geoid = null,
                    s2Token = null
                )
            }
        }
    }
    
    /**
     * Shares rain gauge data via WhatsApp
     * @param rainData The rain gauge data
     * @return true if successful, false otherwise
     */
    suspend fun shareRainGaugeViaWhatsApp(rainData: RainGaugeData): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val shareData = handleRainGaugeOffRamp(rainData)
                
                // Create WhatsApp intent
                val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    setPackage("com.whatsapp")
                    putExtra(Intent.EXTRA_TEXT, shareData.message)
                }
                
                // Check if WhatsApp is installed
                if (whatsappIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(whatsappIntent)
                    true
                } else {
                    // Fallback to generic share
                    val genericIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareData.message)
                    }
                    context.startActivity(Intent.createChooser(genericIntent, "Share via"))
                    true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * Handles incoming WhatsApp messages with rain data
     * @param messageText The WhatsApp message text
     * @return true if message was processed, false otherwise
     */
    suspend fun processIncomingWhatsAppMessage(messageText: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Parse rain data from WhatsApp message
                val rainData = parseRainDataFromMessage(messageText)
                if (rainData != null) {
                    // Post to Matrix room
                    val s2Token = S2Utils.s2CellL10Safe(rainData.latitude, rainData.longitude)
                    val content = buildString {
                        append("Rainfall: ${String.format("%.1f", rainData.rainfallMm)}mm")
                        if (!rainData.notes.isNullOrBlank()) {
                            append(" - ${rainData.notes}")
                        }
                        append(" #citizenscience")
                        s2Token?.let { append(" @s2:10:$it") }
                    }
                    
                    messagingRepository.sendTextMessage(content, s2Token)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * Creates a deep link to the rain form with pre-filled data
     * @param rainData The rain gauge data
     * @return Deep link URL
     */
    private fun createRainFormDeepLink(rainData: RainGaugeData): String {
        val baseUrl = "https://tt.earthcast.ai/citizenscience/rain"
        val params = mutableListOf<String>()
        
        params.add("lat=${rainData.latitude}")
        params.add("lon=${rainData.longitude}")
        params.add("rainfall=${rainData.rainfallMm}")
        
        if (!rainData.notes.isNullOrBlank()) {
            params.add("notes=${Uri.encode(rainData.notes)}")
        }
        
        return if (params.isNotEmpty()) {
            "$baseUrl?${params.joinToString("&")}"
        } else {
            baseUrl
        }
    }
    
    /**
     * Formats rain gauge data into a WhatsApp message
     * @param rainData The rain gauge data
     * @param geoid Optional geoid
     * @param s2Token Optional S2 token
     * @return Formatted message
     */
    private fun formatRainGaugeMessage(
        rainData: RainGaugeData,
        geoid: String?,
        s2Token: String?
    ): String {
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(rainData.timestamp))
        
        return buildString {
            append("🌧️ Rain Gauge Report\n")
            append("📅 $timestamp\n")
            append("📍 Location: ${String.format("%.6f", rainData.latitude)}, ${String.format("%.6f", rainData.longitude)}\n")
            append("💧 Rainfall: ${String.format("%.1f", rainData.rainfallMm)}mm\n")
            
            if (!rainData.notes.isNullOrBlank()) {
                append("📝 Notes: ${rainData.notes}\n")
            }
            
            if (geoid != null) {
                append("🆔 Geoid: $geoid\n")
            }
            
            if (s2Token != null) {
                append("🔍 S2 Cell: $s2Token\n")
            }
            
            append("\n📱 Open in TerraTrac: https://tt.earthcast.ai/citizenscience/rain")
            append("\n#citizenscience #rainfall #agriculture")
        }
    }
    
    /**
     * Parses rain data from WhatsApp message text
     * @param messageText The message text
     * @return RainGaugeData if parsed successfully, null otherwise
     */
    private fun parseRainDataFromMessage(messageText: String): RainGaugeData? {
        return try {
            // Simple regex patterns to extract data
            val latPattern = "Location: ([0-9.-]+), ([0-9.-]+)".toRegex()
            val rainfallPattern = "Rainfall: ([0-9.]+)mm".toRegex()
            val notesPattern = "Notes: (.+)".toRegex()
            
            val latMatch = latPattern.find(messageText)
            val rainfallMatch = rainfallPattern.find(messageText)
            val notesMatch = notesPattern.find(messageText)
            
            if (latMatch != null && rainfallMatch != null) {
                val latitude = latMatch.groupValues[1].toDouble()
                val longitude = latMatch.groupValues[2].toDouble()
                val rainfall = rainfallMatch.groupValues[1].toDouble()
                val notes = notesMatch?.groupValues?.get(1)
                
                RainGaugeData(
                    latitude = latitude,
                    longitude = longitude,
                    rainfallMm = rainfall,
                    notes = notes
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Creates a WhatsApp share intent for rain gauge data
     * @param rainData The rain gauge data
     * @return Intent for sharing
     */
    fun createWhatsAppShareIntent(rainData: RainGaugeData): Intent {
        val shareData = kotlinx.coroutines.runBlocking {
            handleRainGaugeOffRamp(rainData)
        }
        
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.whatsapp")
            putExtra(Intent.EXTRA_TEXT, shareData.message)
        }
    }
    
    /**
     * Checks if WhatsApp is installed
     * @return true if WhatsApp is available, false otherwise
     */
    fun isWhatsAppInstalled(): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.whatsapp")
        }
        return intent.resolveActivity(context.packageManager) != null
    }
}
