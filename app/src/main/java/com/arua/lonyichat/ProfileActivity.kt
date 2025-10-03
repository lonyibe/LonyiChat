package com.arua.lonyichat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.data.Post
import com.arua.lonyichat.data.Profile
import com.arua.lonyichat.ui.theme.LonyiChatTheme
import com.arua.lonyichat.ui.viewmodel.HomeFeedViewModel
import com.arua.lonyichat.ui.viewmodel.ProfileViewModel

class ProfileActivity : ComponentActivity() {

    private val profileViewModel: ProfileViewModel by viewModels()
    // We need HomeFeedViewModel for PostCard interactions
    private val homeFeedViewModel: HomeFeedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val userId = intent.getStringExtra("USER_ID")

        if (userId == null) {
            Toast.makeText(this, "User ID not found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        profileViewModel.fetchProfile(userId)

        setContent {
            LonyiChatTheme {
                val uiState by profileViewModel.uiState.collectAsState()

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                        uiState.profile != null -> {
                            UserProfileScreen(
                                profile = uiState.profile!!,
                                posts = uiState.posts,
                                homeFeedViewModel = homeFeedViewModel,
                                onNavigateUp = { finish() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun UserProfileScreen(
    profile: Profile,
    posts: List<Post>,
    homeFeedViewModel: HomeFeedViewModel,
    onNavigateUp: () -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Posts", "Photos")
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile.name) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            item {
                ProfileHeader(profile = profile)
            }

            stickyHeader {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title) }
                        )
                    }
                }
            }

            when (selectedTabIndex) {
                0 -> { // Posts Tab
                    if (posts.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillParentMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text("No posts yet.")
                            }
                        }
                    } else {
                        items(posts, key = { it.id }) { post ->
                            PostCard(
                                post = post,
                                viewModel = homeFeedViewModel,
                                onCommentClicked = {
                                    val intent = Intent(context, CommentsActivity::class.java)
                                    intent.putExtra("POST_ID", post.id)
                                    context.startActivity(intent)
                                },
                                showReactionSelector = false,
                                onSelectorOpen = {},
                                onSelectorDismiss = {},
                                onProfileClicked = { authorId ->
                                    if(authorId != profile.userId) {
                                        val intent = Intent(context, ProfileActivity::class.java).apply {
                                            putExtra("USER_ID", authorId)
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            )
                        }
                    }
                }
                1 -> { // Photos Tab
                    val photoPosts = posts.filter { !it.imageUrl.isNullOrBlank() }
                    if (photoPosts.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillParentMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text("No photos yet.")
                            }
                        }
                    } else {
                        item {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.heightIn(max = 1000.dp), // Avoid nested scroll issues
                                contentPadding = PaddingValues(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(photoPosts) { post ->
                                    AsyncImage(
                                        model = post.imageUrl,
                                        contentDescription = "Post photo",
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clickable { /* TODO: Open full screen image */ },
                                        contentScale = ContentScale.Crop
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

@Composable
fun ProfileHeader(profile: Profile) {
    val currentUserId = ApiService.getCurrentUserId()
    val isCurrentUser = profile.userId == currentUserId

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(profile.photoUrl)
                .crossfade(true)
                .placeholder(R.drawable.ic_person_placeholder)
                .error(R.drawable.ic_person_placeholder)
                .build(),
            contentDescription = "Profile Photo",
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = profile.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = profile.email,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ProfileStat(count = profile.followingCount, label = "Following")
            ProfileStat(count = profile.followerCount, label = "Followers")
            ProfileStat(count = profile.churchCount, label = "Churches")
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (isCurrentUser) {
            // Show Edit Profile and Logout buttons for the current user
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { /* TODO: Implement edit profile dialog */ }) {
                    Text("Edit Profile")
                }
                Button(onClick = { /* TODO: Implement logout */ }) {
                    Text("Logout")
                }
            }
        } else {
            // Show Follow/Message buttons for other users
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { /* TODO: Implement follow logic */ }) {
                    Text("Follow")
                }
                OutlinedButton(onClick = { /* TODO: Implement message logic */ }) {
                    Text("Message")
                }
            }
        }
    }
}

@Composable
fun ProfileStat(count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray
        )
    }
}