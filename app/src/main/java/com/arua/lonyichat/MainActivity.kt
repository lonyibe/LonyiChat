@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class) // ✨ FIX: ADDED ExperimentalMaterial3Api to file level

package com.arua.lonyichat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
// 🔥 REMOVED: androidx.compose.material3.pulltorefresh imports
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties // ✨ ADDED: Import for DialogProperties
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.arua.lonyichat.data.*
import com.arua.lonyichat.ui.theme.LonyiChatTheme
import com.arua.lonyichat.ui.viewmodel.*
import com.google.accompanist.swiperefresh.SwipeRefresh // ✨ NEW: Accompanist import
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState // ✨ NEW: Accompanist import
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ✨ NEW: Constant for minimum duration of the pull-to-refresh animation
private const val MIN_REFRESH_DURATION = 500L

private const val TAG = "MainActivity"

// ---------------------------------------------------------------------------------
// 側 PROFILE STATE MANAGEMENT 側
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

// REMOVED @OptIn(ExperimentalMaterial3Api::class) - moved to file level
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

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(ApiService.getCurrentUserId()) {
        if (ApiService.getCurrentUserId() == null) {
            val intent = Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.navigationBars),
        topBar = {
            LonyiChatTopBar(
                title = if (selectedItem is Screen.Profile) "Profile" else selectedItem.title,
                showBackButton = showBackButton,
                onBackClicked = onBackClicked,
                onProfileClicked = { selectedItem = Screen.Profile },
                scrollBehavior = scrollBehavior
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
        },
        floatingActionButton = {
            if (selectedItem is Screen.Home) { // Changed FAB to Home screen for general content creation
                val createPostLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        // Refresh home feed after successful upload
                        homeFeedViewModel.fetchPosts()
                    }
                }
                FloatingActionButton(
                    onClick = {
                        val intent = Intent(context, CreatePostActivity::class.java)
                        createPostLauncher.launch(intent)
                    }
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Create Post")
                }
            } else if (selectedItem is Screen.Media) {
                val createMediaLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        // Refresh media feed after successful upload
                        mediaViewModel.fetchMedia()
                    }
                }
                FloatingActionButton(
                    onClick = {
                        val intent = Intent(context, CreateMediaActivity::class.java)
                        createMediaLauncher.launch(intent)
                    }
                ) {
                    Icon(Icons.Default.VideoCall, contentDescription = "Upload Media")
                }
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
                profileViewModel = profileViewModel,
                scrollBehavior = scrollBehavior
            )
        }
    }
}
// End LonyiChatApp
@Composable // REMOVED @OptIn(ExperimentalMaterial3Api::class) - moved to file level
fun LonyiChatTopBar(
    title: String,
    showBackButton: Boolean,
    onBackClicked: () -> Unit,
    onProfileClicked: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = onBackClicked) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
            containerColor = MaterialTheme.colorScheme.surface
        ),
        scrollBehavior = scrollBehavior
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
    profileViewModel: ProfileViewModel,
    scrollBehavior: TopAppBarScrollBehavior
) {
    when (screen) {
        Screen.Home -> HomeFeedScreen(profileState, homeFeedViewModel, scrollBehavior)
        Screen.Groups -> GroupsChurchScreen(churchesViewModel)
        Screen.Bible -> BibleStudyScreen(bibleViewModel)
        Screen.Chat -> ChatScreen(chatListViewModel)
        Screen.Media -> MediaScreen(mediaViewModel)
        Screen.Profile -> ProfileScreen(profileViewModel) {
            homeFeedViewModel.fetchPosts()
        }
    }
}

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

    // ✨ ADDED: Consistent, modern OutlinedTextField colors
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary, // LonyiOrange for focus
        unfocusedBorderColor = Color.White.copy(alpha = 0.5f), // Subtle white border
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        cursorColor = MaterialTheme.colorScheme.primary,
        // Ensure text is readable against the container background
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    )


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
                    singleLine = true,
                    colors = textFieldColors // ✨ APPLIED: Custom colors
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true,
                    colors = textFieldColors // ✨ APPLIED: Custom colors
                )
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it.filter { it.isDigit() } },
                    label = { Text("Age") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true,
                    colors = textFieldColors // ✨ APPLIED: Custom colors
                )
                OutlinedTextField(
                    value = country,
                    onValueChange = { country = it },
                    label = { Text("Country") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true,
                    colors = textFieldColors // ✨ APPLIED: Custom colors
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

// REMOVED @OptIn(ExperimentalMaterial3Api::class) - moved to file level
@Composable
fun HomeFeedScreen(
    profileState: UserProfileState,
    viewModel: HomeFeedViewModel,
    scrollBehavior: TopAppBarScrollBehavior
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope() // ✨ FIX: Added coroutineScope for nested launch/delay calls

    val createPostLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.fetchPosts()
        }
    }

    // NEW: Reactor List Dialog logic
    val reactorUiState by viewModel.reactorUiState.collectAsState()

    // Show Reactor List Dialog if data is loading or loaded
    if (reactorUiState.reactors.amen.isNotEmpty() || reactorUiState.reactors.hallelujah.isNotEmpty() || reactorUiState.reactors.praiseGod.isNotEmpty() || reactorUiState.isLoading) {
        ReactorListDialog(
            uiState = reactorUiState,
            onDismiss = { viewModel.clearReactorState() } // Clear state on dismiss
        )
    }

    if (reactorUiState.error != null) {
        // Show Toast for error in loading reactors (if a previous one exists, the one below it will clear it)
        Toast.makeText(LocalContext.current, "Error loading reactors: ${reactorUiState.error}", Toast.LENGTH_LONG).show()
        viewModel.clearReactorState()
    }


    val lazyListState = rememberLazyListState()

    LaunchedEffect(uiState.posts.firstOrNull()?.id) {
        if (uiState.posts.isNotEmpty()) {
            coroutineScope.launch {
                lazyListState.animateScrollToItem(0)
            }
        }
    }

    // ✨ PULL-TO-REFRESH IMPLEMENTATION START (HomeFeedScreen) - USING ACCOMPANIST ✨
    // 1. Remember Accompanist state
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = uiState.isLoading)

    // 2. Define refresh action
    val onRefresh = { // ✨ FIX: Removed implicit return type, allowing lambda to return Unit.
        coroutineScope.launch {
            val startTime = System.currentTimeMillis()
            viewModel.fetchPosts()
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            if (duration < MIN_REFRESH_DURATION) {
                delay(MIN_REFRESH_DURATION - duration)
            }
        }
        Unit // ✨ CRITICAL FIX: Explicitly return Unit to resolve Argument Type Mismatch
    }
    // ✨ PULL-TO-REFRESH IMPLEMENTATION END ✨

    when {
        // Only show full-screen loading if list is empty and initial load is in progress
        uiState.isLoading && uiState.posts.isEmpty() -> {
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
            // 3. Wrap content in SwipeRefresh
            SwipeRefresh(
                state = swipeRefreshState,
                onRefresh = onRefresh,
                // The LazyColumn already handles scrolling and nested scroll connection
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier // Removed .nestedScroll(scrollBehavior.nestedScrollConnection) here, keeping it only on Scaffold
                        .fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    item {
                        PostCreationBar(
                            profileState = profileState,
                            onTextClicked = {
                                val intent = Intent(context, CreatePostActivity::class.java)
                                createPostLauncher.launch(intent)
                            },
                            onPhotoClicked = {
                                val intent = Intent(context, CreatePostActivity::class.java)
                                createPostLauncher.launch(intent)
                            }
                        )
                        Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)
                    }

                    items(uiState.posts, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            viewModel = viewModel,
                            onCommentClicked = {
                                val intent = Intent(context, CommentsActivity::class.java)
                                intent.putExtra("POST_ID", post.id)
                                context.startActivity(intent)
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
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

    // ✨ NEW: State for the Reaction Selector Pop-up
    var showReactionSelector by remember { mutableStateOf(false) }

    // ✨ NEW: State to hold the URL of the image to be previewed
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }


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

    // ✨ NEW: Full Screen Image Viewer Dialog
    fullScreenImageUrl?.let { imageUrl ->
        FullScreenImageDialog(
            imageUrl = imageUrl,
            onDismiss = { fullScreenImageUrl = null }
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
                        // ✨ MODIFIED: Add clickable modifier to open the full screen dialog
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { fullScreenImageUrl = post.imageUrl }, // ✨ FIX: Set state to show dialog
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // ✨ NEW: Reaction Summary Bar
            PostReactionSummary(
                post = post,
                onSummaryClicked = {
                    viewModel.fetchReactors(post.id)
                }
            )

            // ✨ NEW: Interaction Bar (Like, Comment, Share)
            Box(modifier = Modifier.fillMaxWidth()) {
                PostInteractionBar(
                    post = post,
                    viewModel = viewModel,
                    onCommentClicked = onCommentClicked,
                    onLikeClicked = {
                        // Single click acts as a default 'amen' (Like) toggle
                        viewModel.reactToPost(post.id, "amen")
                    },
                    onLikeLongPressed = {
                        // Long click shows the reaction selector
                        showReactionSelector = true
                    }
                )

                // ✨ Reaction Selector Menu (Positioned just above the reaction button)
                if (showReactionSelector) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = 16.dp, y = (-56).dp) // Adjust position to float above the button
                    ) {
                        ReactionSelectorMenu(
                            onDismiss = { showReactionSelector = false },
                            onReactionSelected = { reactionType ->
                                viewModel.reactToPost(post.id, reactionType)
                                showReactionSelector = false
                            }
                        )
                    }
                }
            }
        }
    }
}

