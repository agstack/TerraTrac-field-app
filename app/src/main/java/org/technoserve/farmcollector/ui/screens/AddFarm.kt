package org.technoserve.farmcollector.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.LocationManager
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import org.joda.time.Instant
import org.technoserve.farmcollector.R
import org.technoserve.farmcollector.database.Farm
import org.technoserve.farmcollector.database.FarmViewModel
import org.technoserve.farmcollector.database.FarmViewModelFactory
import org.technoserve.farmcollector.map.LocationHelper
import org.technoserve.farmcollector.map.LocationState
import org.technoserve.farmcollector.map.MapViewModel
import org.technoserve.farmcollector.map.getCenterOfPolygon
import org.technoserve.farmcollector.utils.convertSize
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import java.util.regex.Pattern

/**
 * This function is used to add a capture farm details and location and ddd them to the database
 */
@Composable
fun AddFarm(navController: NavController, siteId: Long) {
    var coordinatesData: List<Pair<Double, Double>>? = null
    var accuracyArrayData: List<Float?>? = null
    if (navController.currentBackStackEntry!!.savedStateHandle.contains("coordinates")) {
        val parcelableCoordinates = navController.currentBackStackEntry!!
            .savedStateHandle
            .get<List<ParcelablePair>>("coordinates")
        coordinatesData = parcelableCoordinates?.map { Pair(it.first, it.second) }
        accuracyArrayData =
            navController.currentBackStackEntry!!.savedStateHandle.get<List<Float?>>("accuracyArray")
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .fillMaxWidth()
    ) {
        FarmListHeader(
            title = stringResource(id = R.string.add_farm),
            onSearchQueryChanged = {},
            onBackClicked = { navController.popBackStack() },
            showSearch = false,
            showRestore = false,
            onRestoreClicked = {}
        )
        Spacer(modifier = Modifier.height(16.dp))
        FarmForm(navController, siteId, coordinatesData, accuracyArrayData)
    }
}

// Helper function to truncate a string representation of a number to a specific number of decimal places
fun truncateToDecimalPlaces(value: String, decimalPlaces: Int): String {
    val dotIndex = value.indexOf('.')
    return if (dotIndex == -1 || dotIndex + decimalPlaces + 1 > value.length) {
        value
    } else {
        value.substring(0, dotIndex + decimalPlaces + 1)
    }
}

// Function to read and format stored value
fun readStoredValue(sharedPref: SharedPreferences): String {
    val storedValue = sharedPref.getString("plot_size", "") ?: ""
    val formattedValue = truncateToDecimalPlaces(storedValue, 9)
    return formattedValue
}

fun formatInput(input: String): String {
    return try {
        val number = BigDecimal(input)
        val scale = number.scale()
        val decimalPlaces = scale - number.precision()
        when {
            decimalPlaces > 3 -> {
                BigDecimal(input).setScale(9, RoundingMode.DOWN).stripTrailingZeros()
                    .toPlainString()
            }
            decimalPlaces == 0 -> {
                input
            }

            else -> {
                val formattedNumber = number.setScale(9, RoundingMode.DOWN)
                formattedNumber.stripTrailingZeros().toPlainString()
            }
        }
    } catch (e: NumberFormatException) {
        input
    }
}

fun validateSize(size: String): Boolean {
    val regex = Regex("^[0-9]*\\.?[0-9]*$")
    return size.matches(regex) && size.toFloatOrNull() != null && size.toFloat() > 0 && size.isNotBlank()
}

fun validateNumber(number: String): Boolean {
    val regex = Regex("^[0-9]*\\.?[0-9]*$")
    return number.matches(regex) && number.toFloatOrNull() != null && number.toFloat() > 0 && number.isNotBlank()
}


