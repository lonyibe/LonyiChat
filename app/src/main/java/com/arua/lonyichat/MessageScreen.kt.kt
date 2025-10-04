package com.arua.lonyichat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.ui.viewmodel.MessageViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.text.style.TextAlign // ADDED

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(
    chatId: String,
    viewModel: MessageViewModel,
    // ADDED START: Accept new parameters
    otherUserId: String?,
    friendshipStatus: String,
    otherUserName: String,
    // ADDED END
    onBackPressed: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var newMessageText by remember { mutableStateOf("") }
    // ADDED: Check if chat functions should be enabled
    val isChatEnabled = friendshipStatus == "friends"
    // ADDED: Message to display if chat is disabled
    val disabledMessage = when (friendshipStatus) {
        "none" -> "You need to be friends with $otherUserName to chat."
        "request_sent" -> "Friend request sent. Wait for $otherUserName to accept before chatting."
        "request_received" -> "Accept $otherUserName's friend request in Notifications to chat."
        else -> "Chat is disabled."
    }

    LaunchedEffect(chatId) {
        viewModel.loadMessages(chatId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(otherUserName) }, // MODIFIED: Use otherUserName
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (isChatEnabled) { // MODIFIED: Only show input bar if chat is enabled
                BottomAppBar {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newMessageText,
                            onValueChange = { newMessageText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Type a message...") }
                        )
                        IconButton(onClick = {
                            if (newMessageText.isNotBlank()) {
                                viewModel.sendMessage(chatId, newMessageText)
                                newMessageText = ""
                            }
                        }) {
                            Icon(Icons.Default.Send, contentDescription = "Send Message")
                        }
                    }
                }
            } else { // ADDED: Show disabled message if not friends
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = disabledMessage,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 8.dp),
                    reverseLayout = true
                ) {
                    items(uiState.messages) { message ->
                        MessageBubble(message = message)
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    // Correctly check if the message is from the current user
    val isFromCurrentUser = message.senderId == ApiService.getCurrentUserId()
    val alignment = if (isFromCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (isFromCurrentUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = bubbleColor)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(text = message.text)
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(message.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}