package com.arua.lonyichat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.net.Uri
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
import com.arua.lonyichat.data.Profile
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.ui.theme.LonyiChatTheme
import com.arua.lonyichat.ui.viewmodel.*
import java.text.SimpleDateFormat
import java.util.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import coil.request.ImageRequest
import android.widget.Toast
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.ContentScale


private const val TAG = "MainActivity"

// ---------------------------------------------------------------------------------
// 👤 PROFILE STATE MANAGEMENT 👤
// ---------------------------------------------------------------------------------

data class UserProfileState(
    val userName: String = "Loading...",
    val photoUrl: String? = null,
    val isLoading: Boolean = true
)

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
    private val profileViewModel: ProfileViewModel by viewModels()

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
                        mediaViewModel,
                        profileViewModel
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
    mediaViewModel: MediaViewModel,
    profileViewModel: ProfileViewModel
) {
    val profileUiState by profileViewModel.uiState.collectAsState()

    val profileState = UserProfileState(
        userName = profileUiState.profile?.name ?: "Loading...",
        photoUrl = profileUiState.profile?.photoUrl,
        isLoading = profileUiState.isLoading
    )

    var selectedItem: Screen by remember { mutableStateOf(Screen.Home) }

    val bottomBarItems = listOf(Screen.Home, Screen.Groups, Screen.Bible, Screen.Chat, Screen.Media)

    val showBackButton = selectedItem is Screen.Profile
    val onBackClicked = { selectedItem = Screen.Home }

    val context = LocalContext.current

    LaunchedEffect(ApiService.getCurrentUserId()) {
        if (ApiService.getCurrentUserId() == null) {
            val intent = Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
        topBar = {
            LonyiChatTopBar(
                title = if (selectedItem is Screen.Profile) "Profile" else selectedItem.title,
                showBackButton = showBackButton,
                onBackClicked = onBackClicked,
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
                mediaViewModel = mediaViewModel,
                profileViewModel = profileViewModel
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LonyiChatTopBar(
    title: String,
    showBackButton: Boolean,
    onBackClicked: () -> Unit,
    onProfileClicked: () -> Unit
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = onBackClicked) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        },
        actions = {
            if (!showBackButton) {
                IconButton(onClick = onProfileClicked) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Profile",
                        modifier = Modifier.size(28.dp)
                    )
                }
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
    mediaViewModel: MediaViewModel,
    profileViewModel: ProfileViewModel
) {
    when (screen) {
        Screen.Home -> HomeFeedScreen(profileState, homeFeedViewModel)
        Screen.Groups -> GroupsChurchScreen(churchesViewModel)
        Screen.Bible -> BibleStudyScreen(bibleViewModel)
        Screen.Chat -> ChatScreen(chatListViewModel)
        Screen.Media -> MediaScreen(mediaViewModel)
        Screen.Profile -> ProfileScreen(profileViewModel) {
            homeFeedViewModel.fetchPosts()
        }
    }
}

// ---------------------------------------------------------------------------------
// 👤 PROFILE SCREEN IMPLEMENTATION 👤
// ---------------------------------------------------------------------------------

@Composable
fun ProfileScreen(viewModel: ProfileViewModel, onProfileUpdated: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val uiState by viewModel.uiState.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                viewModel.updateProfilePhoto(uri, activity, onSuccess = onProfileUpdated)
                Toast.makeText(context, "Uploading photo...", Toast.LENGTH_SHORT).show()
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return
            }
            uiState.error != null -> {
                Text(
                    "Error: ${uiState.error}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(32.dp)
                )
                Button(onClick = { viewModel.fetchProfile() }) {
                    Text("Retry Load")
                }
                return
            }
            uiState.profile == null -> {
                Text("Profile data not found.", modifier = Modifier.padding(32.dp))
                return
            }
        }

        val profile = uiState.profile!!

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .clickable(enabled = !uiState.isSaving) {
                    imagePickerLauncher.launch("image/*")
                }
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(profile.photoUrl)
                    .crossfade(true)
                    // ✨ REMOVED: Caching is now enabled by default for speed
                    // .memoryCachePolicy(CachePolicy.DISABLED)
                    // .diskCachePolicy(CachePolicy.DISABLED)
                    .placeholder(R.drawable.ic_person_placeholder)
                    .error(R.drawable.ic_person_placeholder)
                    .build(),
                contentDescription = "Profile Photo",
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )

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

            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(100.dp),
                    strokeWidth = 4.dp,
                    color = MaterialTheme.colorScheme.secondary
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

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ProfileDetail(Icons.Default.Place, "Country", profile.country ?: "Not Set")
                ProfileDetail(Icons.Default.Cake, "Age", profile.age?.toString() ?: "Not Set")
                ProfileDetail(Icons.Default.Phone, "Phone", profile.phone ?: "Not Set")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { showEditDialog = true },
                enabled = !uiState.isSaving
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Saving...")
                } else {
                    Text("Edit Profile")
                }
            }

            Button(onClick = {
                ApiService.logout()
                val intent = Intent(context, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                context.startActivity(intent)
            }) {
                Text("Logout")
            }
        }
    }

    if (showEditDialog) {
        EditProfileDialog(
            profile = uiState.profile,
            onDismiss = { showEditDialog = false },
            onSave = { name, phone, age, country ->
                viewModel.updateProfile(name, phone, age, country, onSuccess = onProfileUpdated)
                showEditDialog = false
            }
        )
    }
}

