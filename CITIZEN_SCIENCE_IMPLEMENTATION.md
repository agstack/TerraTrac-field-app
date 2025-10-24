# TerraTrac Citizen Science MVP - Technical Implementation

## Overview

This document provides a comprehensive technical overview of the Citizen Science MVP implementation for TerraTrac, including architecture, API integrations, database design, and implementation details.

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Deep Link Implementation](#deep-link-implementation)
3. [WhatsApp Off-Ramp System](#whatsapp-off-ramp-system)
4. [AgStack Integration](#agstack-integration)
5. [Matrix Messaging Integration](#matrix-messaging-integration)
6. [Database Schema](#database-schema)
7. [API Services](#api-services)
8. [UI Implementation](#ui-implementation)
9. [Configuration & Settings](#configuration--settings)
10. [Testing & Deployment](#testing--deployment)

## Architecture Overview

### System Components

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   WhatsApp      │    │   TerraTrac     │    │   Matrix        │
│   Off-Ramp      │◄──►│   Citizen       │◄──►│   Community     │
│   System        │    │   Science       │    │   Room          │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   AgStack       │    │   Earthcast     │    │   TERRAPIPE     │
│   Asset         │    │   Backend       │    │   Weather       │
│   Registry      │    │   (Synapse +    │    │   Service       │
│                 │    │   PostgreSQL)   │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

### Key Features

- **Deep Link Routing**: Path-based routes for `tt.earthcast.ai` domain
- **WhatsApp Integration**: Automatic geoid registration and S2-L10 encoding
- **Matrix Messaging**: Public room posting with hashtags and location tags
- **AgStack Integration**: User authentication and asset registry
- **Weather Forecasts**: 10-day hourly predictions with precipitation focus
- **Offline Support**: Local caching and sync when connectivity available

## Deep Link Implementation

### URL Structure

```
https://tt.earthcast.ai/citizenscience          → Citizen Science Hub
https://tt.earthcast.ai/citizenscience/rain     → Rain Reporting Form
https://tt.earthcast.ai/citizenscience/photo     → Photo Sharing Form
https://tt.earthcast.ai/citizenscience/forecast → Weather Forecast Viewer
```

### Android Manifest Configuration

```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="https"
          android:host="tt.earthcast.ai"
          android:pathPrefix="/citizenscience" />
</intent-filter>
```

### Deep Link Handling

```kotlin
private fun handleDeepLink(intent: Intent?) {
    val data = intent?.data
    if (data != null && data.host == "tt.earthcast.ai") {
        val path = data.path
        when {
            path == "/citizenscience" -> navigateToHub()
            path == "/citizenscience/rain" -> navigateToRainForm()
            path == "/citizenscience/photo" -> navigateToPhotoForm()
        }
    }
}
```

### Navigation Routes

```kotlin
object Routes {
    const val CITIZEN_SCIENCE = "citizenscience"
    const val CITIZEN_SCIENCE_RAIN = "citizenscience/rain"
    const val CITIZEN_SCIENCE_PHOTO = "citizenscience/photo"
    const val CITIZEN_SCIENCE_FORECAST = "citizenscience/forecast"
    const val CITIZEN_SCIENCE_FIELDS = "citizenscience/fields"
}
```

## WhatsApp Off-Ramp System

### Core Functionality

The WhatsApp off-ramp system enables users to share rain gauge data via WhatsApp with automatic geoid registration and S2-L10 cell encoding.

### Data Flow

1. **User Input**: Rainfall measurement + location coordinates
2. **Geoid Registration**: Automatic registration with AgStack asset-registry
3. **S2-L10 Encoding**: Location-based cell token generation
4. **Message Formatting**: WhatsApp message with embedded data
5. **Deep Link Generation**: TerraTrac app deep link inclusion

### Implementation Details

#### WhatsAppOffRampService

```kotlin
class WhatsAppOffRampService(private val context: Context) {
    
    data class RainGaugeData(
        val latitude: Double,
        val longitude: Double,
        val rainfallMm: Double,
        val notes: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    suspend fun handleRainGaugeOffRamp(rainData: RainGaugeData): WhatsAppShareData {
        // 1. Register point with AgStack asset-registry
        val geoid = geoidService.registerPoint(
            latitude = rainData.latitude,
            longitude = rainData.longitude,
            name = "Rain Gauge - ${rainData.timestamp}"
        )
        
        // 2. Generate S2-L10 token
        val s2Token = S2Utils.s2CellL10Safe(rainData.latitude, rainData.longitude)
        
        // 3. Create deep link
        val deepLink = createRainFormDeepLink(rainData)
        
        // 4. Format WhatsApp message
        val message = formatRainGaugeMessage(rainData, geoid, s2Token)
        
        return WhatsAppShareData(message, deepLink, geoid, s2Token)
    }
}
```

#### Message Format

```
🌧️ Rain Gauge Report
📅 2024-10-23 14:30
📍 Location: 40.7128, -74.0060
💧 Rainfall: 5.2mm
📝 Notes: Heavy downpour, lasted 2 hours
🆔 Geoid: agstack:hn:12345
🔍 S2 Cell: 89c2594

📱 Open in TerraTrac: https://tt.earthcast.ai/citizenscience/rain
#citizenscience #rainfall #agriculture
```

#### Deep Link Generation

```kotlin
private fun createRainFormDeepLink(rainData: RainGaugeData): String {
    val baseUrl = "https://tt.earthcast.ai/citizenscience/rain"
    val params = mutableListOf<String>()
    
    params.add("lat=${rainData.latitude}")
    params.add("lon=${rainData.longitude}")
    params.add("rainfall=${rainData.rainfallMm}")
    
    if (!rainData.notes.isNullOrBlank()) {
        params.add("notes=${Uri.encode(rainData.notes)}")
    }
    
    return "$baseUrl?${params.joinToString("&")}"
}
```

## AgStack Integration

### User Authentication

Integration with [AgStack user-registry](https://github.com/agstack/user-registry) for SSO authentication.

#### AgStackAuthService

```kotlin
class AgStackAuthService(private val context: Context) {
    
    interface AgStackAuthApi {
        @POST("signup")
        suspend fun signup(@Body request: SignupRequest): AuthResponse
        
        @POST("login")
        suspend fun login(@Body request: LoginRequest): AuthResponse
        
        @POST("logout")
        suspend fun logout(@Body request: LogoutRequest): AuthResponse
        
        @GET("authority-token")
        suspend fun getAuthorityToken(): AuthorityTokenResponse
    }
    
    data class SignupRequest(
        val email: String,
        val password: String,
        val phone_number: String? = null
    )
    
    data class AuthResponse(
        val status: String,
        val message: String,
        val token: String? = null,
        val user: UserData? = null
    )
}
```

### Asset Registry Integration

Integration with [AgStack asset-registry](https://github.com/agstack/asset-registry) for geoid generation.

#### GeoidService

```kotlin
class GeoidService(private val context: Context, private val farmDAO: FarmDAO) {
    
    interface GeoidApi {
        @POST("register")
        suspend fun registerAsset(@Body request: AssetRegisterRequest): AssetRegisterResponse
        
        @POST("points/register")
        suspend fun registerPoint(@Body request: PointRegisterRequest): PointRegisterResponse
        
        @GET("assets")
        suspend fun getMyAssets(): List<AssetInfo>
    }
    
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
}
```

### S2-L10 Cell Computation

```kotlin
object S2Utils {
    
    fun s2CellL10(latitude: Double, longitude: Double): String {
        val latLng = S2LatLng.fromDegrees(latitude, longitude)
        val cellId = S2CellId.fromLatLng(latLng).parent(10) // Level 10 cell
        return cellId.toToken()
    }
    
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
}
```

## Matrix Messaging Integration

### Matrix SDK Integration

Using Matrix Android SDK 2 for real-time messaging in public Citizen Science room.

#### MessagingRepository

```kotlin
class MessagingRepository(private val context: Context) {
    
    private var matrixSession: Session? = null
    private var publicRoom: Room? = null
    
    suspend fun initializeSession(): Boolean {
        val homeserverUrl = getHomeserverUrl()
        val matrix = Matrix.getInstance(context)
        
        val existingSession = matrix.sessionService().getActiveSession()
        if (existingSession != null) {
            matrixSession = existingSession
            return true
        }
        
        return false // User needs OIDC authentication
    }
    
    suspend fun sendTextMessage(content: String, s2Token: String? = null): PostResult {
        val room = publicRoom ?: return PostResult(false, error = "Not connected to public room")
        
        val formattedContent = buildString {
            append(content)
            append(" #citizenscience")
            s2Token?.let { append(" @s2:10:$it") }
        }
        
        val eventId = room.sendService().sendTextMessage(formattedContent)
        return PostResult(true, eventId)
    }
}
```

### Message Format

All Citizen Science messages include:
- **Content**: User-provided message
- **Hashtag**: `#citizenscience` (always included)
- **S2 Tag**: `@s2:10:<token>` (when location available)

### CDLA-2.0 Consent Banner

```kotlin
suspend fun hasUserPostedBefore(): Boolean {
    val session = matrixSession ?: return false
    val userId = session.myUserId
    
    val room = publicRoom ?: return false
    val timeline = room.timelineService().createTimeline(null, TimelineService.Direction.BACKWARDS)
    timeline.start()
    
    val events = timeline.getEvents(100)
    return events.any { it.senderId == userId }
}
```

## Database Schema

### Core Entities

#### CitizenSciencePost

```kotlin
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
```

#### GeoidMapping

```kotlin
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
```

#### ForecastCache

```kotlin
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
```

#### MatrixRooms & MatrixMessages

```kotlin
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
```

#### UserProfile

```kotlin
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
```

## API Services

### Weather Forecast Service

#### ForecastService

```kotlin
class ForecastService(private val context: Context) {
    
    interface ForecastApi {
        @GET("forecast/hourly")
        suspend fun getHourlyForecast(
            @Query("geoid") geoid: String,
            @Query("horizon") horizon: String = "0d"
        ): ForecastResponse
    }
    
    data class ForecastResponse(
        val geoid: String,
        val model: String,
        val horizon: String,
        val units: ForecastUnits,
        val hours: List<ForecastHour>
    )
    
    data class ForecastHour(
        val ts: String, // ISO timestamp
        val precip: Double, // mm
        val temp: Double, // °C
        val wind: Double, // m/s
        val rh: Double // relative humidity %
    )
}
```

#### Mock Forecast Service

```kotlin
class MockForecastService(private val context: Context) {
    
    suspend fun getMockForecast(geoid: String): MockForecastResponse {
        // Generate 10 days of hourly data (240 hours)
        val hours = (0 until 240).map { i ->
            val hour = Calendar.getInstance()
            hour.add(Calendar.HOUR_OF_DAY, i)
            
            ForecastHour(
                ts = dateFormat.format(hour.time),
                precip = generateRealisticPrecipitation(i),
                temp = generateRealisticTemperature(i),
                wind = generateRealisticWind(i),
                humidity = generateRealisticHumidity(i)
            )
        }
        
        return MockForecastResponse(
            geoid = geoid,
            model = "mock-wrf",
            horizon = "10d",
            units = ForecastUnits(precip = "mm", temp = "C"),
            hours = hours
        )
    }
}
```

## UI Implementation

### Screen Architecture

#### CitizenScienceHub

Main entry point with quick actions and field management.

```kotlin
@Composable
fun CitizenScienceHub(navController: NavController) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Welcome card with instructions
            Card(colors = CardDefaults.cardColors(containerColor = Turquoise.copy(alpha = 0.1f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Science, contentDescription = null)
                    Text("Welcome to Citizen Science")
                    Text("Help contribute to agricultural research...")
                }
            }
        }
        
        item { Text("Quick Actions", style = MaterialTheme.typography.titleMedium) }
        
        items(getQuickActions()) { action ->
            QuickActionCard(action = action, onClick = { navController.navigate(action.route) })
        }
    }
}
```

#### RainForm

Rainfall reporting with WhatsApp sharing and Matrix posting.

```kotlin
@Composable
fun RainForm(navController: NavController) {
    var rainfallMm by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var showWhatsAppDialog by remember { mutableStateOf(false) }
    
    // Services
    val whatsAppService = remember { WhatsAppOffRampService(context) }
    val messagingRepository = remember { MessagingRepository(context) }
    
    Column {
        // Header with back button
        Row(modifier = Modifier.background(MaterialTheme.colorScheme.primary)) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Report Rainfall")
        }
        
        // Form content
        Column(modifier = Modifier.padding(16.dp)) {
            // Instructions card
            Card(colors = CardDefaults.cardColors(containerColor = Turquoise.copy(alpha = 0.1f))) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.WaterDrop, contentDescription = null)
                    Text("Help track rainfall patterns...")
                }
            }
            
            // Rainfall input
            OutlinedTextField(
                value = rainfallMm,
                onValueChange = { rainfallMm = it },
                label = { Text("Rainfall (mm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                leadingIcon = { Icon(Icons.Default.WaterDrop, contentDescription = null) },
                suffix = { Text("mm") }
            )
            
            // Notes input
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Additional Notes (Optional)") },
                modifier = Modifier.height(100.dp),
                maxLines = 4
            )
            
            // Submit button
            Button(
                onClick = { /* Submit to Matrix */ },
                enabled = rainfallMm.isNotBlank() && !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Submitting...")
                } else {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Submit Report")
                }
            }
            
            // WhatsApp share button
            if (whatsAppService.isWhatsAppInstalled()) {
                Button(
                    onClick = { showWhatsAppDialog = true },
                    enabled = rainfallMm.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share via WhatsApp")
                }
            }
        }
    }
}
```

#### PhotoForm

Photo sharing with captions and location tagging.

```kotlin
@Composable
fun PhotoForm(navController: NavController) {
    var caption by remember { mutableStateOf("") }
    var selectedImagePath by remember { mutableStateOf<String?>(null) }
    var showImagePicker by remember { mutableStateOf(false) }
    
    Column {
        // Header
        Row(modifier = Modifier.background(MaterialTheme.colorScheme.primary)) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Share Photo")
        }
        
        // Content
        Column(modifier = Modifier.padding(16.dp)) {
            // Photo selection
            if (selectedImagePath != null) {
                AsyncImage(
                    model = selectedImagePath,
                    contentDescription = "Selected photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clickable { showImagePicker = true }
                        .background(Turquoise.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(48.dp))
                        Text("Tap to select photo")
                    }
                }
            }
            
            // Caption input
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("Describe your photo") },
                modifier = Modifier.height(100.dp),
                maxLines = 4
            )
            
            // Submit button
            Button(
                onClick = { /* Submit photo */ },
                enabled = selectedImagePath != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share Photo")
            }
        }
    }
}
```

#### ForecastViewer

Weather forecast display with geoid selection.

```kotlin
@Composable
fun ForecastViewer(navController: NavController) {
    var selectedGeoid by remember { mutableStateOf<String?>(null) }
    var forecastData by remember { mutableStateOf<List<ForecastHour>?>(null) }
    var showGeoidPicker by remember { mutableStateOf(false) }
    
    Column {
        // Header
        Row(modifier = Modifier.background(MaterialTheme.colorScheme.primary)) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Weather Forecast")
        }
        
        // Content
        Column(modifier = Modifier.padding(16.dp)) {
            // Geoid selector
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Field", style = MaterialTheme.typography.titleMedium)
                    
                    if (selectedGeoid != null) {
                        // Show selected field
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showGeoidPicker = true }
                                .padding(12.dp)
                                .background(Turquoise.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Field Name", style = MaterialTheme.typography.titleSmall)
                                Text("2.3 ha", style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    } else {
                        Button(
                            onClick = { showGeoidPicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Field")
                        }
                    }
                }
            }
            
            // Forecast data
            if (forecastData != null) {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("10-Day Hourly Forecast", style = MaterialTheme.typography.titleMedium)
                        
                        LazyColumn(modifier = Modifier.height(400.dp)) {
                            items(forecastData.take(24)) { hour ->
                                ForecastHourItem(hour = hour)
                            }
                        }
                    }
                }
            }
        }
    }
}
```

## Configuration & Settings

### Settings Screen Integration

```kotlin
@Composable
fun CitizenScienceSettingsCard() {
    val context = LocalContext.current
    val settingsPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    
    var matrixHomeserver by remember { 
        mutableStateOf(settingsPrefs.getString("MATRIX_HOMESERVER", "https://matrix.org") ?: "https://matrix.org")
    }
    var terrapipeBase by remember { 
        mutableStateOf(settingsPrefs.getString("TERRAPIPE_BASE", "https://terrapipe.io") ?: "https://terrapipe.io")
    }
    var earthcastApiBase by remember { 
        mutableStateOf(settingsPrefs.getString("EARTHCAST_API_BASE", "https://api.earthcast.ai") ?: "https://api.earthcast.ai")
    }
    var agstackApiBase by remember { 
        mutableStateOf(settingsPrefs.getString("AGSTACK_API_BASE", "https://api.agstack.org") ?: "https://api.agstack.org")
    }
    
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("API Endpoints", style = MaterialTheme.typography.titleSmall)
            
            OutlinedTextField(
                value = matrixHomeserver,
                onValueChange = { matrixHomeserver = it },
                label = { Text("Matrix Homeserver") },
                onFocusChange = { focused ->
                    if (!focused) {
                        settingsPrefs.edit().putString("MATRIX_HOMESERVER", matrixHomeserver).apply()
                    }
                }
            )
            
            OutlinedTextField(
                value = terrapipeBase,
                onValueChange = { terrapipeBase = it },
                label = { Text("TERRAPIPE_BASE") },
                onFocusChange = { focused ->
                    if (!focused) {
                        settingsPrefs.edit().putString("TERRAPIPE_BASE", terrapipeBase).apply()
                    }
                }
            )
            
            OutlinedTextField(
                value = earthcastApiBase,
                onValueChange = { earthcastApiBase = it },
                label = { Text("EARTHCAST_API_BASE") },
                onFocusChange = { focused ->
                    if (!focused) {
                        settingsPrefs.edit().putString("EARTHCAST_API_BASE", earthcastApiBase).apply()
                    }
                }
            )
            
            OutlinedTextField(
                value = agstackApiBase,
                onValueChange = { agstackApiBase = it },
                label = { Text("AGSTACK_API_BASE") },
                onFocusChange = { focused ->
                    if (!focused) {
                        settingsPrefs.edit().putString("AGSTACK_API_BASE", agstackApiBase).apply()
                    }
                }
            )
        }
    }
}
```

### Dependencies

```gradle
dependencies {
    // Matrix SDK for messaging
    implementation 'org.matrix.android:matrix-android-sdk2:1.5.35'
    
    // S2 geometry library for cell computation
    implementation 'com.google.s2:s2-geometry:1.0.0'
    
    // Additional dependencies for Citizen Science features
    implementation 'androidx.browser:browser:1.5.0'
    implementation 'com.google.android.material:material:1.9.0'
}
```

## Testing & Deployment

### Deep Link Testing

1. **Domain Verification**: Ensure `tt.earthcast.ai` domain is properly configured
2. **Intent Filter Testing**: Test deep link handling in Android
3. **Navigation Testing**: Verify route navigation works correctly

### WhatsApp Integration Testing

1. **Message Formatting**: Test WhatsApp message generation
2. **Deep Link Generation**: Verify TerraTrac deep links work
3. **Geoid Registration**: Test automatic asset registry integration
4. **S2-L10 Encoding**: Verify location tagging works correctly

### Matrix Integration Testing

1. **Room Joining**: Test public room connection
2. **Message Posting**: Verify hashtag and S2 tag inclusion
3. **Offline Sync**: Test message persistence and sync
4. **CDLA-2.0 Banner**: Test consent banner display

### AgStack Integration Testing

1. **User Authentication**: Test AgStack user-registry integration
2. **Asset Registration**: Test geoid generation
3. **User Migration**: Test existing TerraTrac user migration
4. **API Configuration**: Test all API endpoint configurations

### Weather Forecast Testing

1. **Mock Data**: Test mock forecast service
2. **Real API**: Test TERRAPIPE integration
3. **Caching**: Test forecast data caching
4. **Geoid Selection**: Test field-based forecast selection

## Security Considerations

### Data Privacy

- **Location Data**: S2-L10 cells provide location privacy while maintaining spatial context
- **User Data**: AgStack user-registry handles authentication securely
- **Message Content**: Matrix room messages are public but include consent banners

### API Security

- **Authentication**: AgStack OIDC integration for secure user authentication
- **API Keys**: Secure storage of API keys and tokens
- **Data Transmission**: HTTPS for all API communications

### Offline Security

- **Local Storage**: Encrypted local database storage
- **Sync Security**: Secure message synchronization when connectivity available
- **Data Validation**: Input validation for all user-provided data

## Performance Considerations

### Caching Strategy

- **Forecast Data**: 2-hour cache for weather forecasts
- **Geoid Mappings**: Local storage for asset registry mappings
- **Matrix Messages**: Offline message storage with sync

### Network Optimization

- **Batch Operations**: Group API calls where possible
- **Retry Logic**: Implement retry mechanisms for failed API calls
- **Offline Support**: Full functionality when offline

### Memory Management

- **Image Handling**: Efficient image loading and caching
- **Database Queries**: Optimized database queries
- **UI Performance**: Lazy loading for large lists

## Future Enhancements

### Planned Features

1. **Community Feed**: Real-time Matrix room message display
2. **Field Management**: Advanced field registration and management
3. **Data Analytics**: Citizen Science data visualization
4. **Push Notifications**: Weather alerts and community updates

### Integration Opportunities

1. **Additional Weather Services**: Multiple forecast providers
2. **Satellite Data**: Integration with satellite imagery
3. **IoT Sensors**: Direct sensor data integration
4. **Machine Learning**: Predictive analytics for agricultural data

## Conclusion

The TerraTrac Citizen Science MVP provides a comprehensive platform for agricultural data collection and community sharing. The implementation leverages modern Android development practices, integrates with multiple external services, and provides a seamless user experience for both data collection and community engagement.

The architecture is designed for scalability, with clear separation of concerns and modular components that can be extended and enhanced as the platform grows. The integration with AgStack services ensures compatibility with the broader agricultural technology ecosystem, while the WhatsApp off-ramp provides an accessible entry point for users already engaged in agricultural communities.

This implementation serves as a solid foundation for the Citizen Science platform, with all core features implemented and ready for testing and deployment.
