package com.arua.lonyichat

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.Profile
import com.arua.lonyichat.ui.viewmodel.SearchViewModel
import kotlinx.coroutines.launch

@Composable
fun NewConversationScreen(
    viewModel: SearchViewModel = viewModel()
) {
    val searchResults by viewModel.searchResults.collectAsState()
    val friendshipStatusMap by viewModel.friendshipStatus.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current as Activity

    Column(modifier = Modifier.fillMaxSize()) {
        // ✨ Modernized Search Bar ✨
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                viewModel.searchUsers(it)
            },
            label = { Text("Search for users...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search Icon")
            },
            shape = RoundedCornerShape(30.dp), // Rounded corners
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn {
            items(searchResults) { user ->
                val status = friendshipStatusMap[user.userId] ?: "none"
                UserRow(
                    user = user,
                    status = status,
                    onAction = {
                        when (status) {
                            "friends" -> { // Click the row to open chat
                                coroutineScope.launch {
                                    try {
                                        val chatId = ApiService.createChat(user.userId)
                                        val intent = Intent(context, MessageActivity::class.java).apply {
                                            putExtra("CHAT_ID", chatId)
                                            putExtra("OTHER_USER_NAME", user.name)
                                            putExtra("OTHER_USER_ID", user.userId)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // Handle error, maybe show a toast
                                    }
                                }
                            }
                            "none", "request_received" -> { // Add or Accept
                                viewModel.sendFriendRequest(user.userId)
                            }
                            // "request_sent" has no action
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun UserRow(
    user: Profile,
    status: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAction)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ✨ Added Placeholder for Profile Picture ✨
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(user.photoUrl)
                .crossfade(true)
                .placeholder(R.drawable.ic_person_placeholder) // Display this while loading
                .error(R.drawable.ic_person_placeholder)       // Display this on error or if URL is null
                .build(),
            contentDescription = "Profile Photo",
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = user.name, modifier = Modifier.weight(1f))

        when (status) {
            "friends" -> {
                // The entire row is clickable, no button needed
            }
            "request_sent" -> {
                OutlinedButton(onClick = {}, enabled = false) {
                    Text("Pending")
                }
            }
            "request_received" -> {
                Button(onClick = onAction) {
                    Text("Accept")
                }
            }
            "none" -> {
                Button(onClick = onAction) {
                    Text("Add Friend")
                }
            }
        }
    }
}