package com.arua.lonyichat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.arua.lonyichat.data.Profile
import com.arua.lonyichat.ui.theme.LonyiChatTheme
import com.arua.lonyichat.ui.viewmodel.SearchViewModel
import com.arua.lonyichat.ui.viewmodel.UserWithFriendshipStatus

class SearchActivity : ComponentActivity() {
    private val viewModel: SearchViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LonyiChatTheme {
                val uiState by viewModel.uiState.collectAsState()
                var searchQuery by remember { mutableStateOf("") }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Search") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                viewModel.searchUsers(it)
                            },
                            label = { Text("Search for people") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )

                        when {
                            uiState.isLoading -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            uiState.error != null -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            else -> {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(uiState.searchResults) { userWithStatus ->
                                        UserSearchResultItem(
                                            userWithStatus = userWithStatus,
                                            onAddFriend = { viewModel.sendFriendRequest(userWithStatus.user.userId) },
                                            onMessage = { /* TODO: Implement messaging functionality */ }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserSearchResultItem(
    userWithStatus: UserWithFriendshipStatus,
    onAddFriend: () -> Unit,
    onMessage: () -> Unit
) {
    val userId = userWithStatus.user.userId // ADDED: Safely get the userId
    val isActionable = userId.isNullOrBlank().not() // ADDED: Check if userId is not null and not blank

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: Navigate to user profile */ }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(userWithStatus.user.photoUrl)
                    .crossfade(true)
                    .placeholder(R.drawable.ic_person_placeholder)
                    .error(R.drawable.ic_person_placeholder)
                    .build(),
                contentDescription = "Profile Photo",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = userWithStatus.user.name)
        }

        when (userWithStatus.friendshipStatus) {
            "friends" -> {
                Button(onClick = onMessage) {
                    Text("Message")
                }
            }
            "request_sent" -> {
                OutlinedButton(onClick = {}, enabled = false) {
                    Text("Request Sent")
                }
            }
            else -> {
                Button(
                    onClick = onAddFriend, // The ViewModel access now happens only if the ID is valid
                    enabled = isActionable // ADDED: Button disabled if ID is null/blank
                ) {
                    Text("Add Friend")
                }
            }
        }
    }
}