// ✨ NEW: Composable for the Full-Screen Image Dialog ✨
@Composable
fun FullScreenImageDialog(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    // Use Dialog with usePlatformDefaultWidth = false and fillMaxSize() to create a full-screen effect.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // Draw behind status and navigation bars for a true full-screen experience
            decorFitsSystemWindows = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black), // Black background for image viewing
            contentAlignment = Alignment.Center
        ) {
            // AsyncImage loads the image URL passed from the post
            AsyncImage(
                model = imageUrl,
                contentDescription = "Full-screen post image",
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clickable(onClick = onDismiss), // Dismiss on click
                contentScale = ContentScale.Fit // Fit the image within the screen bounds
            )

            // Close button in the top right corner
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun InteractionButton(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = text, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        // FIX: Ensure the text is on a single line and handles overflow
        Text(
            text,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ✨ NEW: PostReactionSummary (Display total count and trigger dialog) ✨
@Composable
fun PostReactionSummary(post: com.arua.lonyichat.data.Post, onSummaryClicked: () -> Unit) {
    val totalReactions = post.reactions.amen + post.reactions.hallelujah + post.reactions.praiseGod

    // Divider is always shown, but the summary is only shown if there are reactions
    Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)

    if (totalReactions > 0) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSummaryClicked)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Reaction icons placeholder (Facebook-like)
                if (post.reactions.amen > 0) {
                    Text("🙏", fontSize = 16.sp)
                    Spacer(Modifier.width(4.dp))
                }
                if (post.reactions.hallelujah > 0) {
                    Text("🥳", fontSize = 16.sp)
                    Spacer(Modifier.width(4.dp))
                }
                if (post.reactions.praiseGod > 0) {
                    Text("🙌", fontSize = 16.sp)
                    Spacer(Modifier.width(4.dp))
                }

                Text(
                    text = totalReactions.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // Show comment and share counts on the right
            Text(
                text = "${post.commentCount} Comments · ${post.shareCount} Shares",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray
            )
        }
        Divider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)
    }
}


