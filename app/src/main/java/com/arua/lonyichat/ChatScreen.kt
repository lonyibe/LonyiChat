package com.arua.lonyichat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.Chat
import com.arua.lonyichat.ui.viewmodel.ChatListViewModel
import java.util.*
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import java.text.SimpleDateFormat

@Composable
fun ChatScreen(viewModel: ChatListViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val currentUserId = ApiService.getCurrentUserId()
    val onRefresh = { viewModel.fetchConversations() }
    var searchQuery by remember { mutableStateOf("") }

    val filteredConversations = remember(uiState.conversations, searchQuery) {
        if (searchQuery.isBlank()) {
            uiState.conversations
        } else {
            uiState.conversations.filter { chat ->
                val otherParticipantId = chat.participants.firstOrNull { it != currentUserId }
                val chatName = chat.participantNames[otherParticipantId] ?: "Unknown User"
                chatName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { /* TODO: Navigate to a user search/create chat screen */ }) {
                Icon(Icons.Default.Add, contentDescription = "New Chat")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search Conversations") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true
            )

            when {
                uiState.isLoading && uiState.conversations.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                uiState.error != null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error) }
                else -> {
                    SwipeRefresh(
                        state = rememberSwipeRefreshState(isRefreshing = uiState.isLoading),
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredConversations) { chat ->
                                val otherParticipantId =
                                    chat.participants.firstOrNull { it != currentUserId }
                                val chatName =
                                    chat.participantNames[otherParticipantId] ?: "Unknown User"
                                ChatThreadItem(
                                    chatName,
                                    chat.lastMessage,
                                    chat.lastMessageTimestamp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}