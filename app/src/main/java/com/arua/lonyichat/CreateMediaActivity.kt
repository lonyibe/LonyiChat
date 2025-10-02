package com.arua.lonyichat

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arua.lonyichat.ui.theme.LonyiChatTheme
import com.arua.lonyichat.ui.viewmodel.MediaViewModel

class CreateMediaActivity : ComponentActivity() {

    private val mediaViewModel: MediaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LonyiChatTheme {
                CreateMediaScreen(
                    viewModel = mediaViewModel,
                    onUploadSuccess = {
                        setResult(Activity.RESULT_OK)
                        finish()
                    },
                    onNavigateUp = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMediaScreen(
    viewModel: MediaViewModel,
    onUploadSuccess: () -> Unit,
    onNavigateUp: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current as Activity

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        selectedMediaUri = uri
        uri?.let {
            // Get the file name from the URI
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                selectedFileName = cursor.getString(nameIndex)
            }
        }
    }

    // NEW: Auto-launch the file picker as soon as the screen loads
    LaunchedEffect(Unit) {
        mediaPickerLauncher.launch(arrayOf("video/*", "audio/*"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload Media") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            selectedMediaUri?.let {
                                viewModel.uploadMedia(it, title, description, context)
                            }
                        },
                        // The button is enabled only if a title is provided AND a file is selected
                        enabled = title.isNotBlank() && selectedMediaUri != null && !uiState.isLoading
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Upload")
                        }
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
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title") },
                singleLine = true
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 100.dp),
                label = { Text("Description (optional)") }
            )

            // REMOVED: The manual "Select Video or Music File" button

            if (selectedFileName != null) {
                // Show confirmation card if a file is selected
                val isVideo = context.contentResolver.getType(selectedMediaUri!!)?.startsWith("video/") == true
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isVideo) Icons.Default.Videocam else Icons.Default.Audiotrack, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(text = selectedFileName!!, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    text = "File selected. Enter a title and tap Upload.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                // Show instruction if no file has been selected (e.g., if the user cancelled the picker)
                Text(
                    text = "A file picker should appear automatically. Please select a file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Listen for the error state to show a Toast
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, "Error: $it", Toast.LENGTH_LONG).show()
        }
    }

    // Listen for a change in the media items list, which signals a successful upload
    val mediaCount = remember { mutableStateOf(uiState.mediaItems.size) }
    LaunchedEffect(uiState.mediaItems.size) {
        if (uiState.mediaItems.size > mediaCount.value) {
            onUploadSuccess()
        }
        mediaCount.value = uiState.mediaItems.size
    }
}