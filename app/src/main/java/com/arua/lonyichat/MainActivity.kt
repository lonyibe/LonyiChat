package com.arua.lonyichat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import com.arua.lonyichat.data.Church
import com.arua.lonyichat.data.MediaItem
import com.arua.lonyichat.ui.theme.LonyiChatTheme
import com.arua.lonyichat.ui.viewmodel.*
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "MainActivity"

// ---------------------------------------------------------------------------------
// 👤 PROFILE STATE MANAGEMENT 👤
// ---------------------------------------------------------------------------------

data class UserProfileState(
    val userName: String = "Loading...",
    val isLoading: Boolean = true
)

@Composable
fun rememberProfileState(): UserProfileState {
    val firestore = Firebase.firestore
    val auth = Firebase.auth
    val currentState = remember { mutableStateOf(UserProfileState()) }

    LaunchedEffect(Unit) {
        val userId = auth.currentUser?.uid ?: run {
            Log.e(TAG, "User not authenticated. Cannot fetch profile.")
            currentState.value = currentState.value.copy(userName = "Guest", isLoading = false)
            return@LaunchedEffect
        }

        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                val name = document.getString("name") ?: "Christian"
                currentState.value = currentState.value.copy(userName = name, isLoading = false)
                Log.d(TAG, "Profile fetched successfully for $name")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching profile: $e")
                currentState.value = currentState.value.copy(userName = "Error", isLoading = false)
            }
    }

    return currentState.value
}

// Define the Screens (Tabs)
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "Feed", Icons.Filled.Home)
    data object Groups : Screen("groups", "Churches", Icons.Filled.Group)
    data object Bible : Screen("bible", "Bible", Icons.Filled.Book)
    data object Chat : Screen("chat", "Chat", Icons.Filled.Message)
    data object Media : Screen("media", "Media", Icons.Filled.LiveTv)
    data object Profile : Screen("profile", "Profile", Icons.Filled.Person)
}

