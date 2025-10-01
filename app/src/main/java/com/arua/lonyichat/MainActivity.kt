package com.arua.lonyichat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.arua.lonyichat.ui.theme.LonyiChatTheme
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase


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


// Data class for a mock Post
data class Post(
    val id: Int,
    val author: String,
    val content: String,
    val time: String,
    val reactions: Int,
    val comments: Int,
    val trendingSong: String? = null
)

// Mock Data for the Feed
val mockPosts = listOf(
    Post(1, "Brother David", "Praise God! Just got confirmation on a prayer request. Faith works!", "5m ago", 124, 15),
    Post(2, "Sister Ruth", "Listening to a beautiful Christian worship song: 'Reckless Love'. So peaceful.", "2h ago", 89, 5, "Reckless Love - Cory Asbury"),
    Post(3, "Pastor Mike", "Daily Verse: 'For God so loved the world...' (John 3:16). Share your favorite verse!", "1 day ago", 301, 45),
    Post(4, "Church Group A", "Reminder: Bible study tonight at 7 PM. Topic: Forgiveness.", "2 days ago", 55, 10),
)

// Mock Data for Chat List
data class ChatThread(
    val id: Int,
    val recipient: String,
    val lastMessage: String,
    val time: String
)

val mockChats = listOf(
    ChatThread(1, "Sister Ruth", "Amen! I'm praying for you.", "1m ago"),
    ChatThread(2, "Pastor Mike", "See you tonight at 7.", "3h ago"),
    ChatThread(3, "Brother David", "That verse blessed me.", "1d ago"),
)


// 1. Define the Screens (Tabs)
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "Feed", Icons.Filled.Home)
    data object Groups : Screen("groups", "Churches", Icons.Filled.Group)
    data object Bible : Screen("bible", "Bible", Icons.Filled.Book)
    data object Chat : Screen("chat", "Chat", Icons.Filled.Message)
    data object Media : Screen("media", "Media", Icons.Filled.LiveTv)
    data object Profile : Screen("profile", "Profile", Icons.Filled.Person)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // ✨ THIS LINE ENABLES THE IMMERSIVE, EDGE-TO-EDGE EXPERIENCE ✨
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LonyiChatTheme {
                // ✨ ADDED: This block sets the status bar icons to light for better visibility ✨
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as Activity).window
                        // `false` makes the status bar icons light
                        WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = false
                    }
                }

                // ✨ THIS IS THE FIX 👇: A Surface that fills the entire screen ✨
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LonyiChatApp()
                }
            }
        }
    }
}

@Composable
fun LonyiChatApp() {
    val profileState = rememberProfileState()
    var selectedItem: Screen by remember { mutableStateOf(Screen.Home) }

    val bottomBarItems = listOf(Screen.Home, Screen.Groups, Screen.Bible, Screen.Chat, Screen.Media)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
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
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            ScreenContent(screen = selectedItem, profileState = profileState)
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
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
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
fun ScreenContent(screen: Screen, profileState: UserProfileState) {
    when (screen) {
        Screen.Home -> HomeFeedScreen(profileState)
        Screen.Groups -> GroupsChurchScreen()
        Screen.Bible -> BibleStudyScreen()
        Screen.Chat -> ChatScreen()
        Screen.Media -> MediaScreen()
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
fun HomeFeedScreen(profileState: UserProfileState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PostCreationBar(profileState.userName, profileState.isLoading)
        Divider(color = Color.Gray.copy(alpha = 0.5f), thickness = 1.dp)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp)
        ) {
            items(mockPosts) { post ->
                PostCard(initialPost = post)
                Spacer(modifier = Modifier.height(8.dp))
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
fun PostCard(initialPost: Post) {
    var reactions by remember { mutableIntStateOf(initialPost.reactions) }

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
                    Text(initialPost.author, fontWeight = FontWeight.Bold)
                    Text(initialPost.time, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            initialPost.trendingSong?.let { song ->
                Text("🎵 Trending Song: $song", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(6.dp))
            }

            Text(initialPost.content, style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = Color.Gray.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.clickable {
                        reactions++
                        Log.d(TAG, "Post ${initialPost.id} reacted to. New count: $reactions")
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🙏", Modifier.padding(end = 4.dp))
                    Text("$reactions Reactions", style = MaterialTheme.typography.labelMedium)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.clickable {
                            Log.d(TAG, "Comment button clicked for post ${initialPost.id}")
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Comment", Modifier.size(18.dp))
                        Text(initialPost.comments.toString(), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelMedium)
                    }

                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share",
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                Log.d(TAG, "Share button clicked for post ${initialPost.id}")
                            }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------
// 📚 OTHER SCREEN PLACEHOLDERS 📚
// ---------------------------------------------------------------------------------

@Composable
fun GroupsChurchScreen() {
    Text(
        text = "Churches: Create/Follow Churches, Advertise Programs/Events, Create Events.",
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
fun BibleStudyScreen() {
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
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Daily Bread: Romans 8:28",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "\"And we know that in all things God works for the good of those who love him, who have been called according to his purpose.\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { Log.d(TAG, "Read full chapter clicked") }) {
                    Text("Read Full Chapter")
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(MaterialTheme.shapes.small),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(onClick = { Log.d(TAG, "Continue plan clicked") }) {
                        Text("Continue Plan")
                    }
                    Button(onClick = { Log.d(TAG, "Change plan clicked") }) {
                        Text("Change Plan")
                    }
                }
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

@Composable
fun ChatScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp)
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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(mockChats) { chat ->
                ChatThreadItem(chat = chat)
            }
        }
    }
}

@Composable
fun ChatThreadItem(chat: ChatThread) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { Log.d(TAG, "Chat thread clicked: ${chat.recipient}") }
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
            Text(chat.recipient.first().toString(), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(chat.recipient, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                chat.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                maxLines = 1
            )
        }

        Text(chat.time, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
    Divider(color = Color.Gray.copy(alpha = 0.3f))
}

@Composable
fun MediaScreen() {
    Text(
        text = "Media: Videos, Livestream, Testimonies.",
        modifier = Modifier.padding(16.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun LonyiChatPreview() {
    LonyiChatTheme {
        LonyiChatApp()
    }
}