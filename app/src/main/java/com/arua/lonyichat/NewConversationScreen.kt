package com.arua.lonyichat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.Profile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewConversationScreen(
    onBackPressed: () -> Unit,
    onStartChat: (String) -> Unit // Callback with the newly created chatId
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            isLoading = true
            errorMessage = null
            try {
                searchResults = ApiService.searchUsers(searchQuery)
            } catch (e: Exception) {
                errorMessage = "Failed to search users: ${e.message}"
            } finally {
                isLoading = false
            }
        } else {
            searchResults = emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Conversation") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search for users") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true
            )

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(searchResults) { user ->
                            UserItem(user = user, onClick = {
                                // Create a new chat and then navigate
                                // This part will be implemented in the ViewModel/ApiService later
                                // For now, we'll just simulate it
                                println("Starting chat with ${user.name}")
                                // onStartChat(newlyCreatedChatId)
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserItem(user: Profile, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // You can add a profile picture here later
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = user.name, style = MaterialTheme.typography.bodyLarge)
    }
}