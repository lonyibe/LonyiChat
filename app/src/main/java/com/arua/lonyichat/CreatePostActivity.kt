package com.arua.lonyichat

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var postType by remember { mutableStateOf("post") }
    var pollOptions by remember { mutableStateOf(listOf("", "")) }

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current as Activity

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        disabledBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    )

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
                            when (postType) {
                                "poll" -> {
                                    val validOptions = pollOptions.filter { it.isNotBlank() }
                                    if (validOptions.size >= 2) {
                                        viewModel.createPost(postContent, "poll", pollOptions = validOptions)
                                    }
                                }
                                "prayer" -> viewModel.createPost(postContent, "prayer")
                                else -> {
                                    if (selectedImageUri != null) {
                                        viewModel.createPhotoPost(postContent, selectedImageUri!!, context)
                                    } else {
                                        viewModel.createPost(postContent, "post")
                                    }
                                }
                            }
                        },
                        enabled = (postContent.isNotBlank() || selectedImageUri != null || (postType == "poll" && pollOptions.filter { it.isNotBlank() }.size >= 2)) && !uiState.isUploading
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // ✨ MODIFICATION: Replaced IconButton with a custom IconWithLabel composable ✨
                    IconWithLabel(
                        icon = Icons.Default.PhotoLibrary,
                        label = "Photo",
                        isSelected = postType == "post" && selectedImageUri != null,
                        onClick = {
                            selectedImageUri = null
                            postType = "post"
                            imagePickerLauncher.launch("image/*")
                        }
                    )
                    IconWithLabel(
                        icon = Icons.Default.Poll,
                        label = "Poll",
                        isSelected = postType == "poll",
                        onClick = {
                            postType = if (postType == "poll") "post" else "poll"
                            selectedImageUri = null
                        }
                    )
                    IconWithLabel(
                        painter = painterResource(id = R.drawable.ic_prayer),
                        label = "Prayer",
                        isSelected = postType == "prayer",
                        onClick = {
                            postType = if (postType == "prayer") "post" else "prayer"
                            selectedImageUri = null
                        }
                    )
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
            OutlinedTextField(
                value = postContent,
                onValueChange = { postContent = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 150.dp),
                placeholder = { Text(if (postType == "poll") "Ask a question..." else if (postType == "prayer") "What's your prayer request?" else "What is on your heart?") },
                colors = textFieldColors
            )

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

            if (postType == "poll") {
                Spacer(modifier = Modifier.height(16.dp))
                pollOptions.forEachIndexed { index, option ->
                    OutlinedTextField(
                        value = option,
                        onValueChange = {
                            val newOptions = pollOptions.toMutableList()
                            newOptions[index] = it
                            pollOptions = newOptions
                        },
                        label = { Text("Option ${index + 1}") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                TextButton(onClick = { pollOptions = pollOptions + "" }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Option")
                    Text("Add Option")
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

// ✨ NEW: Custom composable for an icon with a text label below it ✨
@Composable
fun IconWithLabel(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = contentColor
        )
    }
}

// Overload for painter resources
@Composable
fun IconWithLabel(
    painter: androidx.compose.ui.graphics.painter.Painter,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            painter = painter,
            contentDescription = label,
            tint = contentColor
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = contentColor
        )
    }
}