package com.arua.lonyichat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.ui.viewmodel.MessageViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(
    chatId: String,
    viewModel: MessageViewModel,
    otherUserName: String,
    onBackPressed: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var newMessageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // You should replace this with a dynamic way of getting the current user's ID
    val currentUserId = ApiService.getCurrentUserId()

    LaunchedEffect(chatId) {
        viewModel.loadMessages(chatId)
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(otherUserName) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        // FIX 3: Use AutoMirrored ArrowBack icon
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            MessageInputBar(
                onSendMessage = {
                    if (newMessageText.isNotBlank()) {
                        viewModel.sendMessage(chatId, newMessageText)
                        newMessageText = ""
                    }
                },
                text = newMessageText,
                onTextChanged = { newMessageText = it }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFECE5DD)) // WhatsApp-like background color
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null -> {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(uiState.messages) { message ->
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
}

@Composable
fun MessageBubble(message: Message, isSentByCurrentUser: Boolean) {
    val bubbleColor = if (isSentByCurrentUser) Color(0xFFDCF8C6) else Color.White
    // FIX 1: Use Alignment.End and Alignment.Start for horizontal alignment in a Column
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
        horizontalArrangement = if (isSentByCurrentUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isSentByCurrentUser) {
            ProfilePicture(imageUrl = message.senderPhotoUrl)
        }

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(horizontal = 8.dp),
            horizontalAlignment = alignment // This now uses the correct type
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
                    Text(text = message.text)
                    Text(
                        text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(message.timestamp),
                        fontSize = 12.sp,
                        color = Color.Gray,
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
fun MessageInputBar(
    onSendMessage: () -> Unit,
    text: String,
    onTextChanged: (String) -> Unit
) {
    Card(
        modifier = Modifier.padding(8.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = text,
                onValueChange = onTextChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                // FIX 2: Use colors instead of textFieldColors
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
            IconButton(onClick = onSendMessage) {
                // FIX 4: Use AutoMirrored Send icon
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Message")
            }
        }
    }
}