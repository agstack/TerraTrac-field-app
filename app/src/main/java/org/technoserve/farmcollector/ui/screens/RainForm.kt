package org.technoserve.farmcollector.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.technoserve.farmcollector.ui.theme.Teal
import org.technoserve.farmcollector.ui.theme.Turquoise
import org.technoserve.farmcollector.ui.theme.White
import org.technoserve.farmcollector.services.WhatsAppOffRampService
import org.technoserve.farmcollector.services.GeoidService
import org.technoserve.farmcollector.services.MessagingRepository
import org.technoserve.farmcollector.utils.S2Utils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Rain reporting form for Citizen Science
 * Allows users to report rainfall measurements
 */
@Composable
fun RainForm(navController: NavController) {
    var rainfallMm by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showWhatsAppDialog by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    
    // Initialize services
    val context = LocalContext.current
    val whatsAppService = remember { WhatsAppOffRampService(context) }
    val geoidService = remember { GeoidService(context, null) }
    val messagingRepository = remember { MessagingRepository(context) }
    
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
                text = "Report Rainfall",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        
        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Instructions card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Turquoise.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.WaterDrop,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Turquoise
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Help track rainfall patterns in your area. Your observations contribute to agricultural research and weather monitoring.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            // Rainfall input
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Rainfall Measurement",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    OutlinedTextField(
                        value = rainfallMm,
                        onValueChange = { rainfallMm = it },
                        label = { Text("Rainfall (mm)") },
                        placeholder = { Text("e.g., 5.2") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(
                                Icons.Default.WaterDrop,
                                contentDescription = null,
                                tint = Turquoise
                            )
                        },
                        suffix = { Text("mm") }
                    )
                    
                    Text(
                        text = "Tip: Use a rain gauge or measure collected water in a container",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Notes input
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Additional Notes (Optional)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes") },
                        placeholder = { Text("e.g., Heavy downpour, lasted 2 hours, field conditions...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        maxLines = 4
                    )
                }
            }
            
            // Location info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Teal
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Location will be automatically tagged with your current position",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Submit button
            Button(
                onClick = {
                    if (rainfallMm.isNotBlank()) {
                        isSubmitting = true
                        GlobalScope.launch {
                            try {
                                val rainfall = rainfallMm.toDouble()
                                val location = currentLocation ?: Pair(0.0, 0.0) // Default location
                                
                                // Generate S2 token
                                val s2Token = S2Utils.s2CellL10Safe(location.first, location.second)
                                
                                // Format message for Matrix
                                val message = buildString {
                                    append("Rainfall: ${String.format("%.1f", rainfall)}mm")
                                    if (notes.isNotBlank()) {
                                        append(" - $notes")
                                    }
                                    append(" #citizenscience")
                                    s2Token?.let { append(" @s2:10:$it") }
                                }
                                
                                // Post to Matrix room
                                messagingRepository.sendTextMessage(message, s2Token)
                                
                                isSubmitting = false
                                showSuccessDialog = true
                            } catch (e: Exception) {
                                isSubmitting = false
                                showSuccessDialog = true
                            }
                        }
                    }
                },
                enabled = rainfallMm.isNotBlank() && !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Submitting...", color = White)
                } else {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = null,
                        tint = White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Submit Report", color = White, fontWeight = FontWeight.Bold)
                }
            }
            
            // WhatsApp share button
            if (whatsAppService.isWhatsAppInstalled()) {
                Button(
                    onClick = {
                        if (rainfallMm.isNotBlank()) {
                            showWhatsAppDialog = true
                        }
                    },
                    enabled = rainfallMm.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        tint = White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share via WhatsApp", color = White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    
    // Success dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text("Report Submitted!") },
            text = { Text("Thank you for contributing to citizen science. Your rainfall data has been shared with the community.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        navController.navigateUp()
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }
    
    // WhatsApp share dialog
    if (showWhatsAppDialog) {
        AlertDialog(
            onDismissRequest = { showWhatsAppDialog = false },
            title = { Text("Share via WhatsApp") },
            text = { Text("Share your rainfall data with others via WhatsApp. The message will include location data and a link to open in TerraTrac.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWhatsAppDialog = false
                        GlobalScope.launch {
                            try {
                                val rainfall = rainfallMm.toDouble()
                                val location = currentLocation ?: Pair(0.0, 0.0)
                                
                                val rainData = WhatsAppOffRampService.RainGaugeData(
                                    latitude = location.first,
                                    longitude = location.second,
                                    rainfallMm = rainfall,
                                    notes = notes.takeIf { it.isNotBlank() }
                                )
                                
                                whatsAppService.shareRainGaugeViaWhatsApp(rainData)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                ) {
                    Text("Share")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWhatsAppDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
