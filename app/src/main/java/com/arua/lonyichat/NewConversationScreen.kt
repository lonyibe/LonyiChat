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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    val loadingStatusUserIds by viewModel.loadingStatusUserIds.collectAsState()
    // ✨ ADDED: Collect the main search loading state ✨
    val isSearching by viewModel.isSearching.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current as Activity

    Column(modifier = Modifier.fillMaxSize()) {
        // Modernized Search Bar
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
            shape = RoundedCornerShape(30.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // ✨ ADDED: Handle loading, empty, and results states ✨
        when {
            // 1. Show a loading spinner for the initial search
            isSearching -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            // 2. Show a message if the search is done, the query is not empty, and there are no results
            searchResults.isEmpty() && searchQuery.isNotBlank() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No users found.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 3. Show the list of results
            else -> {
                LazyColumn {
                    items(searchResults) { user ->
                        val status = friendshipStatusMap[user.userId]
                        UserRow(
                            user = user,
                            status = status,
                            isLoading = user.userId in loadingStatusUserIds,
                            onAction = {
                                if (status != null) {
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
                                                    // Handle error
                                                }
                                            }
                                        }
                                        "none", "request_received" -> { // Add or Accept
                                            viewModel.sendFriendRequest(user.userId)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UserRow(
    user: Profile,
    status: String?,
    isLoading: Boolean,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading && status == "friends", onClick = onAction)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(user.photoUrl)
                .crossfade(true)
                .placeholder(R.drawable.ic_person_placeholder)
                .error(R.drawable.ic_person_placeholder)
                .build(),
            contentDescription = "Profile Photo",
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = user.name, modifier = Modifier.weight(1f))

        Box(contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                when (status) {
                    "friends" -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Friends",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Friends",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
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
    }
}