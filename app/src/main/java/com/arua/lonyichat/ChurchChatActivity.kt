package com.arua.lonyichat

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.Church
import com.arua.lonyichat.data.ChurchMessage
import com.arua.lonyichat.ui.theme.LonyiChatTheme
import com.arua.lonyichat.ui.viewmodel.ChurchChatViewModel
import com.arua.lonyichat.ui.viewmodel.ChurchesViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Helper list for reactions
val ChatReactions = listOf("👍", "🙏", "❤️", "🤣")

@OptIn(ExperimentalFoundationApi::class)
class ChurchChatActivity : ComponentActivity() {

    private val viewModel: ChurchChatViewModel by viewModels()
    // Need a ViewModel that can trigger global state updates for church list and deletion
    private val churchesViewModel: ChurchesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // We receive the Church object as a Parcelable
        val church = intent.getParcelableExtra<Church>("CHURCH_EXTRA")

        if (church == null) {
            Toast.makeText(this, "Error: Church data not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            LonyiChatTheme {
                ChurchChatScreen(
                    church = church,
                    viewModel = viewModel,
                    churchesViewModel = churchesViewModel, // Pass the ChurchesViewModel
                    onNavigateUp = { finish() },
                    onChurchDeleted = {
                        setResult(Activity.RESULT_OK) // Signal MainActivity to refresh its list
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChurchChatScreen(
    church: Church,
    viewModel: ChurchChatViewModel,
    churchesViewModel: ChurchesViewModel, // Passed ViewModel
    onNavigateUp: () -> Unit,
    onChurchDeleted: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current as ComponentActivity
    val currentUserId = ApiService.getCurrentUserId()
    val isCreator = church.createdBy == currentUserId

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // State to hold a potentially updated church object (specifically for photoUrl)
    var currentChurch by remember { mutableStateOf(church) }

    // ✨ NEW: State for replying to a message (messageId, messageContent)
    var replyingToMessage by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showMessageOptionsMenu by remember { mutableStateOf<ChurchMessage?>(null) } // Message that was long-pressed

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            Toast.makeText(context, "Uploading church photo...", Toast.LENGTH_SHORT).show()
            context.lifecycleScope.launch {
                ApiService.uploadChurchPhoto(currentChurch.id, uri, context).onSuccess { newPhotoUrl ->
                    currentChurch = currentChurch.copy(photoUrl = newPhotoUrl)
                    Toast.makeText(context, "Church photo updated!", Toast.LENGTH_SHORT).show()
                    context.setResult(Activity.RESULT_OK)
                }.onFailure { error ->
                    Toast.makeText(context, "Upload failed: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- DIALOGS (Message Context Menu) ---
    showMessageOptionsMenu?.let { message ->
        MessageContextMenu(
            message = message,
            onDismiss = { showMessageOptionsMenu = null },
            onReply = {
                replyingToMessage = Pair(it.id, it.content)
                showMessageOptionsMenu = null
            },
            onReact = { reaction ->
                viewModel.reactToMessage(currentChurch.id, message.id, reaction)
                showMessageOptionsMenu = null
            },
            onDelete = {
                viewModel.deleteMessage(currentChurch.id, it.id)
                showMessageOptionsMenu = null
            }
        )
    }
    // --- END DIALOGS ---

    // --- Delete Group Confirmation Dialog ---
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Group?") },
            text = { Text("Are you sure you want to permanently delete '${currentChurch.name}'? This action cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    churchesViewModel.deleteChurch(currentChurch.id)
                    onChurchDeleted()
                    showDeleteConfirmDialog = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") } }
        )
    }

    // ✨ NEW: Leave Group Confirmation Dialog
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }
    if (showLeaveConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirmDialog = false },
            title = { Text("Leave Group?") },
            text = { Text("Are you sure you want to leave '${currentChurch.name}'?") },
            confirmButton = {
                Button(onClick = {
                    // Toggling membership when user is already a member means leaving
                    churchesViewModel.joinChurch(currentChurch.id)
                    onNavigateUp()
                    Toast.makeText(context, "Left ${currentChurch.name}", Toast.LENGTH_SHORT).show()
                    showLeaveConfirmDialog = false
                }) { Text("Leave") }
            },
            dismissButton = { TextButton(onClick = { showLeaveConfirmDialog = false }) { Text("Cancel") } }
        )
    }
    // --- END DIALOGS ---

    LaunchedEffect(currentChurch.id) {
        viewModel.fetchMessages(currentChurch.id)
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentChurch.name) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 8.dp).clickable(enabled = showMenu, onClick = { showMenu = true })) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(currentChurch.photoUrl)
                                    .crossfade(true)
                                    .placeholder(R.drawable.ic_person_placeholder)
                                    .error(R.drawable.ic_person_placeholder)
                                    .build(),
                                contentDescription = "Church Profile Photo",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            IconButton(onClick = { showMenu = !showMenu }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                if (isCreator) {
                                    DropdownMenuItem(
                                        text = { Text("Change Group Photo") },
                                        onClick = {
                                            imagePickerLauncher.launch("image/*")
                                            showMenu = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.AddAPhoto, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Add Members (TODO)") },
                                        onClick = {
                                            Toast.makeText(context, "Member management feature pending.", Toast.LENGTH_SHORT).show()
                                            showMenu = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) }
                                    )
                                    Divider()
                                    DropdownMenuItem(
                                        text = { Text("Delete Group") },
                                        onClick = {
                                            showDeleteConfirmDialog = true
                                            showMenu = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                                    )
                                }

                                // ✨ NEW: Leave Group option for all members
                                val isMember = currentChurch.members.contains(currentUserId)
                                if (isMember) {
                                    if (isCreator) Divider() // Only add divider if creator options were shown
                                    DropdownMenuItem(
                                        text = { Text("Leave Group") },
                                        onClick = {
                                            showLeaveConfirmDialog = true
                                            showMenu = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.Logout, contentDescription = null) }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            MessageInputBar(
                message = messageText,
                onMessageChange = { messageText = it },
                onSendClick = {
                    viewModel.sendMessage(
                        churchId = currentChurch.id,
                        content = messageText,
                        repliedToMessageId = replyingToMessage?.first,
                        repliedToMessageContent = replyingToMessage?.second
                    )
                    messageText = ""
                    replyingToMessage = null // Clear reply context
                },
                onCancelReply = { replyingToMessage = null },
                replyingTo = replyingToMessage?.second
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.error != null -> Text("Error: ${uiState.error}", modifier = Modifier.align(Alignment.Center))
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.messages, key = { it.id }) { message ->
                            ChatMessageItem(
                                message = message,
                                onLongClick = { showMessageOptionsMenu = it },
                                onReactionClick = { reaction ->
                                    viewModel.reactToMessage(currentChurch.id, message.id, reaction)
                                },
                                onReplyBubbleClick = { /* Scroll to replied message if implemented */ }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessageItem(
    message: ChurchMessage,
    onLongClick: (ChurchMessage) -> Unit,
    onReactionClick: (String) -> Unit,
    onReplyBubbleClick: (String) -> Unit
) {
    val isCurrentUser = message.authorId == ApiService.getCurrentUserId()
    val horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
    val bubbleColor = if (isCurrentUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = { /* Default click does nothing */ },
            onLongClick = { onLongClick(message) }
        ),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isCurrentUser) {
            AsyncImage(
                model = message.authorPhotoUrl,
                contentDescription = "Author Photo",
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                placeholder = painterResource(id = R.drawable.ic_person_placeholder)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
        ) {
            if(!isCurrentUser){
                Text(
                    text = message.authorName,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                )
            }

            // ✨ NEW: Reply Bubble
            message.repliedToMessageContent?.let { replyContent ->
                ReplyBubble(
                    content = replyContent,
                    onClick = { message.repliedToMessageId?.let(onReplyBubbleClick) }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Box(
                modifier = Modifier
                    .background(bubbleColor, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(text = message.content)
            }

            // ✨ NEW: Reaction Chips
            // The null check is now safe, using the pattern provided below
            ReactionChips(message.reactions) { emoji -> onReactionClick(emoji) }

            Text(
                text = message.createdAt.toDate().toFormattedTimeString(),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp, start = 8.dp, end = 8.dp)
            )
        }

        if (isCurrentUser) {
            Spacer(modifier = Modifier.width(8.dp))
            AsyncImage(
                model = message.authorPhotoUrl,
                contentDescription = "Author Photo",
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                placeholder = painterResource(id = R.drawable.ic_person_placeholder)
            )
        }
    }
}

@Composable
fun ReactionChips(reactions: Map<String, List<String>>?, onReactionClick: (String) -> Unit) {
    val currentUserId = ApiService.getCurrentUserId()

    // ✨ FIX: Use Elvis operator to safely handle a null reactions map.
    val safeReactions = reactions ?: emptyMap()

    if (safeReactions.isEmpty()) return

    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Iterate over the safeReactions map.
        safeReactions.filterValues { it.isNotEmpty() }.forEach { (emoji, userIds) ->
            val isReactedByMe = userIds.contains(currentUserId)

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isReactedByMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable { onReactionClick(emoji) }
            ) {
                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(emoji, color = if (isReactedByMe) Color.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(end = 4.dp))
                    Text(
                        text = userIds.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isReactedByMe) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun ReplyBubble(content: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // A visual line indicating it's a quote/reply
        Box(modifier = Modifier.width(3.dp).height(24.dp).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = "Replying to Message",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MessageInputBar(
    message: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onCancelReply: () -> Unit,
    replyingTo: String?
) {
    Surface(shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 8.dp)
        ) {
            // ✨ NEW: Reply Preview
            if (replyingTo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Replying to:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(replyingTo, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onCancelReply) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel Reply", modifier = Modifier.size(20.dp))
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = message,
                    onValueChange = onMessageChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") },
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onSendClick,
                    enabled = message.isNotBlank(),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun MessageContextMenu(
    message: ChurchMessage,
    onDismiss: () -> Unit,
    onReply: (ChurchMessage) -> Unit,
    onReact: (String) -> Unit,
    onDelete: (ChurchMessage) -> Unit
) {
    val currentUserId = ApiService.getCurrentUserId()
    val isAuthor = message.authorId == currentUserId

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message Options") },
        text = {
            Column {
                Text(message.content, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                Divider(Modifier.padding(vertical = 8.dp))
                // Reaction Buttons
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    ChatReactions.forEach { emoji ->
                        Button(onClick = {
                            onReact(emoji)
                            onDismiss()
                        }, modifier = Modifier.weight(1f).padding(horizontal = 4.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(emoji)
                        }
                    }
                }

                // Reply Button
                TextButton(onClick = { onReply(message) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Reply, contentDescription = "Reply")
                    Spacer(Modifier.width(8.dp))
                    Text("Reply to Message")
                }

                // Delete Button
                if (isAuthor) {
                    TextButton(onClick = { onDelete(message) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Message (Yours)")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}


fun Date.toFormattedTimeString(): String {
    val simpleDateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    return simpleDateFormat.format(this)
}