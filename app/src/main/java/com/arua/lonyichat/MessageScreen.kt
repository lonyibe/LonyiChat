package com.arua.lonyichat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
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
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MessageScreen(
    chatId: String,
    viewModel: MessageViewModel,
    otherUserName: String,
    onBackPressed: () -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onStartRecording: () -> Boolean,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
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
                    uiState.isLoading && uiState.messages.isEmpty() -> {
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
                onPickImage = onPickImage,
                onPickVideo = onPickVideo,
                onPickAudio = onPickAudio,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onCancelRecording = onCancelRecording
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
                    .clip(bubbleShape)
                    .widthIn(max = 280.dp)
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

                    when (message.type) {
                        "image" -> ImageMessage(message)
                        "video" -> VideoMessage(message)
                        "audio", "voice" -> AudioMessage(message)
                        else -> TextMessage(message, textColor)
                    }

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
        Image(
            painter = rememberAsyncImagePainter(model = message.url),
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
        Text(text = message.text)
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
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onStartRecording: () -> Boolean,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var showAttachmentMenu by remember { mutableStateOf(false) }

    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableStateOf(0L) }
    var slideOffset by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val slideToCancelDistancePx = with(density) { -150.dp.toPx() }

    // Timer for recording duration
    LaunchedEffect(isRecording) {
        recordingTime = 0L
        if (isRecording) {
            while (isRecording) {
                delay(1000)
                recordingTime++
            }
        }
    }

    Column(modifier = Modifier.animateContentSize()) {
        AnimatedVisibility(
            visible = showAttachmentMenu,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            AttachmentMenu(onPickImage, onPickVideo, onPickAudio)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (isRecording) {
                RecordingOverlay(
                    recordingTime = recordingTime,
                    slideOffset = slideOffset
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = { showAttachmentMenu = !showAttachmentMenu }) {
                        Icon(
                            if (showAttachmentMenu) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = "Attach file",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

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

                    Crossfade(targetState = text.isNotBlank(), label = "SendOrMic") { isTextEntered ->
                        if (isTextEntered) {
                            IconButton(onClick = {
                                onSendMessage(text)
                                text = ""
                            }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Message",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            val scope = rememberCoroutineScope()
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .pointerInput(Unit) {
                                        forEachGesture {
                                            awaitPointerEventScope {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                var longPressJob: Job? = null
                                                if (!isRecording) {
                                                    longPressJob = scope.launch {
                                                        delay(200)
                                                        if (onStartRecording()) {
                                                            isRecording = true
                                                        }
                                                    }
                                                }

                                                var isCancelled = false
                                                do {
                                                    val event = awaitPointerEvent()
                                                    val isPointerUp = event.changes.all { !it.pressed }

                                                    if (isPointerUp) {
                                                        longPressJob?.cancel()
                                                        if (isRecording) {
                                                            if (slideOffset < slideToCancelDistancePx) {
                                                                onCancelRecording()
                                                            } else {
                                                                onStopRecording()
                                                            }
                                                            isRecording = false
                                                            slideOffset = 0f
                                                        }
                                                        isCancelled = true
                                                    } else {
                                                        // ✨ FIXED: Manually sum the x position change
                                                        var xChange = 0f
                                                        for(change in event.changes) {
                                                            xChange += change.positionChange().x
                                                        }
                                                        slideOffset += xChange
                                                    }
                                                } while (!isCancelled)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "Record Voice Message",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun RecordingOverlay(recordingTime: Long, slideOffset: Float) {
    val slideCancelThreshold = with(LocalDensity.current) { -150.dp.toPx() }
    val isCancelled = slideOffset < slideCancelThreshold

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = Color.Red,
                modifier = Modifier.padding(start = 16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = String.format("%02d:%02d", recordingTime / 60, recordingTime % 60),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier
                .graphicsLayer {
                    translationX = slideOffset
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(visible = isCancelled, enter = fadeIn(), exit = fadeOut()) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Cancel Recording",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
            AnimatedVisibility(visible = !isCancelled, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    "Slide to cancel",
                    modifier = Modifier.padding(end = 16.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun AttachmentMenu(
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit
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
    }
}

@Composable
fun AttachmentButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp)
    ) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = description, fontSize = 12.sp)
    }
}