// lonyibe/lonyichat/LonyiChat-87a97249019887eaa5b777f1336cd7c6a85c85c1/app/src/main/java/com/arua/lonyichat/MessageScreen.kt
package com.arua.lonyichat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MessageScreen(
    chatId: String,
    viewModel: MessageViewModel,
    otherUserName: String,
    onBackPressed: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val isDarkTheme = isSystemInDarkTheme()
    val currentUserId = ApiService.getCurrentUserId()

    // Read the IME inset directly for keyboard height tracking
    val keyboardHeight = WindowInsets.ime.getBottom(LocalDensity.current)

    LaunchedEffect(chatId) {
        viewModel.loadMessages(chatId)
    }

    // FIX: Use scrollToItem() for instantaneous (super fast) scroll when keyboard/messages change
    LaunchedEffect(uiState.messages.size, keyboardHeight) {
        if (uiState.messages.isNotEmpty()) {
            listState.scrollToItem( // Changed from animateScrollToItem
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
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
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
                    uiState.isLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.error != null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = uiState.error!!,
                                color = MaterialTheme.colorScheme.error,
                            )
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
                }
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
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    if (!isSentByCurrentUser) {
                        Text(
                            text = message.senderName,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(text = message.text, color = textColor)
                    Text(
                        text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(message.timestamp),
                        fontSize = 12.sp,
                        color = textColor.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
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
    onSendMessage: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = {
                onSendMessage(text)
                text = ""
            },
            enabled = text.isNotBlank(),
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send Message",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}