// ✨ NEW: PostInteractionBar (Handles Like button with Long-Press) ✨
@Composable
fun PostInteractionBar(
    post: com.arua.lonyichat.data.Post,
    viewModel: HomeFeedViewModel,
    onCommentClicked: () -> Unit,
    onLikeClicked: () -> Unit,
    onLikeLongPressed: () -> Unit
) {
    val currentReaction = when {
        post.userReactions.amen -> "amen"
        post.userReactions.hallelujah -> "hallelujah"
        post.userReactions.praiseGod -> "praiseGod"
        else -> null
    }

    // Determine the icon and text based on the user's current reaction
    val (icon, text) = when (currentReaction) {
        "amen" -> Pair(Icons.Default.ThumbUp, "Amen")
        "hallelujah" -> Pair(Icons.Default.Star, "Hallelujah")
        "praiseGod" -> Pair(Icons.Default.Favorite, "Praise God")
        else -> Pair(Icons.Default.ThumbUp, "Like")
    }

    val contentColor by animateColorAsState(
        targetValue = if (currentReaction != null) MaterialTheme.colorScheme.primary else Color.Gray,
        label = "reactionColor"
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- Reaction Button (Long Press for Selector) ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    onClick = onLikeClicked, // Single click for default reaction toggle (Amen)
                    onLongClick = onLikeLongPressed // Long click for reaction selection
                )
                .padding(vertical = 8.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = text, tint = contentColor)
            Spacer(Modifier.width(8.dp))
            Text(text, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        // --- Comment Button ---
        InteractionButton(
            icon = Icons.Default.Comment,
            text = "Comment",
            onClick = onCommentClicked,
            modifier = Modifier.weight(1f)
        )

        // --- Share Button ---
        InteractionButton(
            icon = Icons.Default.Share,
            text = "Share",
            onClick = { viewModel.sharePost(post.id) },
            modifier = Modifier.weight(1f)
        )
    }
}

