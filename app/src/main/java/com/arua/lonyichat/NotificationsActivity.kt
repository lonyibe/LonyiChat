package com.arua.lonyichat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.arua.lonyichat.data.Notification
import com.arua.lonyichat.ui.theme.LonyiChatTheme
import com.arua.lonyichat.ui.viewmodel.NotificationUiState
import com.arua.lonyichat.ui.viewmodel.NotificationViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

class NotificationsActivity : ComponentActivity() {

    // ✨ ADDED: ViewModel instance
    private val viewModel: NotificationViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LonyiChatTheme {
                // ✨ UPDATED: Collect state from ViewModel
                val uiState by viewModel.uiState.collectAsState()

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Notifications") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    NotificationScreenContent(
                        modifier = Modifier.padding(innerPadding),
                        uiState = uiState,
                        onRefresh = { viewModel.fetchNotifications() },
                        onNotificationClicked = { notification ->
                            viewModel.markAsRead(notification.id)
                            // TODO: Navigate to the specific post, event, etc.
                            // Example:
                            // val intent = Intent(this, CommentsActivity::class.java)
                            // intent.putExtra("POST_ID", notification.resourceId)
                            // startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationScreenContent(
    modifier: Modifier = Modifier,
    uiState: NotificationUiState,
    onRefresh: () -> Unit,
    onNotificationClicked: (Notification) -> Unit
) {
    SwipeRefresh(
        state = rememberSwipeRefreshState(isRefreshing = uiState.isLoading),
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        when {
            uiState.isLoading && uiState.notifications.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                }
            }
            uiState.notifications.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("You have no new notifications.")
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.notifications, key = { it.id }) { notification ->
                        NotificationItem(
                            notification = notification,
                            onClick = { onNotificationClicked(notification) }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationItem(notification: Notification, onClick: () -> Unit) {
    val backgroundColor = if (notification.read) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(notification.sender.photoUrl)
                .crossfade(true)
                .placeholder(R.drawable.ic_person_placeholder)
                .error(R.drawable.ic_person_placeholder)
                .build(),
            contentDescription = "Sender's profile picture",
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            val notificationText = buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(notification.sender.name)
                }
                when (notification.type) {
                    "reaction" -> append(" reacted to your post.")
                    "comment" -> append(" commented on your post.")
                    "event" -> append(" created a new event.")
                    "friend_request" -> append(" sent you a friend request.")
                    else -> append(" sent you a notification.")
                }
            }
            Text(text = notificationText)
            Text(
                text = notification.createdAt.toDate().toRelativeTimeString(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}