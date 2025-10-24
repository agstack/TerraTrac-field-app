package org.technoserve.farmcollector.utils

import com.google.common.geometry.S2CellId
import com.google.common.geometry.S2LatLng

/**
 * Utility class for S2 geometry operations
 * Provides S2-L10 cell computation for location-based tagging
 */
object S2Utils {
    
    /**
     * Computes the S2-L10 cell token for the given latitude and longitude
     * @param latitude Latitude in degrees
     * @param longitude Longitude in degrees
     * @return S2-L10 cell token as string
     */
    fun s2CellL10(latitude: Double, longitude: Double): String {
        val latLng = S2LatLng.fromDegrees(latitude, longitude)
        val cellId = S2CellId.fromLatLng(latLng).parent(10) // Level 10 cell
        return cellId.toToken()
    }
    
    /**
     * Computes the S2-L10 cell token for the given latitude and longitude
     * with null safety
     * @param latitude Latitude in degrees (nullable)
     * @param longitude Longitude in degrees (nullable)
     * @return S2-L10 cell token as string, or null if coordinates are invalid
     */
    fun s2CellL10Safe(latitude: Double?, longitude: Double?): String? {
        if (latitude == null || longitude == null) return null
        if (latitude.isNaN() || longitude.isNaN()) return null
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) return null
        
        return try {
            s2CellL10(latitude, longitude)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Validates if the given coordinates are within valid ranges
     * @param latitude Latitude in degrees
     * @param longitude Longitude in degrees
     * @return true if coordinates are valid, false otherwise
     */
    fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
}
