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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddAPhoto
// ✨ FIX: Adding all missing icon imports.
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
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
import androidx.compose.ui.unit.dp
// ✨ FIX: This import is crucial for resolving the lifecycleScope extension on Activity.
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val context = LocalContext.current as Activity
    val currentUserId = ApiService.getCurrentUserId()
    val isCreator = church.createdBy == currentUserId

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // State to hold a potentially updated church object (specifically for photoUrl)
    var currentChurch by remember { mutableStateOf(church) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            Toast.makeText(context, "Uploading church photo...", Toast.LENGTH_SHORT).show()
            // Wrapped suspend function call in lifecycleScope.launch { ... }
            context.lifecycleScope.launch {
                ApiService.uploadChurchPhoto(currentChurch.id, uri, context).onSuccess { newPhotoUrl ->
                    // Optimistically update the UI state with the new URL
                    currentChurch = currentChurch.copy(photoUrl = newPhotoUrl)
                    Toast.makeText(context, "Church photo updated!", Toast.LENGTH_SHORT).show()
                    // Signal main activity to refresh its Church list on resume
                    (context as Activity).setResult(Activity.RESULT_OK)
                }.onFailure { error ->
                    Toast.makeText(context, "Upload failed: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- DIALOGS ---
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
                    // Church Photo/Details Button
                    Box(modifier = Modifier.padding(end = 8.dp).clickable(onClick = {
                        // Simplified: Directly offer photo update if creator, otherwise ignore click
                        if (isCreator) {
                            showMenu = true
                        } else {
                            // If not creator, maybe navigate to a simple details screen
                            Toast.makeText(context, "Group details coming soon!", Toast.LENGTH_SHORT).show()
                        }
                    })) {
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
                        // Menu for Creator
                        if (isCreator) {
                            IconButton(onClick = { showMenu = !showMenu }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = MaterialTheme.colorScheme.onSurface)
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
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
                    viewModel.sendMessage(currentChurch.id, messageText)
                    messageText = ""
                }
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
                        items(uiState.messages) { message ->
                            ChatMessageItem(message)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChurchMessage) {
    val isCurrentUser = message.authorId == ApiService.getCurrentUserId()
    val horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
    val bubbleColor = if (isCurrentUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = Modifier.fillMaxWidth(),
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
            Box(
                modifier = Modifier
                    .background(bubbleColor, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(text = message.content)
            }
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
fun MessageInputBar(
    message: String,
    onMessageChange: (String) -> Unit,
    onSendClick: () -> Unit
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(8.dp),
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

fun Date.toFormattedTimeString(): String {
    val simpleDateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    return simpleDateFormat.format(this)
}