// ✨ NEW: ReactionSelectorMenu (Simple menu for reaction selection) ✨
@Composable
fun ReactionSelectorMenu(
    onDismiss: () -> Unit,
    onReactionSelected: (reactionType: String) -> Unit
) {
    // A Card to visually represent a floating reaction selector bar
    Card(
        elevation = CardDefaults.cardElevation(8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Amen
            TextButton(onClick = { onReactionSelected("amen"); onDismiss() }) { Text("🙏 Amen") }
            // Hallelujah
            TextButton(onClick = { onReactionSelected("hallelujah"); onDismiss() }) { Text("🥳 Hallelujah") }
            // Praise God
            TextButton(onClick = { onReactionSelected("praiseGod"); onDismiss() }) { Text("🙌 Praise God") }
        }
    }
}

// ✨ NEW: ReactorListDialog (Displays list of users who reacted) ✨
@Composable
fun ReactorListDialog(
    uiState: ReactorUiState,
    onDismiss: () -> Unit
) {
    // Determine the content to display based on the current loading/data state
    val content: @Composable () -> Unit = {
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Text("Error loading reactions: ${uiState.error}", color = MaterialTheme.colorScheme.error)
        } else {
            val amenReactors = uiState.reactors.amen
            val hallelujahReactors = uiState.reactors.hallelujah
            val praiseGodReactors = uiState.reactors.praiseGod

            // Get all unique reactors for the "All" tab
            val allReactors = (amenReactors + hallelujahReactors + praiseGodReactors)
                .distinctBy { it.userId }
                .toMutableList()

            Column(modifier = Modifier.fillMaxWidth()) {
                // Tab Row for Reaction Types (All, Amen, Hallelujah, Praise God)
                var selectedTab by remember { mutableStateOf(0) }
                val tabs = listOf(
                    Triple("All", allReactors.size, null),
                    Triple("Amen", amenReactors.size, "🙏"),
                    Triple("Hallelujah", hallelujahReactors.size, "🥳"),
                    Triple("Praise God", praiseGodReactors.size, "🙌")
                )

                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabs.forEachIndexed { index, tabData ->
                        val (title, count, emoji) = tabData
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text("$title ($count)", style = MaterialTheme.typography.labelLarge) },
                            icon = if (emoji != null) {
                                { Text(emoji, fontSize = 20.sp) }
                            } else null
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                val currentList = when (selectedTab) {
                    1 -> amenReactors
                    2 -> hallelujahReactors
                    3 -> praiseGodReactors
                    else -> allReactors
                }

                if (currentList.isEmpty()) {
                    Text("No one has left this reaction yet.", modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 300.dp)
                    ) {
                        items(currentList, key = { it.userId }) { reactor ->
                            ReactorItem(reactor = reactor)
                        }
                    }
                }
            }
        }
    }


    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Post Reactions") },
        text = content,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        textContentColor = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