// ... (The rest of your MainActivity.kt file remains unchanged)
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
fun ProfileDetail(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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

    val isSaveEnabled = name.isNotBlank() && phone.isNotBlank() && age.isNotBlank() && country.isNotBlank()

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
        },
        containerColor = MaterialTheme.colorScheme.background,
        textContentColor = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
fun HomeFeedScreen(
    profileState: UserProfileState,
    viewModel: HomeFeedViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current as Activity

    var showTextPostDialog by remember { mutableStateOf(false) }
    var showPhotoPostDialog by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    var showCommentDialogForPostId by remember { mutableStateOf<String?>(null) }


    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                selectedImageUri = uri
                showPhotoPostDialog = true
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PostCreationBar(
            profileState = profileState,
            onTextClicked = { showTextPostDialog = true },
            onPhotoClicked = { imagePickerLauncher.launch("image/*") }
        )
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
                        PostCard(
                            post = post,
                            viewModel = viewModel,
                            onCommentClicked = { showCommentDialogForPostId = post.id }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    if (showTextPostDialog) {
        PostCreationDialog(
            profileState = profileState,
            onDismiss = { showTextPostDialog = false },
            onPost = { content ->
                viewModel.createPost(content, "post")
                showTextPostDialog = false
            }
        )
    }

    if (showPhotoPostDialog && selectedImageUri != null) {
        PhotoPostCreationDialog(
            imageUri = selectedImageUri!!,
            isUploading = uiState.isUploading,
            onDismiss = { showPhotoPostDialog = false },
            onPost = { caption ->
                viewModel.createPhotoPost(caption, selectedImageUri!!, context)
                showPhotoPostDialog = false
            }
        )
    }

    if (showCommentDialogForPostId != null) {
        val postId = showCommentDialogForPostId!!
        CommentCreationDialog(
            onDismiss = { showCommentDialogForPostId = null },
            onComment = { content ->
                viewModel.addComment(postId, content)
                showCommentDialogForPostId = null
            }
        )
    }
}

@Composable
fun PostCreationBar(
    profileState: UserProfileState,
    onTextClicked: () -> Unit,
    onPhotoClicked: () -> Unit
) {
    val prompt = when {
        profileState.isLoading -> "Loading user profile..."
        else -> "What is on your heart, ${profileState.userName}?"
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
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(profileState.photoUrl)
                    .crossfade(true)
                    .placeholder(R.drawable.ic_person_placeholder)
                    .error(R.drawable.ic_person_placeholder)
                    .build(),
                contentDescription = "Your Profile Photo",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = prompt,
                color = if (profileState.isLoading) Color.LightGray else Color.Gray,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTextClicked() }
            )
        }
        Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PostActionButton(icon = Icons.Default.Edit, text = "Text", onClick = onTextClicked)
            PostActionButton(icon = Icons.Default.PhotoCamera, text = "Photo", onClick = onPhotoClicked)
        }
    }
}

