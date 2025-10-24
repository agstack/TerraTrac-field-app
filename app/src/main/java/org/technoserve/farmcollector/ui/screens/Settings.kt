package org.technoserve.farmcollector.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.technoserve.farmcollector.R
import org.technoserve.farmcollector.utils.Language
import org.technoserve.farmcollector.utils.LanguageViewModel

/**
 *  This function is used to display the dark mode toggle and language selection
 */

@Composable
fun SettingsScreen(
    navController: NavController,
    darkMode: MutableState<Boolean>,
    languageViewModel: LanguageViewModel,
    languages: List<Language>
) {
    val currentLanguage by languageViewModel.currentLanguage.collectAsState()
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("theme_mode", Context.MODE_PRIVATE)

    Column(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
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
                text = stringResource(id = R.string.settings),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.light_dark_theme),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = darkMode.value,
                    onCheckedChange = {
                        darkMode.value = it
                        sharedPreferences.edit().putBoolean("dark_mode", it).apply()
                    }
                )
            }
            Text(
                text = stringResource(id = R.string.select_language),
                style = MaterialTheme.typography.titleMedium
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                languages.forEach { language ->
                    LanguageCard(
                        language = language,
                        isSelected = language == currentLanguage,
                        onSelect = {
                            languageViewModel.selectLanguage(language, context)
                        }
                    )
                }
            }
            
            // Citizen Science Settings
            Text(
                text = "Citizen Science Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            CitizenScienceSettingsCard()
        }
    }
}

@Composable
fun LanguageCard(language: Language, isSelected: Boolean, onSelect: (String) -> Unit) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .clickable { onSelect(language.code) },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Text(
            text = language.displayName,
            modifier = Modifier.padding(16.dp),
        )
    }
}

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
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "API Endpoints",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            OutlinedTextField(
                value = matrixHomeserver,
                onValueChange = { matrixHomeserver = it },
                label = { Text("Matrix Homeserver") },
                modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.fillMaxWidth(),
                onFocusChange = { focused ->
                    if (!focused) {
                        settingsPrefs.edit().putString("AGSTACK_API_BASE", agstackApiBase).apply()
                    }
                }
            )
        }
    }
}
