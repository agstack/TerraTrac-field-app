package org.technoserve.farmcollector.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import org.technoserve.farmcollector.database.converters.DateConverter
import java.util.UUID

/**
 * Database entities for Citizen Science features
 * Designed to interface with Earthcast backend (Synapse + PostgreSQL)
 */

@Entity(tableName = "CitizenSciencePosts")
data class CitizenSciencePost(
    @ColumnInfo(name = "remote_id")
    val remoteId: UUID = UUID.randomUUID(),
    
    @ColumnInfo(name = "post_type")
    val postType: String, // "rain", "photo", "general"
    
    @ColumnInfo(name = "content")
    val content: String,
    
    @ColumnInfo(name = "image_path")
    val imagePath: String? = null,
    
    @ColumnInfo(name = "rainfall_mm")
    val rainfallMm: Float? = null,
    
    @ColumnInfo(name = "s2_token")
    val s2Token: String? = null,
    
    @ColumnInfo(name = "latitude")
    val latitude: Double? = null,
    
    @ColumnInfo(name = "longitude")
    val longitude: Double? = null,
    
    @ColumnInfo(name = "matrix_event_id")
    val matrixEventId: String? = null,
    
    @ColumnInfo(name = "matrix_room_id")
    val matrixRoomId: String? = null,
    
    @ColumnInfo(name = "synced")
    val synced: Boolean = false,
    
    @ColumnInfo(name = "created_at")
    @TypeConverters(DateConverter::class)
    val createdAt: Long,
    
    @ColumnInfo(name = "updated_at")
    @TypeConverters(DateConverter::class)
    val updatedAt: Long
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L
}

@Entity(tableName = "GeoidMappings")
data class GeoidMapping(
    @ColumnInfo(name = "local_field_id")
    val localFieldId: Long,
    
    @ColumnInfo(name = "geoid")
    val geoid: String,
    
    @ColumnInfo(name = "field_name")
    val fieldName: String,
    
    @ColumnInfo(name = "geometry")
    val geometry: String, // JSON string of GeoJSON polygon
    
    @ColumnInfo(name = "area_hectares")
    val areaHectares: Double? = null,
    
    @ColumnInfo(name = "registered_at")
    @TypeConverters(DateConverter::class)
    val registeredAt: Long,
    
    @ColumnInfo(name = "synced")
    val synced: Boolean = false
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L
}

@Entity(tableName = "ForecastCache")
data class ForecastCache(
    @ColumnInfo(name = "geoid")
    val geoid: String,
    
    @ColumnInfo(name = "forecast_data")
    val forecastData: String, // JSON string of forecast response
    
    @ColumnInfo(name = "cached_at")
    @TypeConverters(DateConverter::class)
    val cachedAt: Long,
    
    @ColumnInfo(name = "expires_at")
    @TypeConverters(DateConverter::class)
    val expiresAt: Long
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L
}

@Entity(tableName = "MatrixRooms")
data class MatrixRoom(
    @ColumnInfo(name = "room_id")
    val roomId: String,
    
    @ColumnInfo(name = "room_name")
    val roomName: String,
    
    @ColumnInfo(name = "room_type")
    val roomType: String, // "public", "private"
    
    @ColumnInfo(name = "joined_at")
    @TypeConverters(DateConverter::class)
    val joinedAt: Long,
    
    @ColumnInfo(name = "last_sync")
    @TypeConverters(DateConverter::class)
    val lastSync: Long? = null
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L
}

@Entity(tableName = "MatrixMessages")
data class MatrixMessage(
    @ColumnInfo(name = "event_id")
    val eventId: String,
    
    @ColumnInfo(name = "room_id")
    val roomId: String,
    
    @ColumnInfo(name = "sender_id")
    val senderId: String,
    
    @ColumnInfo(name = "content")
    val content: String,
    
    @ColumnInfo(name = "message_type")
    val messageType: String, // "text", "image", "file"
    
    @ColumnInfo(name = "image_url")
    val imageUrl: String? = null,
    
    @ColumnInfo(name = "timestamp")
    @TypeConverters(DateConverter::class)
    val timestamp: Long,
    
    @ColumnInfo(name = "synced")
    val synced: Boolean = false
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L
}

@Entity(tableName = "UserProfile")
data class UserProfile(
    @ColumnInfo(name = "matrix_user_id")
    val matrixUserId: String,
    
    @ColumnInfo(name = "display_name")
    val displayName: String,
    
    @ColumnInfo(name = "avatar_url")
    val avatarUrl: String? = null,
    
    @ColumnInfo(name = "agstack_user_id")
    val agstackUserId: String? = null,
    
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    
    @ColumnInfo(name = "created_at")
    @TypeConverters(DateConverter::class)
    val createdAt: Long,
    
    @ColumnInfo(name = "last_sync")
    @TypeConverters(DateConverter::class)
    val lastSync: Long? = null
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0L
}

// Data classes for API communication with Earthcast backend
data class CitizenSciencePostDto(
    val remote_id: String,
    val post_type: String,
    val content: String,
    val image_path: String?,
    val rainfall_mm: Float?,
    val s2_token: String?,
    val latitude: Double?,
    val longitude: Double?,
    val matrix_event_id: String?,
    val matrix_room_id: String?,
    val created_at: Long
)

data class GeoidMappingDto(
    val local_field_id: Long,
    val geoid: String,
    val field_name: String,
    val geometry: String,
    val area_hectares: Double?,
    val registered_at: Long
)

data class ForecastDataDto(
    val geoid: String,
    val forecast_data: String,
    val cached_at: Long,
    val expires_at: Long
)

data class MatrixMessageDto(
    val event_id: String,
    val room_id: String,
    val sender_id: String,
    val content: String,
    val message_type: String,
    val image_url: String?,
    val timestamp: Long
)

data class UserProfileDto(
    val matrix_user_id: String,
    val display_name: String,
    val avatar_url: String?,
    val agstack_user_id: String?,
    val device_id: String,
    val created_at: Long
)
