package com.arua.lonyichat

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.arua.lonyichat.ui.theme.LonyiChatTheme
import com.arua.lonyichat.ui.viewmodel.EventViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.background // ✨ FIX: Added missing import for 'background'

class CreateEventActivity : ComponentActivity() {

    private val viewModel: EventViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LonyiChatTheme {
                CreateEventScreen(
                    viewModel = viewModel,
                    onNavigateUp = { finish() },
                    onSuccess = {
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventScreen(
    viewModel: EventViewModel,
    onNavigateUp: () -> Unit,
    onSuccess: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf<Date?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current as ComponentActivity

    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault()) }

    val datePickerDialogState = remember { mutableStateOf(false) }
    val timePickerDialogState = remember { mutableStateOf(false) }
    var dateSelection by remember { mutableStateOf(Calendar.getInstance()) }

    // --- Image Picker ---
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }
    // --------------------

    // --- Side Effects ---
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, "Error: $it", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(uiState.createSuccess) {
        if (uiState.createSuccess) {
            Toast.makeText(context, "Event created successfully!", Toast.LENGTH_SHORT).show()
            onSuccess()
        }
    }
    // --------------------

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create a Church Event") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Image Upload Preview ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { imagePickerLauncher.launch("image/*") }
                    .background(if (selectedImageUri == null) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent), // ✨ FIXED: Corrected background usage
                contentAlignment = Alignment.Center
            ) {
                if (selectedImageUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(selectedImageUri).build(),
                        contentDescription = "Selected Event Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = "Add Photo", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Add Event Photo (Required)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // --- Text Fields ---
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Event Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Event Details (Description)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 100.dp)
            )

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location (Physical/Online Link)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // --- Date Picker Button ---
            OutlinedTextField(
                value = selectedDate?.let { dateFormatter.format(it) } ?: "Select Date and Time",
                onValueChange = { /* Read-only */ },
                label = { Text("Date & Time") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { datePickerDialogState.value = true },
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { datePickerDialogState.value = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Select Date")
                    }
                }
            )

            // --- Date Picker Dialogs ---
            if (datePickerDialogState.value) {
                // Mock/Simplified DatePickerDialog
                AlertDialog(
                    onDismissRequest = { datePickerDialogState.value = false },
                    title = { Text("Select Date") },
                    text = { Text("Assuming DatePicker component handles selection.") },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerDialogState.value = false
                            timePickerDialogState.value = true
                            dateSelection.time = Date() // Mock date selection here
                        }) { Text("OK") }
                    }
                )
            }

            if (timePickerDialogState.value) {
                // Mock/Simplified TimePickerDialog
                AlertDialog(
                    onDismissRequest = { timePickerDialogState.value = false },
                    title = { Text("Select Time") },
                    text = { Text("Assuming TimePicker component handles selection.") },
                    confirmButton = {
                        TextButton(onClick = {
                            timePickerDialogState.value = false
                            selectedDate = Date() // Mock final date selection
                        }) { Text("OK") }
                    }
                )
            }

            // --- Submit Button ---
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (selectedImageUri != null) {
                        viewModel.createEventWithPhoto(
                            title,
                            description,
                            selectedImageUri!!,
                            selectedDate?.time ?: 0L,
                            location,
                            context
                        )
                    } else {
                        Toast.makeText(context, "Please select an event photo.", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = title.isNotBlank() && description.isNotBlank() && location.isNotBlank() && selectedDate != null && selectedImageUri != null && !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text("Publish Event")
                }
            }
        }
    }
}