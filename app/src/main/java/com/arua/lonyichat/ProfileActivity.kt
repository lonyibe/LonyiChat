package com.arua.lonyichat

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.AddAPhoto
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
                                profileViewModel = profileViewModel,
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
    profileViewModel: ProfileViewModel,
    onNavigateUp: () -> Unit
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Posts", "Photos")
    val context = LocalContext.current
    val activity = context as Activity

    var showEditDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                profileViewModel.updateProfilePhoto(uri, activity, onSuccess = {
                    Toast.makeText(context, "Profile photo updated!", Toast.LENGTH_SHORT).show()
                })
                Toast.makeText(context, "Uploading photo...", Toast.LENGTH_SHORT).show()
            }
        }
    )

    if (showEditDialog) {
        EditProfileDialog(
            profile = profile,
            onDismiss = { showEditDialog = false },
            onSave = { name, phone, age, country ->
                profileViewModel.updateProfile(name, phone, age, country, onSuccess = {
                    profileViewModel.fetchProfile(profile.userId) // Refresh profile after edit
                })
                showEditDialog = false
            }
        )
    }

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
                ProfileHeader(
                    profile = profile,
                    onEditProfile = { showEditDialog = true },
                    onLogout = {
                        ApiService.logout()
                        val intent = Intent(context, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        context.startActivity(intent)
                        activity.finishAffinity()
                    },
                    onProfilePicClick = {
                        if (profile.userId == ApiService.getCurrentUserId()) {
                            imagePickerLauncher.launch("image/*")
                        }
                    }
                )
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
                                modifier = Modifier.heightIn(max = 1000.dp),
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
fun ProfileHeader(
    profile: Profile,
    onEditProfile: () -> Unit,
    onLogout: () -> Unit,
    onProfilePicClick: () -> Unit
) {
    val currentUserId = ApiService.getCurrentUserId()
    val isCurrentUser = profile.userId == currentUserId

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.clickable(enabled = isCurrentUser, onClick = onProfilePicClick)
        ) {
            Crossfade(targetState = profile.photoUrl, animationSpec = tween(durationMillis = 500)) { imageUrl ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
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
            }
            if (isCurrentUser) {
                Icon(
                    imageVector = Icons.Default.AddAPhoto,
                    contentDescription = "Change Photo",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(4.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEditProfile) {
                    Text("Edit Profile")
                }
                Button(onClick = onLogout) {
                    Text("Logout")
                }
            }
        } else {
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

@Composable
fun EditProfileDialog(
    profile: Profile?,
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, age: String, country: String) -> Unit
) {
    if (profile == null) {
        onDismiss()
        return
    }

    var name by remember { mutableStateOf(profile.name) }
    var phone by remember { mutableStateOf(profile.phone ?: "") }
    var age by remember { mutableStateOf(profile.age?.toString() ?: "") }
    var country by remember { mutableStateOf(profile.country ?: "") }

    val isSaveEnabled = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Your Profile") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it.filter { it.isDigit() } },
                    label = { Text("Age") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = country,
                    onValueChange = { country = it },
                    label = { Text("Country") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, phone, age, country) },
                enabled = isSaveEnabled
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}