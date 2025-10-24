package org.technoserve.farmcollector.services

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.technoserve.farmcollector.database.Farm
import org.technoserve.farmcollector.database.FarmDAO
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.UUID

/**
 * Service for managing AgStack Geoid registration and retrieval
 * Handles polygon registration and geoid mapping
 */
class GeoidService(
    private val context: Context,
    private val farmDAO: FarmDAO
) {
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("geoid_mappings", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // API interfaces - based on AgStack asset-registry
    private interface GeoidApi {
        @POST("register")
        suspend fun registerAsset(@Body request: AssetRegisterRequest): AssetRegisterResponse
        
        @GET("assets")
        suspend fun getMyAssets(): List<AssetInfo>
        
        @POST("points/register")
        suspend fun registerPoint(@Body request: PointRegisterRequest): PointRegisterResponse
    }
    
    // Data classes - based on AgStack asset-registry
    data class AssetRegisterRequest(
        val geometry: GeoJsonPolygon,
        val name: String,
        val description: String? = null,
        val user_id: String? = null
    )
    
    data class AssetRegisterResponse(
        val geoid: String, // 256-byte/16-char alphanumeric unique ID
        val name: String,
        val area: Double?,
        val status: String
    )
    
    data class PointRegisterRequest(
        val latitude: Double,
        val longitude: Double,
        val name: String? = null,
        val user_id: String? = null
    )
    
    data class PointRegisterResponse(
        val geoid: String, // 256-byte/16-char alphanumeric unique ID
        val latitude: Double,
        val longitude: Double,
        val status: String
    )
    
    data class AssetInfo(
        val geoid: String,
        val name: String,
        val area: Double?,
        val geometry: GeoJsonPolygon,
        val created_at: String
    )
    
    data class GeoJsonPolygon(
        val type: String = "Polygon",
        val coordinates: List<List<List<Double>>>
    )
    
    data class LocalGeoidMapping(
        val localFieldId: Long,
        val geoid: String,
        val name: String,
        val registeredAt: Long
    )
    
    private val api: GeoidApi by lazy {
        val baseUrl = getApiBaseUrl()
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeoidApi::class.java)
    }
    
    /**
     * Registers a polygon (farm) with the AgStack asset-registry
     * @param farm The farm to register
     * @param customName Optional custom name for the field
     * @return Geoid string if successful, null if failed
     */
    suspend fun registerPolygon(farm: Farm, customName: String? = null): String? {
        return withContext(Dispatchers.IO) {
            try {
                val coordinates = farm.coordinates ?: return@withContext null
                if (coordinates.isEmpty()) return@withContext null
                
                // Convert coordinates to GeoJSON format
                val geoJsonCoordinates = listOf(
                    coordinates.map { listOf(it.first ?: 0.0, it.second ?: 0.0) }
                )
                
                val geoJsonPolygon = GeoJsonPolygon(coordinates = geoJsonCoordinates)
                val fieldName = customName ?: "Field ${farm.farmerName} - ${farm.id}"
                
                val request = AssetRegisterRequest(
                    geometry = geoJsonPolygon,
                    name = fieldName,
                    description = "TerraTrac field polygon",
                    user_id = getCurrentUserId()
                )
                
                val response = api.registerAsset(request)
                
                if (response.status == "success") {
                    // Store the mapping locally
                    storeGeoidMapping(farm.id, response.geoid, response.name)
                    response.geoid
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Registers a point (lat-lon) with the AgStack asset-registry
     * Used for WhatsApp off-ramp with rain gauge locations
     * @param latitude Latitude
     * @param longitude Longitude
     * @param name Optional name for the point
     * @return Geoid string if successful, null if failed
     */
    suspend fun registerPoint(latitude: Double, longitude: Double, name: String? = null): String? {
        return withContext(Dispatchers.IO) {
            try {
                val request = PointRegisterRequest(
                    latitude = latitude,
                    longitude = longitude,
                    name = name ?: "Rain Gauge Point",
                    user_id = getCurrentUserId()
                )
                
                val response = api.registerPoint(request)
                
                if (response.status == "success") {
                    response.geoid
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Retrieves all registered assets for the current user
     * @return List of asset information
     */
    suspend fun getMyAssets(): List<AssetInfo> {
        return withContext(Dispatchers.IO) {
            try {
                api.getMyAssets()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    /**
     * Gets the current user ID from AgStack authentication
     * @return User ID if available, null otherwise
     */
    private fun getCurrentUserId(): String? {
        val authPrefs = context.getSharedPreferences("agstack_auth", Context.MODE_PRIVATE)
        val userJson = authPrefs.getString("current_user", null) ?: return null
        return try {
            val user = gson.fromJson(userJson, com.google.gson.JsonObject::class.java)
            user.get("id")?.asString
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Gets the geoid for a specific local field ID
     * @param localFieldId The local field ID
     * @return Geoid string if found, null otherwise
     */
    fun getGeoidForField(localFieldId: Long): String? {
        val mappingJson = sharedPreferences.getString("geoid_$localFieldId", null) ?: return null
        return try {
            val mapping = gson.fromJson(mappingJson, LocalGeoidMapping::class.java)
            mapping.geoid
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Gets all local geoid mappings
     * @return List of local geoid mappings
     */
    fun getAllGeoidMappings(): List<LocalGeoidMapping> {
        val allKeys = sharedPreferences.all.keys.filter { it.startsWith("geoid_") }
        return allKeys.mapNotNull { key ->
            val mappingJson = sharedPreferences.getString(key, null) ?: return@mapNotNull null
            try {
                gson.fromJson(mappingJson, LocalGeoidMapping::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Stores a geoid mapping locally
     * @param localFieldId The local field ID
     * @param geoid The geoid string
     * @param name The field name
     */
    private fun storeGeoidMapping(localFieldId: Long, geoid: String, name: String) {
        val mapping = LocalGeoidMapping(
            localFieldId = localFieldId,
            geoid = geoid,
            name = name,
            registeredAt = System.currentTimeMillis()
        )
        
        val mappingJson = gson.toJson(mapping)
        sharedPreferences.edit()
            .putString("geoid_$localFieldId", mappingJson)
            .apply()
    }
    
    /**
     * Removes a geoid mapping
     * @param localFieldId The local field ID
     */
    fun removeGeoidMapping(localFieldId: Long) {
        sharedPreferences.edit()
            .remove("geoid_$localFieldId")
            .apply()
    }
    
    /**
     * Gets the API base URL from settings
     * @return API base URL
     */
    private fun getApiBaseUrl(): String {
        val settingsPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return settingsPrefs.getString("EARTHCAST_API_BASE", "https://api.earthcast.ai") ?: "https://api.earthcast.ai"
    }
}