@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun FarmForm(
    navController: NavController,
    siteId: Long,
    coordinatesData: List<Pair<Double, Double>>?,
    accuracyArrayData: List<Float?>?
) {
    val context = LocalContext.current as Activity
    var isValid by remember { mutableStateOf(true) }
    var farmerName by rememberSaveable { mutableStateOf("") }
    var memberId by rememberSaveable { mutableStateOf("") }
    val farmerPhoto by rememberSaveable { mutableStateOf("") }
    var village by rememberSaveable { mutableStateOf("") }
    var district by rememberSaveable { mutableStateOf("") }
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    var accuracyArray by rememberSaveable { mutableStateOf(listOf<Float>()) }
    val items = listOf("Ha", "Acres", "Sqm", "Timad", "Fichesa", "Manzana", "Tarea")
    var expanded by remember { mutableStateOf(false) }
    val sharedPref = context.getSharedPreferences("FarmCollector", Context.MODE_PRIVATE)
    val farmViewModel: FarmViewModel = viewModel(
        factory = FarmViewModelFactory(context.applicationContext as Application)
    )
    val mapViewModel: MapViewModel = viewModel()
    var size by rememberSaveable { mutableStateOf(readStoredValue(sharedPref)) }
    var selectedUnit by rememberSaveable {
        mutableStateOf(
            sharedPref.getString(
                "selectedUnit",
                items[0]
            ) ?: items[0]
        )
    }
    var isValidSize by remember { mutableStateOf(true) }
    var isFormSubmitted by remember { mutableStateOf(false) }
    val scientificNotationPattern = Pattern.compile("([+-]?\\d*\\.?\\d+)[eE][+-]?\\d+")
    val showDialog = remember { mutableStateOf(false) }
    val showLocationDialog = remember { mutableStateOf(false) }
    val showLocationDialogNew = remember { mutableStateOf(false) }
    // Function to update the selected unit
    fun updateSelectedUnit(newUnit: String) {
        selectedUnit = newUnit
        sharedPref.edit().putString("selectedUnit", newUnit).apply()
    }
    val locationHelper = LocationHelper(context)
    var locationState by remember { mutableStateOf<LocationState?>(null) }

    LaunchedEffect(locationHelper) {
        locationHelper.locationState.collect { state ->
            locationState = state
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                size = sharedPref.getString("plot_size", "") ?: ""
                selectedUnit = sharedPref.getString("selectedUnit", "Ha") ?: "Ha"
                with(sharedPref.edit()) {
                    remove("plot_size")
                    remove("selectedUnit")
                    apply()
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showLocationDialog.value) {
        AlertDialog(
            onDismissRequest = { showLocationDialog.value = false },
            title = { Text(stringResource(id = R.string.enable_location)) },
            text = { Text(stringResource(id = R.string.enable_location_msg)) },
            confirmButton = {
                Button(onClick = {
                    showLocationDialog.value = false
                    promptEnableLocation(context)
                }) {
                    Text(stringResource(id = R.string.yes))
                }
            },
            dismissButton = {
                Button(onClick = {
                    showLocationDialog.value = false
                    Toast.makeText(
                        context,
                        R.string.location_permission_denied_message,
                        Toast.LENGTH_SHORT
                    ).show()
                }) {
                    Text(stringResource(id = R.string.no))
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = 6.dp
        )
    }

    fun saveFarm() {
        // Validate size input if the size is empty we use the default size 0
        if (size.isEmpty()) {
            size = "0.0"
        }
        val sizeInHa = convertSize(size.toDouble(), selectedUnit)
        val newUUID = UUID.randomUUID()
        val coordinatesSize =
            coordinatesData?.size ?: 0
        val finalAccuracyArray = when {
            accuracyArray.isEmpty() -> emptyList()
            coordinatesSize == 0 -> listOf(accuracyArray[0])
            else -> {
                val result = accuracyArrayData!!.toMutableList()
                if (coordinatesSize > 1) {
                    result.add(accuracyArrayData.last())
                }
                result
            }
        }
        addFarm(
            farmViewModel,
            siteId,
            remote_id = newUUID,
            farmerPhoto,
            farmerName,
            memberId,
            village,
            district,
            0.toFloat(),
            sizeInHa.toFloat(),
            latitude,
            longitude,
            coordinates = coordinatesData,
            accuracyArray = finalAccuracyArray
        )
        val returnIntent = Intent()
        context.setResult(Activity.RESULT_OK, returnIntent)
        navController.navigate("farmList/${siteId}")
    }
    if (showDialog.value) {
        AlertDialog(
            modifier = Modifier.padding(horizontal = 32.dp),
            onDismissRequest = { showDialog.value = false },
            title = { Text(text = stringResource(id = R.string.add_farm)) },
            text = {
                Column {
                    Text(text = stringResource(id = R.string.confirm_add_farm))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    saveFarm()
                }) {
                    Text(text = stringResource(id = R.string.add_farm))
                }
            },
            dismissButton = {
                TextButton(onClick =
                {
                    showDialog.value = false
                    navController.navigate("setPolygon")
                }) {
                    Text(text = stringResource(id = R.string.set_polygon))
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = 6.dp
        )
    }

    fun validateForm(): Boolean {
        isValid = true
        val textWithNumbersRegex = Regex(".*[a-zA-Z]+.*")
        if (farmerName.isBlank() || !farmerName.matches(textWithNumbersRegex)) {
            isValid = false
        }
        if (village.isBlank() || !village.matches(textWithNumbersRegex)) {
            isValid = false
        }
        if (district.isBlank() || !district.matches(textWithNumbersRegex)) {
            isValid = false
        }
        if (size.isBlank() || size.toFloatOrNull() == null || size.toFloat() <= 0) {
            isValid = false
        }
        if (selectedUnit.isBlank()) {
            isValid = false
        }
        if (latitude.isBlank() || longitude.isBlank()) {
            isValid = false
        }
        return isValid
    }

    val scrollState = rememberScrollState()
    val fillForm = stringResource(id = R.string.fill_form)
    val showPermissionRequest = remember { mutableStateOf(false) }
    val (focusRequester1) = FocusRequester.createRefs()
    val (focusRequester2) = FocusRequester.createRefs()
    val (focusRequester3) = FocusRequester.createRefs()
    val isDarkTheme = isSystemInDarkTheme()
    val inputLabelColor = MaterialTheme.colorScheme.onBackground
    val inputTextColor = if (isDarkTheme) Color.White else Color.Black
    val inputBorder = if (isDarkTheme) Color.LightGray else Color.DarkGray
    val textWithNumbersRegex = Regex(".*[a-zA-Z]+.*")
    var isfarmerNameValid by remember { mutableStateOf(true) }
    var isvillageValid by remember { mutableStateOf(true) }
    var isDistrictValid by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(state = scrollState)
    ) {
        TextField(
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { focusRequester1.requestFocus() }
            ),
            value = farmerName,
            onValueChange = {
                farmerName = it
                isfarmerNameValid =
                    farmerName.isNotBlank() && farmerName.matches(textWithNumbersRegex)
            },
            label = {
                Text(
                    stringResource(id = R.string.farm_name) + " (*)",
                    color = inputLabelColor
                )
            },
            supportingText = {
                if (!isfarmerNameValid) {
                    Text(stringResource(R.string.error_farmer_name_empty) + " (*)")
                }
            },
            isError = !isfarmerNameValid,
            colors = TextFieldDefaults.colors(
                errorLeadingIconColor = Color.Red,
                cursorColor = inputTextColor,
                errorCursorColor = Color.Red,
                focusedIndicatorColor = inputBorder,
                unfocusedIndicatorColor = inputBorder,
                errorIndicatorColor = Color.Red
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .onKeyEvent {
                    if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                        focusRequester1.requestFocus()
                    }
                    false
                }
        )
        TextField(
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { focusRequester1.requestFocus() }
            ),
            value = memberId,
            onValueChange = { memberId = it },
            label = { Text(stringResource(id = R.string.member_id), color = inputLabelColor) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .onKeyEvent {
                    if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                        focusRequester1.requestFocus()
                    }
                    false
                }
        )
        TextField(
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { focusRequester2.requestFocus() }
            ),
            value = village,
            onValueChange = {
                village = it
                isvillageValid = village.isNotBlank() && village.matches(textWithNumbersRegex)
            },
            label = {
                Text(
                    stringResource(id = R.string.village) + " (*)",
                    color = inputLabelColor
                )
            },
            supportingText = {
                if (!isvillageValid) {
                    Text(stringResource(R.string.error_village_empty))
                }
            },
            isError = !isvillageValid,
            colors = TextFieldDefaults.colors(
                errorLeadingIconColor = Color.Red,
                cursorColor = inputTextColor,
                errorCursorColor = Color.Red,
                focusedIndicatorColor = inputBorder,
                unfocusedIndicatorColor = inputBorder,
                errorIndicatorColor = Color.Red
            ),
            modifier = Modifier
                .focusRequester(focusRequester1)
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
        TextField(
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { focusRequester3.requestFocus() }
            ),
            value = district,
            onValueChange = {
                district = it
                isDistrictValid = district.isNotBlank() && district.matches(textWithNumbersRegex)
            },
            label = {
                Text(
                    stringResource(id = R.string.district) + " (*)",
                    color = inputLabelColor
                )
            },
            supportingText = {
                if (!isDistrictValid) {
                    Text(text = stringResource(R.string.error_district_empty))
                }
            },
            isError = !isDistrictValid,
            colors = TextFieldDefaults.colors(
                errorLeadingIconColor = Color.Red,
                cursorColor = inputTextColor,
                errorCursorColor = Color.Red,
                focusedIndicatorColor = inputBorder,
                unfocusedIndicatorColor = inputBorder,
                errorIndicatorColor = Color.Red
            ),
            modifier = Modifier
                .focusRequester(focusRequester2)
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextField(
                singleLine = true,
                value = truncateToDecimalPlaces(size, 9),
                onValueChange = { inputValue ->
                    val formattedValue = when {
                        validateSize(inputValue) -> inputValue
                        scientificNotationPattern.matcher(inputValue).matches() -> {
                            truncateToDecimalPlaces(formatInput(inputValue), 9)
                        }

                        else -> inputValue
                    }
                    size = formattedValue
                    isValidSize = validateSize(formattedValue)
                    with(sharedPref.edit()) {
                        putString("plot_size", formattedValue)
                        apply()
                    }
                },
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Number
                ),
                label = {
                    Text(
                        text = stringResource(id = R.string.size_in_hectares) + " (*)",
                        color = inputLabelColor
                    )
                },
                supportingText = {
                    when {
                        isFormSubmitted && size.isBlank() -> {
                            Text(stringResource(R.string.error_farm_size_empty))
                        }

                        isFormSubmitted && !isValidSize -> {
                            Text(stringResource(R.string.error_farm_size_invalid))
                        }
                    }
                },
                isError = isFormSubmitted && (!isValidSize || size.isBlank()),
                colors = TextFieldDefaults.colors(
                    errorLeadingIconColor = Color.Red,
                    cursorColor = inputTextColor,
                    errorCursorColor = Color.Red,
                    focusedIndicatorColor = inputBorder,
                    unfocusedIndicatorColor = inputBorder,
                    errorIndicatorColor = Color.Red
                ),
                modifier = Modifier
                    .focusRequester(focusRequester3)
                    .weight(1f)
                    .padding(end = 16.dp)
            )
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.weight(1f)
            ) {
                TextField(
                    readOnly = true,
                    value = selectedUnit,
                    onValueChange = { },
                    label = { Text(stringResource(R.string.unit)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(
                            expanded = expanded
                        )
                    },
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    modifier = Modifier.background(MaterialTheme.colorScheme.background),
                    onDismissRequest = { expanded = false }
                ) {
                    items.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(text = selectionOption) },
                            onClick = {
                                updateSelectedUnit(selectionOption)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        // If coordinatesData exists and latitude/longitude are empty, calculate the center
        if (coordinatesData?.isNotEmpty() == true && latitude.isBlank() && longitude.isBlank()) {
            val center = coordinatesData.toLatLngList().getCenterOfPolygon()
            val bounds: LatLngBounds = center
            longitude = bounds.northeast.longitude.toString()
            latitude = bounds.southwest.latitude.toString()
        }

        if ((size.toDoubleOrNull()?.let { convertSize(it, selectedUnit).toFloat() } ?: 0f) < 4f) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextField(
                    readOnly = true,
                    value = latitude,
                    onValueChange = { it ->
                        val formattedValue = when {
                            validateNumber(it) -> it
                            scientificNotationPattern.matcher(it).matches() -> {
                                truncateToDecimalPlaces(formatInput(it), 9)
                            }

                            else -> {
                                // Show a Toast message if the input does not meet the requirements
                                Toast.makeText(
                                    context,
                                    R.string.error_latitude_decimal_places,
                                    Toast.LENGTH_SHORT
                                ).show()
                                null
                            }
                        }
                        formattedValue?.let {
                            latitude = it
                        }
                    },
                    label = {
                        Text(
                            stringResource(id = R.string.latitude) + " (*)",
                            color = inputLabelColor
                        )
                    },
                    supportingText = {
                        if (!isValid && latitude.split(".").last().length < 6) Text(
                            stringResource(R.string.error_latitude_decimal_places)
                        )
                    },
                    isError = !isValid && latitude.split(".").last().length < 6,
                    colors = TextFieldDefaults.colors(
                        errorLeadingIconColor = Color.Red,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 16.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                TextField(
                    readOnly = true,
                    value = longitude,
                    onValueChange = { it ->
                        val formattedValue = when {
                            validateNumber(it) -> it
                            scientificNotationPattern.matcher(it).matches() -> {
                                truncateToDecimalPlaces(formatInput(it), 9)
                            }

                            else -> {
                                // Show a Toast message if the input does not meet the requirements
                                Toast.makeText(
                                    context,
                                    R.string.error_longitude_decimal_places,
                                    Toast.LENGTH_SHORT
                                ).show()
                                null
                            }
                        }
                        formattedValue?.let {
                            longitude = it
                        }
                    },
                    label = {
                        Text(
                            stringResource(id = R.string.longitude) + " (*)",
                            color = inputLabelColor
                        )
                    },
                    supportingText = {
                        if (!isValid && longitude.split(".").last().length < 6) Text(
                            stringResource(R.string.error_longitude_decimal_places) + ""
                        )
                    },
                    isError = !isValid && longitude.split(".").last().length < 6,
                    colors = TextFieldDefaults.colors(
                        errorLeadingIconColor = Color.Red,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 16.dp)
                )
            }
        }
        if (showPermissionRequest.value) {
            LocationPermissionRequest(
                onLocationEnabled = {
                    showLocationDialog.value = true
                },
                onPermissionsGranted = {
                    showPermissionRequest.value = false
                },
                showLocationDialogNew = showLocationDialogNew,
                hasToShowDialog = showLocationDialogNew.value
            )
        }


        fun roundToDecimalPlaces(value: Double): String {
            val bigDecimal = BigDecimal.valueOf(value)
            return bigDecimal.setScale(9, BigDecimal.ROUND_DOWN).toString()
        }

        /**
         * Function to handle location permission and coordinate calculation
         */
        fun handleLocationAndNavigate(size: String, selectedUnit: String) {
            val enteredSize =
                size.toDoubleOrNull()?.let { convertSize(it, selectedUnit).toFloat() } ?: 0f
            if (coordinatesData?.isNotEmpty() == true && latitude.isBlank() && longitude.isBlank()) {
                val center = coordinatesData.toLatLngList().getCenterOfPolygon()
                val bounds: LatLngBounds = center
                latitude = roundToDecimalPlaces(bounds.northeast.longitude.toString().toDouble())
                longitude = roundToDecimalPlaces(bounds.southwest.latitude.toString().toDouble())
            }
            locationHelper.requestLocationPermissionAndUpdateCoordinates(
                enteredSize = enteredSize,
                navController = navController,
                mapViewModel = mapViewModel,
                onLocationResult = { newLatitude, newLongitude, accuracy ->
                    latitude = newLatitude
                    longitude = newLongitude
                    accuracyArray = accuracyArray + accuracy.toFloat()

                }
            )
        }

        Button(
            onClick = {
                if (isLocationEnabled(context)) {
                    handleLocationAndNavigate(size, selectedUnit)
                }
                else
                    showPermissionRequest.value = true
            },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth(0.7f)
                .height(50.dp)
                .padding(bottom = 5.dp),
            enabled = size.isNotBlank()
        ) {
            val enteredSize =
                size.toDoubleOrNull()?.let { convertSize(it, selectedUnit).toFloat() } ?: 0f

            Text(
                text = if (enteredSize >= 4f) {
                    stringResource(id = R.string.set_polygon)
                } else {
                    stringResource(id = R.string.get_coordinates)
                }
            )
        }
        Button(
            onClick = {
                isFormSubmitted = true
                // Finding the center of the polygon captured
                if (coordinatesData?.isNotEmpty() == true && latitude.isBlank() && longitude.isBlank()) {
                    val center = coordinatesData.toLatLngList().getCenterOfPolygon()
                    val bounds: LatLngBounds = center
                    longitude = bounds.northeast.longitude.toString()
                    latitude = bounds.southwest.latitude.toString()
                }
                if (validateForm()) {
                    // Ask user to confirm before adding farm
                    if (coordinatesData?.isNotEmpty() == true) saveFarm()
                    else showDialog.value = true
                } else {
                    Toast.makeText(context, fillForm, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(text = stringResource(id = R.string.add_farm))
        }
    }
    DisposableEffect(locationHelper) {
        onDispose {
            locationHelper.cleanup()
        }
    }
}

fun addFarm(
    farmViewModel: FarmViewModel,
    siteId: Long,
    remote_id: UUID,
    farmerPhoto: String,
    farmerName: String,
    memberId: String,
    village: String,
    district: String,
    purchases: Float,
    size: Float,
    latitude: String,
    longitude: String,
    coordinates: List<Pair<Double, Double>>?,
    accuracyArray: List<Float?>?
): Farm {
    val farm = Farm(
        siteId,
        remote_id,
        farmerPhoto,
        farmerName,
        memberId,
        village,
        district,
        purchases,
        size,
        latitude,
        longitude,
        coordinates,
        accuracyArray,
        createdAt = Instant.now().millis,
        updatedAt = Instant.now().millis
    )
    farmViewModel.addFarm(farm, siteId)
    return farm
}

fun isLocationEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

fun promptEnableLocation(context: Context) {
    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    context.startActivity(intent)
}


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LocationPermissionRequest(
    onLocationEnabled: () -> Unit,
    onPermissionsGranted: () -> Unit,
    showLocationDialogNew: MutableState<Boolean>,
    hasToShowDialog: Boolean
) {
    val context = LocalContext.current
    val multiplePermissionsState = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    )

    LaunchedEffect(Unit) {
        if (isLocationEnabled(context)) {
            if (multiplePermissionsState.allPermissionsGranted) {
                onPermissionsGranted()
            } else {
                multiplePermissionsState.launchMultiplePermissionRequest()
            }
        } else {
            onLocationEnabled()
        }
    }


    if ((!multiplePermissionsState.allPermissionsGranted) && hasToShowDialog) {
        Column {
            AlertDialog(
                onDismissRequest = { showLocationDialogNew.value = false },
                title = { Text(stringResource(id = R.string.enable_location)) },
                text = { Text(stringResource(id = R.string.enable_location_msg)) },
                confirmButton = {
                    Button(onClick = {
                        // Perform action to enable location permissions
                        promptEnableLocation(context)
                        showLocationDialogNew.value = false
                    }) {
                        Text(stringResource(id = R.string.yes))
                    }
                },
                dismissButton = {
                    Button(onClick = {
                        // Show a toast message indicating that the permission was denied
                        Toast.makeText(
                            context,
                            R.string.location_permission_denied_message,
                            Toast.LENGTH_SHORT
                        ).show()
                        showLocationDialogNew.value = false
                    }) {
                        Text(stringResource(id = R.string.no))
                    }
                },
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 6.dp
            )
        }
    }
}

fun List<Pair<Double, Double>>.toLatLngList(): List<LatLng> {
    return map { LatLng(it.first, it.second) }
}