class MainActivity : ComponentActivity() {
    private val homeFeedViewModel: HomeFeedViewModel by viewModels()
    private val churchesViewModel: ChurchesViewModel by viewModels()
    private val chatListViewModel: ChatListViewModel by viewModels()
    private val bibleViewModel: BibleViewModel by viewModels()
    private val mediaViewModel: MediaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LonyiChatTheme {
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as Activity).window
                        val insetsController = WindowInsetsControllerCompat(window, view)
                        insetsController.isAppearanceLightStatusBars = false
                        insetsController.isAppearanceLightNavigationBars = false
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LonyiChatApp(
                        homeFeedViewModel,
                        churchesViewModel,
                        chatListViewModel,
                        bibleViewModel,
                        mediaViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun LonyiChatApp(
    homeFeedViewModel: HomeFeedViewModel,
    churchesViewModel: ChurchesViewModel,
    chatListViewModel: ChatListViewModel,
    bibleViewModel: BibleViewModel,
    mediaViewModel: MediaViewModel
) {
    val profileState = rememberProfileState()
    var selectedItem: Screen by remember { mutableStateOf(Screen.Home) }

    val bottomBarItems = listOf(Screen.Home, Screen.Groups, Screen.Bible, Screen.Chat, Screen.Media)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
        topBar = {
            LonyiChatTopBar(
                title = if (selectedItem is Screen.Profile) "Profile" else selectedItem.title,
                onProfileClicked = { selectedItem = Screen.Profile }
            )
        },
        bottomBar = {
            if (selectedItem !is Screen.Profile) {
                LonyiChatBottomBar(
                    items = bottomBarItems,
                    selectedItem = selectedItem,
                    onItemSelected = { selectedItem = it }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            ScreenContent(
                screen = selectedItem,
                profileState = profileState,
                homeFeedViewModel = homeFeedViewModel,
                churchesViewModel = churchesViewModel,
                chatListViewModel = chatListViewModel,
                bibleViewModel = bibleViewModel,
                mediaViewModel = mediaViewModel
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LonyiChatTopBar(title: String, onProfileClicked: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        actions = {
            IconButton(onClick = onProfileClicked) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Profile",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
fun LonyiChatBottomBar(
    items: List<Screen>,
    selectedItem: Screen,
    onItemSelected: (Screen) -> Unit
) {
    NavigationBar(
        modifier = Modifier.navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        items.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.title) },
                label = { Text(screen.title) },
                selected = selectedItem == screen,
                onClick = { onItemSelected(screen) }
            )
        }
    }
}

@Composable
fun ScreenContent(
    screen: Screen,
    profileState: UserProfileState,
    homeFeedViewModel: HomeFeedViewModel,
    churchesViewModel: ChurchesViewModel,
    chatListViewModel: ChatListViewModel,
    bibleViewModel: BibleViewModel,
    mediaViewModel: MediaViewModel
) {
    when (screen) {
        Screen.Home -> HomeFeedScreen(profileState, homeFeedViewModel)
        Screen.Groups -> GroupsChurchScreen(churchesViewModel)
        Screen.Bible -> BibleStudyScreen(bibleViewModel)
        Screen.Chat -> ChatScreen(chatListViewModel)
        Screen.Media -> MediaScreen(mediaViewModel)
        Screen.Profile -> ProfileScreen(profileState)
    }
}

@Composable
fun ProfileScreen(profileState: UserProfileState) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = "Profile",
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (profileState.isLoading) {
            Text("Loading profile...")
        } else {
            Text(
                text = profileState.userName,
                style = MaterialTheme.typography.headlineMedium
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { /* TODO: Handle Edit Profile logic */ }) {
            Text("Edit Profile")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            Firebase.auth.signOut()
            val intent = Intent(context, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            context.startActivity(intent)
        }) {
            Text("Logout")
        }
    }
}

// ---------------------------------------------------------------------------------
// 🌟 HOME FEED IMPLEMENTATION 🌟
// ---------------------------------------------------------------------------------

@Composable
fun HomeFeedScreen(
    profileState: UserProfileState,
    viewModel: HomeFeedViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PostCreationBar(profileState.userName, profileState.isLoading)
        Divider(color = Color.Gray.copy(alpha = 0.5f), thickness = 1.dp)

        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp), contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Error: ${uiState.error}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(uiState.posts) { post ->
                        PostCard(post = post)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PostCreationBar(userName: String, isLoading: Boolean) {
    val prompt = when {
        isLoading -> "Loading user profile..."
        else -> "What is on your heart, $userName?"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Gray)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = prompt,
                color = if (isLoading) Color.LightGray else Color.Gray,
                modifier = Modifier.fillMaxWidth(0.9f)
            )
        }
        Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Go Live", color = MaterialTheme.colorScheme.primary)
            Text("Photo", color = MaterialTheme.colorScheme.primary)
            Text("Check In", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun PostCard(post: com.arua.lonyichat.data.Post) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Column {
                    Text(post.authorName, fontWeight = FontWeight.Bold)
                    Text(
                        post.createdAt.toDate().toFormattedString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(post.content, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = Color.Gray.copy(alpha = 0.3f))
        }
    }
}

// ---------------------------------------------------------------------------------
// ⛪️ CHURCHES / GROUPS SCREEN ⛪️
// ---------------------------------------------------------------------------------

@Composable
fun GroupsChurchScreen(viewModel: ChurchesViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Churches & Groups",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(uiState.churches) { church ->
                        ChurchCard(
                            church = church,
                            onFollowClicked = { viewModel.followChurch(church.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChurchCard(church: Church, onFollowClicked: () -> Unit) {
    val currentUserId = Firebase.auth.currentUser?.uid
    val isMember = church.members.contains(currentUserId)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(church.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(church.description, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                Text("${church.followerCount} Followers", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = onFollowClicked,
                enabled = !isMember,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMember) Color.Gray else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isMember) "Joined" else "Join")
            }
        }
    }
}

// ---------------------------------------------------------------------------------
// 💬 CHAT SCREEN 💬
// ---------------------------------------------------------------------------------

@Composable
fun ChatScreen(viewModel: ChatListViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val currentUserId = Firebase.auth.currentUser?.uid

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top=16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Find other Christians", style = MaterialTheme.typography.titleMedium)

                Row(
                    modifier = Modifier.clickable { Log.d(TAG, "Find Christians clicked") },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Find Friends", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Search", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Messages",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Divider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))

        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.conversations) { chat ->
                        val otherParticipantId = chat.participants.firstOrNull { it != currentUserId }
                        val chatName = chat.participantNames[otherParticipantId] ?: "Unknown User"

                        ChatThreadItem(
                            chatName = chatName,
                            lastMessage = chat.lastMessage,
                            timestamp = chat.lastMessageTimestamp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatThreadItem(chatName: String, lastMessage: String, timestamp: Date?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: Navigate to conversation screen */ }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(chatName.firstOrNull()?.toString() ?: " ", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(chatName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                maxLines = 1
            )
        }
        Text(timestamp?.toFormattedString() ?: "", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
    Divider(color = Color.Gray.copy(alpha = 0.3f))
}

// ---------------------------------------------------------------------------------
// 📚 BIBLE SCREEN 📚
// ---------------------------------------------------------------------------------

@Composable
fun BibleStudyScreen(viewModel: BibleViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    Text(
                        text = "Error: ${uiState.error}",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                uiState.verseOfTheDay != null -> {
                    val verse = uiState.verseOfTheDay!!
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Daily Bread: ${verse.reference}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "\"${verse.text}\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { Log.d(TAG, "Read full chapter clicked") }) {
                            Text("Read Full Chapter")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Your Reading Plan Progress",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Currently reading: Genesis (Day 3 of 90)", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = 0.03f,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { Log.d(TAG, "Bible Search clicked") },
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search", Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text("Search Bible")
            }
            Button(
                onClick = { Log.d(TAG, "Browse Books clicked") },
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            ) {
                Icon(Icons.Default.Timeline, contentDescription = "Books", Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text("Books A-Z")
            }
        }
    }
}

// ---------------------------------------------------------------------------------
// 🎬 MEDIA SCREEN 🎬
// ---------------------------------------------------------------------------------

@Composable
fun MediaScreen(viewModel: MediaViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Media & Testimonies",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(uiState.mediaItems) { mediaItem ->
                        MediaItemCard(mediaItem = mediaItem)
                    }
                }
            }
        }
    }
}

@Composable
fun MediaItemCard(mediaItem: MediaItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: Open video player or testimony text */ },
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (mediaItem.mediaType) {
                "video" -> Icons.Default.Videocam
                "livestream" -> Icons.Default.LiveTv
                "testimony" -> Icons.Default.Book
                else -> Icons.Default.PlayCircle
            }
            Icon(
                imageVector = icon,
                contentDescription = mediaItem.mediaType,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(mediaItem.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(mediaItem.description, style = MaterialTheme.typography.bodyMedium, color = Color.Gray, maxLines = 2)
            }
        }
    }
}

// ---------------------------------------------------------------------------------
// UTILITY FUNCTIONS
// ---------------------------------------------------------------------------------
fun Date.toFormattedString(): String {
    val simpleDateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    return simpleDateFormat.format(this)
}

@Preview(showBackground = true)
@Composable
fun LonyiChatPreview() {
    LonyiChatTheme {
        LonyiChatApp(
            HomeFeedViewModel(),
            ChurchesViewModel(),
            ChatListViewModel(),
            BibleViewModel(),
            MediaViewModel()
        )
    }
}