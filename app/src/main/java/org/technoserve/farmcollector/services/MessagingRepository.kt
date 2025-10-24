package org.technoserve.farmcollector.services

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.matrix.android.sdk.api.Matrix
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.room.Room
import org.matrix.android.sdk.api.session.room.send.SendService
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import org.matrix.android.sdk.api.session.room.timeline.TimelineService
import java.io.File

/**
 * Repository for Matrix messaging operations
 * Handles posting to public Citizen Science room
 */
class MessagingRepository(private val context: Context) {
    
    private val settingsPreferences: SharedPreferences = 
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    
    private var matrixSession: Session? = null
    private var publicRoom: Room? = null
    
    // Data classes
    data class PostResult(
        val success: Boolean,
        val eventId: String? = null,
        val error: String? = null
    )
    
    data class CitizenSciencePost(
        val content: String,
        val imagePath: String? = null,
        val s2Token: String? = null
    )
    
    /**
     * Initializes the Matrix session
     * @return true if successful, false otherwise
     */
    suspend fun initializeSession(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val homeserverUrl = getHomeserverUrl()
                val matrix = Matrix.getInstance(context)
                
                // Check if user is already logged in
                val existingSession = matrix.sessionService().getActiveSession()
                if (existingSession != null) {
                    matrixSession = existingSession
                    return@withContext true
                }
                
                // For now, return false - user needs to login through OIDC
                // In a full implementation, this would handle OIDC authentication
                false
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * Joins the public Citizen Science room
     * @param roomId The room ID for the public Citizen Science room
     * @return true if successful, false otherwise
     */
    suspend fun joinPublicRoom(roomId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val session = matrixSession ?: return@withContext false
                val room = session.roomService().getRoom(roomId)
                if (room != null) {
                    publicRoom = room
                    return@withContext true
                }
                
                // Try to join the room
                session.roomService().joinRoom(roomId)
                val joinedRoom = session.roomService().getRoom(roomId)
                if (joinedRoom != null) {
                    publicRoom = joinedRoom
                    return@withContext true
                }
                
                false
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * Posts a text message to the public room
     * @param content The message content
     * @param s2Token Optional S2 token to include
     * @return PostResult indicating success/failure
     */
    suspend fun sendTextMessage(content: String, s2Token: String? = null): PostResult {
        return withContext(Dispatchers.IO) {
            try {
                val room = publicRoom ?: return@withContext PostResult(false, error = "Not connected to public room")
                
                // Format the message with hashtags and S2 token
                val formattedContent = buildString {
                    append(content)
                    append(" #citizenscience")
                    s2Token?.let { append(" @s2:10:$it") }
                }
                
                val eventId = room.sendService().sendTextMessage(formattedContent)
                PostResult(true, eventId)
            } catch (e: Exception) {
                e.printStackTrace()
                PostResult(false, error = e.message)
            }
        }
    }
    
    /**
     * Posts an image with caption to the public room
     * @param imagePath Path to the image file
     * @param caption Optional caption
     * @param s2Token Optional S2 token to include
     * @return PostResult indicating success/failure
     */
    suspend fun sendImageMessage(imagePath: String, caption: String? = null, s2Token: String? = null): PostResult {
        return withContext(Dispatchers.IO) {
            try {
                val room = publicRoom ?: return@withContext PostResult(false, error = "Not connected to public room")
                
                val imageFile = File(imagePath)
                if (!imageFile.exists()) {
                    return@withContext PostResult(false, error = "Image file not found")
                }
                
                // Send image first
                val imageEventId = room.sendService().sendImage(
                    imageFile = imageFile,
                    filename = imageFile.name,
                    mimeType = "image/jpeg"
                )
                
                // Send caption as separate message if provided
                if (!caption.isNullOrBlank()) {
                    val formattedCaption = buildString {
                        append(caption)
                        append(" #citizenscience")
                        s2Token?.let { append(" @s2:10:$it") }
                    }
                    room.sendService().sendTextMessage(formattedCaption)
                }
                
                PostResult(true, imageEventId)
            } catch (e: Exception) {
                e.printStackTrace()
                PostResult(false, error = e.message)
            }
        }
    }
    
    /**
     * Posts a combined text and image message
     * @param post The CitizenSciencePost to send
     * @return PostResult indicating success/failure
     */
    suspend fun sendCitizenSciencePost(post: CitizenSciencePost): PostResult {
        return withContext(Dispatchers.IO) {
            try {
                if (post.imagePath != null) {
                    sendImageMessage(post.imagePath, post.content, post.s2Token)
                } else {
                    sendTextMessage(post.content, post.s2Token)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                PostResult(false, error = e.message)
            }
        }
    }
    
    /**
     * Gets recent messages from the public room
     * @param limit Number of messages to fetch
     * @return List of timeline events
     */
    suspend fun getRecentMessages(limit: Int = 50): List<TimelineEvent> {
        return withContext(Dispatchers.IO) {
            try {
                val room = publicRoom ?: return@withContext emptyList()
                val timeline = room.timelineService().createTimeline(null, TimelineService.Direction.BACKWARDS)
                timeline.start()
                
                val events = mutableListOf<TimelineEvent>()
                timeline.getEvents(limit).forEach { events.add(it) }
                
                events
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    /**
     * Checks if user has posted in the public room before
     * Used to determine if CDLA-2.0 consent banner should be shown
     * @return true if user has posted before, false otherwise
     */
    suspend fun hasUserPostedBefore(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val session = matrixSession ?: return@withContext false
                val userId = session.myUserId
                
                val room = publicRoom ?: return@withContext false
                val timeline = room.timelineService().createTimeline(null, TimelineService.Direction.BACKWARDS)
                timeline.start()
                
                // Check last 100 events for user's messages
                val events = timeline.getEvents(100)
                events.any { it.senderId == userId }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * Gets the Matrix homeserver URL from settings
     * @return Homeserver URL
     */
    private fun getHomeserverUrl(): String {
        return settingsPreferences.getString("MATRIX_HOMESERVER", "https://matrix.org") ?: "https://matrix.org"
    }
    
    /**
     * Gets the public room ID from settings
     * @return Public room ID
     */
    private fun getPublicRoomId(): String {
        return settingsPreferences.getString("CITIZEN_SCIENCE_ROOM_ID", "") ?: ""
    }
}
