package com.arua.lonyichat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.rememberAsyncImagePainter
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.ui.theme.LonyiDarkSurface
import com.arua.lonyichat.ui.theme.LonyiDarkTextPrimary
import com.arua.lonyichat.ui.theme.LonyiOrange
import com.arua.lonyichat.ui.viewmodel.MessageViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

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

    // ✨ START: State management for message actions
    var replyingToMessage by remember { mutableStateOf<Message?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var messageWithOptions by remember { mutableStateOf<Message?>(null) }
    // ✨ END: State management

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

    // ✨ START: Show message options dialog when a message is selected
    if (messageWithOptions != null) {
        MessageOptionsDialog(
            message = messageWithOptions!!,
            onDismiss = { messageWithOptions = null },
            onReply = {
                replyingToMessage = it
                messageWithOptions = null
            },
            onEdit = {
                editingMessage = it
                messageWithOptions = null
            },
            onDelete = {
                viewModel.deleteMessage(it.id)
                messageWithOptions = null
            },
            onReact = { message, emoji ->
                viewModel.reactToMessage(message.id, emoji)
                messageWithOptions = null
            },
            isSentByCurrentUser = messageWithOptions?.senderId == currentUserId
        )
    }
    // ✨ END: Show message options dialog

    // ✨ START: Show edit message dialog
    if (editingMessage != null) {
        EditMessageDialog(
            message = editingMessage!!,
            onDismiss = { editingMessage = null },
            onSave = { updatedText ->
                viewModel.editMessage(editingMessage!!.id, updatedText)
                editingMessage = null
            }
        )
    }
    // ✨ END: Show edit message dialog

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
                                        isSentByCurrentUser = message.senderId == currentUserId,
                                        // ✨ ADDED: Long click to show options
                                        onLongClick = { messageWithOptions = message }
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
                        // ✨ UPDATED: Pass reply info to ViewModel
                        viewModel.sendMessage(
                            chatId = chatId,
                            text = text,
                            repliedToMessageId = replyingToMessage?.id,
                            repliedToMessageContent = replyingToMessage?.text
                        )
                        replyingToMessage = null // Clear reply state after sending
                    }
                },
                // ✨ START: Pass reply state to MessageInput
                replyingTo = replyingToMessage,
                onCancelReply = { replyingToMessage = null },
                // ✨ END: Pass reply state
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isSentByCurrentUser: Boolean,
    onLongClick: () -> Unit // ✨ ADDED: Callback for long click
) {
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
                    // ✨ UPDATED: Use combinedClickable for long press
                    .combinedClickable(
                        onClick = { /* Can be used for message details later */ },
                        onLongClick = onLongClick
                    )
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

                    // ✨ ADDED: Display for replied message
                    if (message.repliedToMessageContent != null) {
                        ReplyPreview(
                            content = message.repliedToMessageContent,
                            isSentByCurrentUser = isSentByCurrentUser
                        )
                    }

                    when (message.type) {
                        "image" -> ImageMessage(message)
                        "video" -> VideoMessage(message)
                        "audio", "voice" -> AudioMessage(message, isSentByCurrentUser)
                        else -> TextMessage(message, textColor)
                    }

                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ✨ ADDED: Display 'Edited' text if message is edited
                        if (message.isEdited) {
                            Text(
                                text = "Edited",
                                fontSize = 11.sp,
                                color = textColor.copy(alpha = 0.7f),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                        Text(
                            text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(message.timestamp),
                            fontSize = 12.sp,
                            color = if (message.type == "image" || message.type == "video") Color.White.copy(alpha = 0.8f) else textColor.copy(alpha = 0.7f),
                            modifier = Modifier
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
            // ✨ ADDED: Display for reactions
            if (message.reactions.isNotEmpty()) {
                ReactionsDisplay(
                    reactions = message.reactions,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// ✨ NEW: Composable to show a preview of a replied message inside a bubble
@Composable
fun ReplyPreview(content: String, isSentByCurrentUser: Boolean) {
    val replyColor = if (isSentByCurrentUser) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(replyColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

// ✨ NEW: Composable to display reactions below a message
@Composable
fun ReactionsDisplay(reactions: Map<String, List<String>>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        reactions.forEach { (emoji, userIds) ->
            if (userIds.isNotEmpty()) {
                Text(
                    text = "$emoji ${userIds.size}",
                    fontSize = 12.sp
                )
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
fun AudioMessage(message: Message, isSentByCurrentUser: Boolean) {
    message.url?.let { url ->
        VoiceMessagePlayer(
            url = url,
            isSentByCurrentUser = isSentByCurrentUser
        )
    } ?: Text(message.text)
}

@Composable
private fun VoiceMessagePlayer(url: String, isSentByCurrentUser: Boolean) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingValue: Boolean) {
                isPlaying = isPlayingValue
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            delay(50)
        }
    }

    val progress = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()) else 0f

    val waveformColor = if (isSentByCurrentUser) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    val progressColor = if (isSentByCurrentUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        IconButton(onClick = {
            if (exoPlayer.isPlaying) {
                exoPlayer.pause()
            } else {
                if(exoPlayer.currentPosition >= exoPlayer.duration){
                    exoPlayer.seekTo(0)
                }
                exoPlayer.play()
            }
        }) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = progressColor
            )
        }

        Box(modifier = Modifier.weight(1f).height(30.dp), contentAlignment = Alignment.CenterStart) {
            Waveform(
                modifier = Modifier.fillMaxSize(),
                progress = progress,
                waveformColor = waveformColor,
                progressColor = progressColor
            )
            Slider(
                value = progress,
                onValueChange = { newProgress ->
                    val newPosition = (newProgress * duration).toLong()
                    currentPosition = newPosition
                    exoPlayer.seekTo(newPosition)
                },
                modifier = Modifier.fillMaxSize(),
                colors = SliderDefaults.colors(
                    thumbColor = progressColor,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                )
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatTime(duration),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun Waveform(
    modifier: Modifier = Modifier,
    progress: Float,
    waveformColor: Color,
    progressColor: Color
) {
    val waveformData = remember {
        listOf(0.2f, 0.5f, 0.7f, 0.4f, 0.8f, 0.3f, 0.6f, 0.9f, 0.5f, 0.7f, 0.4f, 0.8f, 0.3f, 0.6f, 0.9f, 0.5f, 0.7f, 0.4f, 0.8f, 0.3f, 0.6f, 0.9f, 0.5f, 0.7f, 0.4f, 0.8f, 0.3f, 0.2f, 0.5f, 0.7f)
    }

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val barWidth = 3.dp.toPx()
        val barSpacing = 2.dp.toPx()
        val totalBarWidth = barWidth + barSpacing
        val progressWidth = canvasWidth * progress

        for (i in 0..30) {
            val value = waveformData.getOrElse(i) { 0.2f }
            val barHeight = canvasHeight * value
            val startX = i * totalBarWidth
            val startY = (canvasHeight - barHeight) / 2

            val color = if (startX < progressWidth) progressColor else waveformColor

            drawRoundRect(
                color = color,
                topLeft = Offset(x = startX, y = startY),
                size = Size(width = barWidth, height = barHeight),
                cornerRadius = CornerRadius(barWidth / 2)
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms < 0) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
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
    replyingTo: Message?, // ✨ ADDED
    onCancelReply: () -> Unit, // ✨ ADDED
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

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingTime = 0L
            while (isRecording) {
                delay(1000)
                recordingTime++
            }
        }
    }

    Column(modifier = Modifier.animateContentSize()) {
        // ✨ ADDED: Reply Preview UI
        AnimatedVisibility(visible = replyingTo != null) {
            replyingTo?.let {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Replying to ${it.senderName}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            it.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(onClick = onCancelReply) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel Reply")
                    }
                }
            }
        }

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
                RecordingControls(
                    recordingTime = recordingTime,
                    onSendClick = {
                        onStopRecording()
                        isRecording = false
                    },
                    onCancelClick = {
                        onCancelRecording()
                        isRecording = false
                    }
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
                            IconButton(onClick = {
                                isRecording = onStartRecording()
                            }) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "Record Voice Message",
                                    tint = MaterialTheme.colorScheme.primary
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
fun RecordingControls(
    recordingTime: Long,
    onSendClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onCancelClick) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Cancel Recording",
                tint = MaterialTheme.colorScheme.error
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
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

        IconButton(onClick = onSendClick) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send Recording",
                tint = MaterialTheme.colorScheme.primary
            )
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
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = description, fontSize = 12.sp)
    }
}

// ✨ NEW: Dialog for message options
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageOptionsDialog(
    message: Message,
    onDismiss: () -> Unit,
    onReply: (Message) -> Unit,
    onEdit: (Message) -> Unit,
    onDelete: (Message) -> Unit,
    onReact: (Message, String) -> Unit,
    isSentByCurrentUser: Boolean
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            ReactionPicker(onEmojiSelected = { onReact(message, it) })
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            OptionItem(icon = Icons.AutoMirrored.Filled.Reply, text = "Reply", onClick = { onReply(message) })
            if (isSentByCurrentUser && message.type == "text") { // Only allow editing for text messages
                OptionItem(icon = Icons.Default.Edit, text = "Edit", onClick = { onEdit(message) })
            }
            if (isSentByCurrentUser) {
                OptionItem(icon = Icons.Default.Delete, text = "Delete", onClick = { onDelete(message) }, isDestructive = true)
            }
        }
    }
}

// ✨ NEW: Reaction picker UI
@Composable
fun ReactionPicker(onEmojiSelected: (String) -> Unit) {
    val reactions = listOf("👍", "🙏", "❤️", "🤣")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        reactions.forEach { emoji ->
            Text(
                text = emoji,
                fontSize = 24.sp,
                modifier = Modifier
                    .clickable { onEmojiSelected(emoji) }
                    .padding(8.dp)
            )
        }
    }
}

// ✨ NEW: A single item in the options dialog
@Composable
fun OptionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit, isDestructive: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ✨ NEW: Dialog for editing a message
@Composable
fun EditMessageDialog(
    message: Message,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(message.text) }

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Edit Message", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onSave(text) }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}