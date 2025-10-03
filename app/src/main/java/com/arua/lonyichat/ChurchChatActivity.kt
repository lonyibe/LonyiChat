package com.arua.lonyichat

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
// ✨ ADDED: Import for RoundedCornerShape, resolving compilation errors on lines 166 and 212
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
// ✨ ADDED: Import for painterResource, resolving compilation errors on lines 149 and 186
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.Church
import com.arua.lonyichat.data.ChurchMessage
import com.arua.lonyichat.ui.theme.LonyiChatTheme
import com.arua.lonyichat.ui.viewmodel.ChurchChatViewModel
import java.text.SimpleDateFormat
import java.util.*

class ChurchChatActivity : ComponentActivity() {

    private val viewModel: ChurchChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                    onNavigateUp = { finish() }
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
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(church.id) {
        viewModel.fetchMessages(church.id)
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(church.name) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            MessageInputBar(
                message = messageText,
                onMessageChange = { messageText = it },
                onSendClick = {
                    viewModel.sendMessage(church.id, messageText)
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