@Composable
fun PostActionButton(icon: ImageVector, text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = text, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(4.dp))
        Text(text, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun PostCard(post: com.arua.lonyichat.data.Post, viewModel: HomeFeedViewModel, onCommentClicked: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val currentUserId = ApiService.getCurrentUserId()
    val isAuthor = post.authorId == currentUserId
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }


    if (isAuthor && showEditDialog) {
        EditPostDialog(
            postContent = post.content,
            onDismiss = { showEditDialog = false },
            onPost = { newContent ->
                viewModel.updatePost(post.id, newContent)
                showEditDialog = false
            }
        )
    }

    if (isAuthor && showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Post") },
            text = { Text("Are you sure you want to permanently delete this post?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePost(post.id)
                        showDeleteConfirmDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }


    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        )
    ) {
        Column {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(post.authorPhotoUrl)
                                .crossfade(true)
                                .placeholder(R.drawable.ic_person_placeholder)
                                .error(R.drawable.ic_person_placeholder)
                                .build(),
                            contentDescription = "Author's Profile Photo",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(post.authorName, fontWeight = FontWeight.Bold)
                            Text(
                                post.createdAt.toDate().toFormattedString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }
                    if (isAuthor) {
                        Box {
                            IconButton(onClick = { showMenu = !showMenu }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = {
                                        showEditDialog = true
                                        showMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        showDeleteConfirmDialog = true
                                        showMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(post.content, style = MaterialTheme.typography.bodyMedium)

                if (post.imageUrl != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    AsyncImage(
                        model = post.imageUrl,
                        contentDescription = "Post image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Divider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReactionButton(
                    text = "Amen 🙏",
                    count = post.reactions.amen,
                    onClick = { viewModel.reactToPost(post.id, "amen") }
                )
                ReactionButton(
                    text = "Hallelujah 🙌",
                    count = post.reactions.hallelujah,
                    onClick = { viewModel.reactToPost(post.id, "hallelujah") }
                )
                ReactionButton(
                    text = "Praise God 🎉",
                    count = post.reactions.praiseGod,
                    onClick = { viewModel.reactToPost(post.id, "praiseGod") }
                )
            }
            Divider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                InteractionButton(icon = Icons.Default.ThumbUp, text = "Like", onClick = { viewModel.reactToPost(post.id, "amen") })
                InteractionButton(icon = Icons.Default.Comment, text = "Comment", onClick = onCommentClicked)
                InteractionButton(icon = Icons.Default.Share, text = "Share", onClick = { viewModel.sharePost(post.id) })
            }
        }
    }
}

@Composable
fun ReactionButton(text: String, count: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text("$text ($count)")
    }
}

@Composable
fun InteractionButton(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 16.dp)
    ) {
        Icon(icon, contentDescription = text, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCreationDialog(
    profileState: UserProfileState,
    onDismiss: () -> Unit,
    onPost: (String) -> Unit
) {
    var postContent by remember { mutableStateOf("") }
    val isPostButtonEnabled = postContent.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Post",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(profileState.photoUrl)
                            .crossfade(true)
                            .placeholder(R.drawable.ic_person_placeholder)
                            .error(R.drawable.ic_person_placeholder)
                            .build(),
                        contentDescription = "Your Profile Photo",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = profileState.userName,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = postContent,
                    onValueChange = { postContent = it },
                    label = { Text("Share your thought...") },
                    placeholder = { Text("What is on your heart today?") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPost(postContent) },
                enabled = isPostButtonEnabled
            ) {
                Text("Post")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }

        },
        containerColor = MaterialTheme.colorScheme.background,
        textContentColor = MaterialTheme.colorScheme.onBackground
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPostDialog(
    postContent: String,
    onDismiss: () -> Unit,
    onPost: (String) -> Unit
) {
    var updatedContent by remember { mutableStateOf(postContent) }
    val isPostButtonEnabled = updatedContent.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Post",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            OutlinedTextField(
                value = updatedContent,
                onValueChange = { updatedContent = it },
                label = { Text("Update your thought...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { onPost(updatedContent) },
                enabled = isPostButtonEnabled
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        textContentColor = MaterialTheme.colorScheme.onBackground
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoPostCreationDialog(
    imageUri: Uri,
    isUploading: Boolean,
    onDismiss: () -> Unit,
    onPost: (String) -> Unit
) {
    var caption by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Photo Post") },
        text = {
            Column {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Selected image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Write a caption...") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPost(caption) },
                enabled = !isUploading
            ) {
                if (isUploading) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                } else {
                    Text("Post")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentCreationDialog(
    onDismiss: () -> Unit,
    onComment: (String) -> Unit
) {
    var commentContent by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a comment") },
        text = {
            OutlinedTextField(
                value = commentContent,
                onValueChange = { commentContent = it },
                label = { Text("Your comment...") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onComment(commentContent) },
                enabled = commentContent.isNotBlank()
            ) {
                Text("Post")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

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
    val currentUserId = ApiService.getCurrentUserId()
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

@Composable
fun ChatScreen(viewModel: ChatListViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val currentUserId = ApiService.getCurrentUserId()

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
            MediaViewModel(),
            ProfileViewModel()
        )
    }
}