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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.arua.lonyichat.data.Profile
import com.arua.lonyichat.ui.theme.LonyiChatTheme
import com.arua.lonyichat.ui.viewmodel.HomeFeedViewModel
import com.arua.lonyichat.ui.viewmodel.ProfileViewModel

class CreatePostActivity : ComponentActivity() {

    private val homeFeedViewModel: HomeFeedViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LonyiChatTheme {
                val profileState by profileViewModel.uiState.collectAsState()

                CreatePostScreen(
                    viewModel = homeFeedViewModel,
                    profile = profileState.profile,
                    onPostSuccess = {
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
fun CreatePostScreen(
    viewModel: HomeFeedViewModel,
    profile: Profile?,
    onPostSuccess: () -> Unit,
    onNavigateUp: () -> Unit
) {
    var postContent by remember { mutableStateOf("") }
    // REVERTED: Now only handles the selected image URI
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current as Activity

    // Launcher only for selecting an image (no video/audio logic)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    // REMOVED: mediaPickerLauncher for video/audio is gone.

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Create Post") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            // SIMPLIFIED LOGIC: Only handles photo upload or text post
                            if (selectedImageUri != null) {
                                viewModel.createPhotoPost(postContent, selectedImageUri!!, context)
                            } else {
                                viewModel.createPost(postContent, "post")
                            }
                        },
                        // SIMPLIFIED ENABLEMENT: Only checks for text or selected image URI
                        enabled = (postContent.isNotBlank() || selectedImageUri != null) && !uiState.isUploading
                    ) {
                        if (uiState.isUploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Post")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Photo Icon (Original functionality)
                    IconButton(onClick = {
                        selectedImageUri = null // Clear old selection if any
                        imagePickerLauncher.launch("image/*")
                    }) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = "Add Photo",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // REMOVED: Video Icon is gone.

                    // REMOVED: Music Icon is gone.
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = profile?.photoUrl,
                    contentDescription = "Your Profile Photo",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    placeholder = painterResource(id = R.drawable.ic_person_placeholder),
                    error = painterResource(id = R.drawable.ic_person_placeholder)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = profile?.name ?: "User",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = postContent,
                onValueChange = { postContent = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 150.dp),
                // Reverting hint to simple prompt
                placeholder = { Text("What is on your heart?") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )

            // SIMPLIFIED PREVIEW LOGIC: Only shows image preview
            if (selectedImageUri != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(contentAlignment = Alignment.TopEnd) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "Selected image preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(onClick = { selectedImageUri = null }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove Image",
                            tint = Color.White,
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        )
                    }
                }
            }
        }
    }

    val postCreationSuccess by viewModel.postCreationSuccess.collectAsState()
    LaunchedEffect(postCreationSuccess) {
        if (postCreationSuccess) {
            onPostSuccess()
            viewModel.postCreationSuccessShown()
        }
    }
}