fun ReactorItem(reactor: Reactor) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(reactor.photoUrl)
                .crossfade(true)
                .placeholder(R.drawable.ic_person_placeholder)
                .error(R.drawable.ic_person_placeholder)
                .build(),
            contentDescription = "Reactor Profile Photo",
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(reactor.name, fontWeight = FontWeight.Medium)
    }
}


@Composable
fun EditPostDialog(
    postContent: String,
    onDismiss: () -> Unit,
    onPost: (String) -> Unit
) {
    var updatedContent by remember { mutableStateOf(postContent) }
    val isPostButtonEnabled = updatedContent.isNotBlank()

    // ✨ ADDED: Consistent, modern OutlinedTextField colors
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        disabledBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Post",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            OutlinedTextField( // ✨ MODIFIED: Using OutlinedTextField for a visible border
                value = updatedContent,
                onValueChange = { updatedContent = it },
                label = { Text("Update your thought...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp),
                colors = textFieldColors // ✨ APPLIED: Custom colors
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

@Composable
fun GroupsChurchScreen(viewModel: ChurchesViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope() // ✨ FIX: Added coroutineScope for nested launch/delay calls

    // ✨ PULL-TO-REFRESH IMPLEMENTATION START (GroupsChurchScreen) - USING ACCOMPANIST ✨
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = uiState.isLoading)

    // 2. Define refresh action
    val onRefresh = { // ✨ FIX: Removed implicit return type, allowing lambda to return Unit.
        coroutineScope.launch {
            val startTime = System.currentTimeMillis()
            viewModel.fetchChurches()
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            if (duration < MIN_REFRESH_DURATION) {
                delay(MIN_REFRESH_DURATION - duration)
            }
        }
        Unit // ✨ CRITICAL FIX: Explicitly return Unit to resolve Argument Type Mismatch
    }
    // ✨ PULL-TO-REFRESH IMPLEMENTATION END ✨

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
            uiState.isLoading && uiState.churches.isEmpty() -> {
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
                // 3. Wrap content in SwipeRefresh
                SwipeRefresh(
                    state = swipeRefreshState,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
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
    val coroutineScope = rememberCoroutineScope() // ✨ FIX: Added coroutineScope for nested launch/delay calls

    // ✨ PULL-TO-REFRESH IMPLEMENTATION START (ChatScreen) - USING ACCOMPANIST ✨
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = uiState.isLoading)

    // 2. Define refresh action
    val onRefresh = { // ✨ FIX: Removed implicit return type, allowing lambda to return Unit.
        coroutineScope.launch {
            val startTime = System.currentTimeMillis()
            viewModel.fetchConversations()
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            if (duration < MIN_REFRESH_DURATION) {
                delay(MIN_REFRESH_DURATION - duration)
            }
        }
        Unit // ✨ CRITICAL FIX: Explicitly return Unit to resolve Argument Type Mismatch
    }
    // ✨ PULL-TO-REFRESH IMPLEMENTATION END ✨

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
            uiState.isLoading && uiState.conversations.isEmpty() -> {
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
                // 3. Wrap content in SwipeRefresh
                SwipeRefresh(
                    state = swipeRefreshState,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
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
    val coroutineScope = rememberCoroutineScope() // ✨ FIX: Added coroutineScope for nested launch/delay calls

    // ✨ PULL-TO-REFRESH IMPLEMENTATION START (MediaScreen) - USING ACCOMPANIST ✨
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = uiState.isLoading)

    // 2. Define refresh action
    val onRefresh = { // ✨ FIX: Removed implicit return type, allowing lambda to return Unit.
        coroutineScope.launch {
            val startTime = System.currentTimeMillis()
            viewModel.fetchMedia()
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            if (duration < MIN_REFRESH_DURATION) {
                delay(MIN_REFRESH_DURATION - duration)
            }
        }
        Unit // ✨ CRITICAL FIX: Explicitly return Unit to resolve Argument Type Mismatch
    }
    // ✨ PULL-TO-REFRESH IMPLEMENTATION END ✨

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
            uiState.isLoading && uiState.mediaItems.isEmpty() -> {
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
                // 3. Wrap content in SwipeRefresh
                SwipeRefresh(
                    state = swipeRefreshState,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(uiState.mediaItems) { mediaItem ->
                            MediaItemCard(mediaItem = mediaItem)
                        }
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