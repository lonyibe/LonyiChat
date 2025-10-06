// lonyibe/lonyichat/LonyiChat-87a97249019887eaa5b777f1336cd7c6a85c85c1/app/src/main/java/com/arua/lonyichat/MessageScreen.kt
package com.arua.lonyichat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.ui.theme.LonyiDarkSurface
import com.arua.lonyichat.ui.theme.LonyiDarkTextPrimary
import com.arua.lonyichat.ui.theme.LonyiOrange
import com.arua.lonyichat.ui.viewmodel.MessageViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MessageScreen(
    chatId: String,
    viewModel: MessageViewModel,
    otherUserName: String,
    onBackPressed: () -> Unit,
    // ✨ ADDED: Callbacks to trigger media pickers
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onRecordVoice: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val isDarkTheme = isSystemInDarkTheme()
    val currentUserId = ApiService.getCurrentUserId()
    val keyboardHeight = WindowInsets.ime.getBottom(LocalDensity.current)

    LaunchedEffect(chatId) {
        viewModel.loadMessages(chatId)
    }

    LaunchedEffect(uiState.messages.size, keyboardHeight) {
        if (uiState.messages.isNotEmpty()) {
            listState.scrollToItem(
                index = uiState.messages.size - 1,
                scrollOffset = 0
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(otherUserName) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDarkTheme) LonyiDarkSurface else LonyiOrange,
                    titleContentColor = if (isDarkTheme) LonyiDarkTextPrimary else Color.White,
                    navigationIconContentColor = if (isDarkTheme) LonyiDarkTextPrimary else Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .imePadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when {
                    uiState.isLoading && uiState.messages.isEmpty() -> { // Only show full-screen loader when messages are empty
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.error != null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = uiState.error!!, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            items(uiState.messages, key = { it.id }) { message ->
                                Box(
                                    modifier = Modifier
                                        .animateItemPlacement()
                                        .padding(vertical = 4.dp)
                                ) {
                                    MessageBubble(
                                        message = message,
                                        isSentByCurrentUser = message.senderId == currentUserId
                                    )
                                }
                            }
                        }
                    }
                }
            }
            MessageInput(
                onSendMessage = { text ->
                    if (text.isNotBlank()) {
                        viewModel.sendMessage(chatId, text)
                    }
                },
                // ✨ ADDED: Pass the media picker callbacks to the input composable
                onPickImage = onPickImage,
                onPickVideo = onPickVideo,
                onPickAudio = onPickAudio,
                onRecordVoice = onRecordVoice
            )
        }
    }
}

@Composable
fun MessageBubble(message: Message, isSentByCurrentUser: Boolean) {
    val bubbleColor = if (isSentByCurrentUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isSentByCurrentUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val alignment = if (isSentByCurrentUser) Alignment.End else Alignment.Start
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isSentByCurrentUser) 16.dp else 0.dp,
        bottomEnd = if (isSentByCurrentUser) 0.dp else 16.dp
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isSentByCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isSentByCurrentUser) {
            ProfilePicture(imageUrl = message.senderPhotoUrl)
        }

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(horizontal = 8.dp),
            horizontalAlignment = alignment
        ) {
            Box(
                modifier = Modifier
                    .background(bubbleColor, shape = bubbleShape)
                    .clip(bubbleShape) // Clip the content to the bubble shape
                    .widthIn(max = 280.dp) // Set a max width for the bubble
            ) {
                Column(modifier = Modifier.padding(horizontal = if (message.type == "image" || message.type == "video") 0.dp else 12.dp, vertical = if (message.type == "image" || message.type == "video") 0.dp else 8.dp)) {
                    if (!isSentByCurrentUser) {
                        Text(
                            text = message.senderName,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                        )
                    }

                    // ✨ NEW: Display content based on message type
                    when (message.type) {
                        "image" -> ImageMessage(message)
                        "video" -> VideoMessage(message)
                        "audio", "voice" -> AudioMessage(message)
                        else -> TextMessage(message, textColor) // Default to text
                    }

                    // Timestamp aligned to the bottom right
                    Text(
                        text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(message.timestamp),
                        fontSize = 12.sp,
                        color = if (message.type == "image" || message.type == "video") Color.White.copy(alpha = 0.8f) else textColor.copy(alpha = 0.7f),
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 4.dp, end = 8.dp, bottom = if (message.type == "image" || message.type == "video") 4.dp else 0.dp)
                            .background(
                                if (message.type == "image" || message.type == "video") Color.Black.copy(alpha = 0.3f) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 4.dp)

                    )
                }
            }
        }
    }
}
// ✨ NEW: Composables for different message types ✨
@Composable
fun TextMessage(message: Message, textColor: Color) {
    Text(text = message.text, color = textColor)
}

@Composable
fun ImageMessage(message: Message) {
    Image(
        painter = rememberAsyncImagePainter(model = message.url),
        contentDescription = "Image message",
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentScale = ContentScale.Crop
    )
    if (message.text.isNotEmpty() && message.text != "📷 Image") {
        Text(
            text = message.text,
            modifier = Modifier.padding(8.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun VideoMessage(message: Message) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // In a real app, you would use a library like ExoPlayer to show a thumbnail
        Image(
            painter = rememberAsyncImagePainter(model = message.url), // This might show the first frame if the server is configured correctly
            contentDescription = "Video message",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.6f
        )
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "Play video",
            tint = Color.White,
            modifier = Modifier
                .size(64.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .padding(8.dp)
        )
    }
}

@Composable
fun AudioMessage(message: Message) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
    ) {
        Icon(
            if (message.type == "voice") Icons.Default.Mic else Icons.Default.MusicNote,
            contentDescription = "Audio message",
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = message.text) // e.g., "🎤 Voice Message" or "🎵 Audio"
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "Play audio",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable {
                // TODO: Implement audio playback
            }
        )
    }
}


@Composable
fun ProfilePicture(imageUrl: String?) {
    Image(
        painter = rememberAsyncImagePainter(
            model = imageUrl,
            error = painterResource(id = R.drawable.ic_person_placeholder)
        ),
        contentDescription = "Profile Picture",
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape),
        contentScale = ContentScale.Crop
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInput(
    onSendMessage: (String) -> Unit,
    // ✨ ADDED: Callbacks for the new buttons
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onRecordVoice: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var showAttachmentMenu by remember { mutableStateOf(false) }

    Column {
        // ✨ NEW: The attachment menu that appears when you click the '+' button
        AnimatedVisibility(
            visible = showAttachmentMenu,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            AttachmentMenu(onPickImage, onPickVideo, onPickAudio, onRecordVoice)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attachment button
            IconButton(onClick = { showAttachmentMenu = !showAttachmentMenu }) {
                Icon(
                    if (showAttachmentMenu) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = "Attach file",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Text field
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Type a message...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Send button
            IconButton(
                onClick = {
                    onSendMessage(text)
                    text = ""
                },
                enabled = text.isNotBlank(),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Message",
                    tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
        }
    }
}

// ✨ NEW: The attachment menu composable
@Composable
fun AttachmentMenu(
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onRecordVoice: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        AttachmentButton(icon = Icons.Default.Image, description = "Image", onClick = onPickImage)
        AttachmentButton(icon = Icons.Default.Videocam, description = "Video", onClick = onPickVideo)
        AttachmentButton(icon = Icons.Default.MusicNote, description = "Audio", onClick = onPickAudio)
        AttachmentButton(icon = Icons.Default.Mic, description = "Voice", onClick = onRecordVoice)
    }
}

// ✨ NEW: A button for the attachment menu
@Composable
fun AttachmentButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = description, fontSize = 12.sp)
    }
}