package org.technoserve.farmcollector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.technoserve.farmcollector.ui.theme.Teal
import org.technoserve.farmcollector.ui.theme.Turquoise
import org.technoserve.farmcollector.ui.theme.White
import java.text.SimpleDateFormat
import java.util.*

/**
 * Weather forecast viewer for Citizen Science
 * Shows hourly weather predictions for selected geoid
 */
@Composable
fun ForecastViewer(navController: NavController) {
    var selectedGeoid by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showGeoidPicker by remember { mutableStateOf(false) }
    var forecastData by remember { mutableStateOf<List<ForecastHour>?>(null) }
    
    // Mock geoid options
    val availableGeoids = listOf(
        GeoidOption("agstack:hn:12345", "Field 1 - North Farm", "2.3 ha"),
        GeoidOption("agstack:hn:12346", "Field 2 - South Farm", "1.8 ha"),
        GeoidOption("agstack:hn:12347", "Field 3 - East Farm", "3.1 ha")
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Weather Forecast",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        
        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Geoid selector
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Select Field",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (selectedGeoid != null) {
                        val selected = availableGeoids.find { it.geoid == selectedGeoid }
                        if (selected != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showGeoidPicker = true }
                                    .padding(12.dp)
                                    .background(
                                        Turquoise.copy(alpha = 0.1f),
                                        RoundedCornerShape(8.dp)
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Turquoise
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selected.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = selected.area,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = { showGeoidPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Turquoise)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Field", color = White)
                        }
                    }
                }
            }
            
            // Refresh button
            if (selectedGeoid != null) {
                Button(
                    onClick = {
                        isLoading = true
                        // TODO: Refresh forecast data
                        kotlinx.coroutines.GlobalScope.launch {
                            kotlinx.coroutines.delay(1000)
                            isLoading = false
                            // Load mock forecast data
                            forecastData = generateMockForecastData()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Loading...", color = White)
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Refresh Forecast", color = White)
                    }
                }
            }
            
            // Forecast data
            if (forecastData != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "10-Day Hourly Forecast",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Show first 24 hours
                        val next24Hours = forecastData.take(24)
                        LazyColumn(
                            modifier = Modifier.height(400.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(next24Hours) { hour ->
                                ForecastHourItem(hour = hour)
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Geoid picker dialog
    if (showGeoidPicker) {
        AlertDialog(
            onDismissRequest = { showGeoidPicker = false },
            title = { Text("Select Field") },
            text = {
                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableGeoids) { geoid ->
                        GeoidOptionItem(
                            geoid = geoid,
                            onClick = {
                                selectedGeoid = geoid.geoid
                                showGeoidPicker = false
                                // Load forecast for selected geoid
                                isLoading = true
                                kotlinx.coroutines.GlobalScope.launch {
                                    kotlinx.coroutines.delay(1000)
                                    isLoading = false
                                    forecastData = generateMockForecastData()
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGeoidPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ForecastHourItem(hour: ForecastHour) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = hour.time,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(60.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Precipitation
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.WaterDrop,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF2196F3)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${String.format("%.1f", hour.precip)}mm",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            // Temperature
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.Thermostat,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFFFF9800)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${String.format("%.1f", hour.temp)}°C",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            // Wind
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.Air,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF9C27B0)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${String.format("%.1f", hour.wind)}m/s",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun GeoidOptionItem(geoid: GeoidOption, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = Turquoise
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = geoid.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = geoid.area,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

data class GeoidOption(
    val geoid: String,
    val name: String,
    val area: String
)

data class ForecastHour(
    val time: String,
    val precip: Double,
    val temp: Double,
    val wind: Double,
    val humidity: Double
)

fun generateMockForecastData(): List<ForecastHour> {
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val now = Calendar.getInstance()
    
    return (0 until 24).map { i ->
        val hour = Calendar.getInstance()
        hour.time = now.time
        hour.add(Calendar.HOUR_OF_DAY, i)
        
        ForecastHour(
            time = dateFormat.format(hour.time),
            precip = (Math.random() * 5.0).let { if (it < 0.5) 0.0 else it },
            temp = 20.0 + (Math.random() - 0.5) * 10.0,
            wind = 1.0 + Math.random() * 5.0,
            humidity = 40.0 + Math.random() * 40.0
        )
    }
}
