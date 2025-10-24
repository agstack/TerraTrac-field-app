package org.technoserve.farmcollector.services

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.UUID

/**
 * Service for AgStack user registry authentication
 * Integrates with AgStack's user-registry API for SSO
 */
class AgStackAuthService(private val context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("agstack_auth", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // API interfaces
    private interface AgStackAuthApi {
        @POST("signup")
        suspend fun signup(@Body request: SignupRequest): AuthResponse
        
        @POST("login")
        suspend fun login(@Body request: LoginRequest): AuthResponse
        
        @POST("logout")
        suspend fun logout(@Body request: LogoutRequest): AuthResponse
        
        @GET("authority-token")
        suspend fun getAuthorityToken(): AuthorityTokenResponse
        
        @POST("update")
        suspend fun updateUser(@Body request: UpdateUserRequest): AuthResponse
    }
    
    // Data classes
    data class SignupRequest(
        val email: String,
        val password: String,
        val phone_number: String? = null
    )
    
    data class LoginRequest(
        val email: String,
        val password: String
    )
    
    data class LogoutRequest(
        val token: String
    )
    
    data class UpdateUserRequest(
        val token: String,
        val phone_number: String? = null
    )
    
    data class AuthResponse(
        val status: String,
        val message: String,
        val token: String? = null,
        val user: UserData? = null
    )
    
    data class UserData(
        val id: String,
        val email: String,
        val phone_number: String?,
        val token_required: String,
        val geoID: String? = null,
        val boundaries: String? = null,
        val polygon: String? = null
    )
    
    data class AuthorityTokenResponse(
        val authority_token: String
    )
    
    data class AgStackUser(
        val id: String,
        val email: String,
        val phoneNumber: String?,
        val token: String,
        val geoID: String?,
        val boundaries: String?,
        val polygon: String?
    )
    
    private val api: AgStackAuthApi by lazy {
        val baseUrl = getAgStackApiBaseUrl()
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AgStackAuthApi::class.java)
    }
    
    /**
     * Signs up a new user with AgStack
     * @param email User email
     * @param password User password
     * @param phoneNumber Optional phone number
     * @return AgStackUser if successful, null if failed
     */
    suspend fun signup(email: String, password: String, phoneNumber: String? = null): AgStackUser? {
        return withContext(Dispatchers.IO) {
            try {
                val request = SignupRequest(email, password, phoneNumber)
                val response = api.signup(request)
                
                if (response.status == "success" && response.user != null && response.token != null) {
                    val user = AgStackUser(
                        id = response.user.id,
                        email = response.user.email,
                        phoneNumber = response.user.phone_number,
                        token = response.token,
                        geoID = response.user.geoID,
                        boundaries = response.user.boundaries,
                        polygon = response.user.polygon
                    )
                    
                    // Store user data locally
                    storeUserData(user)
                    user
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
     * Logs in an existing user
     * @param email User email
     * @param password User password
     * @return AgStackUser if successful, null if failed
     */
    suspend fun login(email: String, password: String): AgStackUser? {
        return withContext(Dispatchers.IO) {
            try {
                val request = LoginRequest(email, password)
                val response = api.login(request)
                
                if (response.status == "success" && response.user != null && response.token != null) {
                    val user = AgStackUser(
                        id = response.user.id,
                        email = response.user.email,
                        phoneNumber = response.user.phone_number,
                        token = response.token,
                        geoID = response.user.geoID,
                        boundaries = response.user.boundaries,
                        polygon = response.user.polygon
                    )
                    
                    // Store user data locally
                    storeUserData(user)
                    user
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
     * Logs out the current user
     * @return true if successful, false otherwise
     */
    suspend fun logout(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val currentUser = getCurrentUser()
                if (currentUser != null) {
                    val request = LogoutRequest(currentUser.token)
                    val response = api.logout(request)
                    
                    if (response.status == "success") {
                        clearUserData()
                        true
                    } else {
                        false
                    }
                } else {
                    true // Already logged out
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * Updates user information
     * @param phoneNumber New phone number
     * @return true if successful, false otherwise
     */
    suspend fun updateUser(phoneNumber: String? = null): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val currentUser = getCurrentUser() ?: return@withContext false
                
                val request = UpdateUserRequest(currentUser.token, phoneNumber)
                val response = api.updateUser(request)
                
                if (response.status == "success") {
                    // Update local user data
                    val updatedUser = currentUser.copy(phoneNumber = phoneNumber)
                    storeUserData(updatedUser)
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
     * Gets the current logged-in user
     * @return AgStackUser if logged in, null otherwise
     */
    fun getCurrentUser(): AgStackUser? {
        val userJson = sharedPreferences.getString("current_user", null) ?: return null
        return try {
            gson.fromJson(userJson, AgStackUser::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Checks if user is currently logged in
     * @return true if logged in, false otherwise
     */
    fun isLoggedIn(): Boolean {
        return getCurrentUser() != null
    }
    
    /**
     * Gets the authority token for domain verification
     * @return Authority token if successful, null otherwise
     */
    suspend fun getAuthorityToken(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getAuthorityToken()
                response.authority_token
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Migrates existing TerraTrac user to AgStack
     * @param deviceId TerraTrac device ID
     * @param email User email
     * @param password User password
     * @return AgStackUser if successful, null if failed
     */
    suspend fun migrateTerraTracUser(deviceId: String, email: String, password: String): AgStackUser? {
        return withContext(Dispatchers.IO) {
            try {
                // First try to login with existing credentials
                val existingUser = login(email, password)
                if (existingUser != null) {
                    return@withContext existingUser
                }
                
                // If login fails, try to signup
                val newUser = signup(email, password)
                if (newUser != null) {
                    // Store the device ID mapping for future reference
                    storeDeviceMapping(deviceId, newUser.id)
                }
                
                newUser
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Stores user data locally
     * @param user The user to store
     */
    private fun storeUserData(user: AgStackUser) {
        val userJson = gson.toJson(user)
        sharedPreferences.edit()
            .putString("current_user", userJson)
            .apply()
    }
    
    /**
     * Clears stored user data
     */
    private fun clearUserData() {
        sharedPreferences.edit()
            .remove("current_user")
            .apply()
    }
    
    /**
     * Stores device ID mapping
     * @param deviceId TerraTrac device ID
     * @param agstackUserId AgStack user ID
     */
    private fun storeDeviceMapping(deviceId: String, agstackUserId: String) {
        sharedPreferences.edit()
            .putString("device_mapping_$deviceId", agstackUserId)
            .apply()
    }
    
    /**
     * Gets AgStack user ID for a device ID
     * @param deviceId TerraTrac device ID
     * @return AgStack user ID if found, null otherwise
     */
    fun getAgStackUserIdForDevice(deviceId: String): String? {
        return sharedPreferences.getString("device_mapping_$deviceId", null)
    }
    
    /**
     * Gets the AgStack API base URL from settings
     * @return API base URL
     */
    private fun getAgStackApiBaseUrl(): String {
        val settingsPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return settingsPrefs.getString("AGSTACK_API_BASE", "https://api.agstack.org") ?: "https://api.agstack.org"
    }
}
