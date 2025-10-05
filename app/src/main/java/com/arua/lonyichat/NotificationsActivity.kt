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
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.arua.lonyichat.data.Notification
import com.arua.lonyichat.ui.theme.LonyiChatTheme
import com.arua.lonyichat.ui.viewmodel.NotificationViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.runBlocking // ADDED: Required for lifecycle synchronization

class NotificationsActivity : ComponentActivity() {

    private val viewModel: NotificationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LonyiChatTheme {
                NotificationScreen(
                    viewModel = viewModel,
                    onNavigateUp = { finish() }
                )
            }
        }
    }

    // ✨ POWER FIX: Trigger bulk read when the user leaves the activity (e.g., presses back or home).
    // This immediately updates the local badge counter and starts the server update process.
    override fun onPause() {
        super.onPause()
        // viewModel.markAllVisibleAsRead() // REMOVED
    }

    // ADDED START: Ensure actions are complete before the activity finishes
    override fun onDestroy() {
        super.onDestroy()
        runBlocking {
            // Await all markAsRead and accept/delete operations launched in the ViewModel.
            // This guarantees the bulk mark-as-read triggered in onPause finishes before MainActivity resumes.
            viewModel.awaitAllPendingActions()
        }
    }
    // ADDED END
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    viewModel: NotificationViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = uiState.isLoading)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = { viewModel.fetchNotifications() },
            modifier = Modifier.padding(paddingValues)
        ) {
            when {
                uiState.isLoading && uiState.notifications.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                    }
                }
                uiState.notifications.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("You have no new notifications.")
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(uiState.notifications, key = { it.id }) { notification ->
                            NotificationItem(
                                notification = notification,
                                onClick = {
                                    if (!notification.read) {
                                        viewModel.markAsRead(notification.id)
                                    }
                                    // TODO: Implement navigation to the relevant post, event, etc.
                                },
                                viewModel = viewModel
                            )
                            Divider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationItem(
    notification: Notification,
    onClick: () -> Unit,
    viewModel: NotificationViewModel // ADDED: Accept ViewModel
) {
    val backgroundColor = if (notification.read) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(backgroundColor)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(notification.sender?.photoUrl)
                    .placeholder(R.drawable.ic_person_placeholder)
                    .error(R.drawable.ic_person_placeholder)
                    .crossfade(true)
                    .build(),
                contentDescription = "Sender profile picture",
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(getNotificationIconColor(notification.type)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getNotificationIcon(notification.type),
                    contentDescription = "Notification type",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(notification.sender?.name ?: "Someone")
                    }
                    append(" ${getNotificationText(notification.type)}")
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            notification.createdAt?.toDate()?.toRelativeTimeString()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // ADDED: Action buttons for friend requests
            if (notification.type == "friend_request" && notification.sender?.id.isNullOrBlank().not() && !notification.read) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { notification.sender?.id?.let { viewModel.acceptFriendRequest(notification.id, it) } }, // ADDED safe call
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Accept", style = MaterialTheme.typography.labelLarge)
                    }
                    OutlinedButton(
                        onClick = { viewModel.deleteFriendRequest(notification.id) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Delete", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            // ADDED END
        }
    }
}

private fun getNotificationIcon(type: String): ImageVector {
    return when (type) {
        "reaction" -> Icons.Default.ThumbUp
        "comment" -> Icons.Default.Comment
        "event" -> Icons.Default.Event
        "friend_request", "friend_accepted", "friend_deleted" -> Icons.Default.Notifications // Use a relevant icon
        else -> Icons.Default.Notifications
    }
}

private fun getNotificationIconColor(type: String): Color {
    return when (type) {
        "reaction" -> Color(0xFF1877F2) // Blue
        "comment" -> Color(0xFF4CAF50) // Green
        "event" -> Color(0xFFE91E63) // Pink
        "friend_request" -> Color(0xFFFF9800) // Orange
        else -> Color.Gray
    }
}

private fun getNotificationText(type: String): String {
    return when (type) {
        "reaction" -> "reacted to your post."
        "comment" -> "commented on your post."
        "event" -> "created a new event."
        "friend_request" -> "sent you a friend request."
        "friend_accepted" -> "accepted your friend request."
        "friend_deleted" -> "dismissed your friend request."
        else -> "sent you a notification."
    }
}