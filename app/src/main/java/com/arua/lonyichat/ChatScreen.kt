package com.arua.lonyichat

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.ui.viewmodel.ChatListViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatListViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val currentUserId = ApiService.getCurrentUserId()

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
            FloatingActionButton(onClick = {
                context.startActivity(Intent(context, NewConversationActivity::class.java))
            }) {
                Icon(Icons.Default.Add, contentDescription = "New Chat")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
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

            SwipeRefresh(
                state = rememberSwipeRefreshState(isRefreshing = uiState.isLoading),
                onRefresh = { viewModel.fetchConversations() }
            ) {
                when {
                    uiState.isLoading && uiState.conversations.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.error != null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    filteredConversations.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No conversations found.", style = MaterialTheme.typography.headlineSmall)
                                if (searchQuery.isBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Tap the '+' button to start a new chat.")
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(filteredConversations) { chat ->
                                val otherParticipantId = chat.participants.first { it != currentUserId }
                                ChatThreadItem(
                                    chat = chat,
                                    onClick = {
                                        val intent = Intent(context, MessageActivity::class.java).apply {
                                            putExtra("CHAT_ID", chat.id)
                                            putExtra("OTHER_USER_ID", otherParticipantId)
                                            putExtra("OTHER_USER_NAME", chat.participantNames[otherParticipantId])
                                        }
                                        context.startActivity(intent)
                                    }
                                )
                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            }
                        }
                    }
                }
            }
        }
    }
}