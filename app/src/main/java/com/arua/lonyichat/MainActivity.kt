@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.arua.lonyichat

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.arua.lonyichat.data.*
import com.arua.lonyichat.ui.theme.LonyiChatTheme
import com.arua.lonyichat.ui.viewmodel.*
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class AppReaction(val type: String, val emoji: String, val label: String)

val LonyiReactions = listOf(
    AppReaction("amen", "🙏", "Amen"),
    AppReaction("hallelujah", "🥳", "Hallelujah"),
    AppReaction("praiseGod", "🙌", "Praise God")
)

private const val MIN_REFRESH_DURATION = 500L
private const val TAG = "MainActivity"

data class UserProfileState(
    val userName: String = "Loading...",
    val photoUrl: String? = null,
    val isLoading: Boolean = true
)

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "Feed", Icons.Filled.Home)
    data object Groups : Screen("groups", "Churches", Icons.Filled.Group)
    data object Bible : Screen("bible", "Bible", Icons.Filled.Book)
    data object Chat : Screen("chat", "Chat", Icons.Filled.Message)
    data object Media : Screen("media", "Media", Icons.Filled.LiveTv)
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

    // ✨ THIS IS THE FIX: Refresh the current user's profile every time the MainActivity is resumed.
    // This ensures the profile picture in the "What's on your heart?" bar is always up-to-date.
    override fun onResume() {
        super.onResume()
        ApiService.getCurrentUserId()?.let {
            profileViewModel.fetchProfile(it)
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
        topBar = {
            LonyiChatTopBar(
                title = "LonyiChat",
                onProfileClicked = {
                    val intent = Intent(context, ProfileActivity::class.java).apply {
                        putExtra("USER_ID", ApiService.getCurrentUserId())
                    }
                    context.startActivity(intent)
                },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            LonyiChatBottomBar(
                items = bottomBarItems,
                selectedItem = selectedItem,
                onItemSelected = { selectedItem = it }
            )
        },
        floatingActionButton = {}
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

// No changes are needed below this line
@Composable
fun LonyiChatTopBar(
    title: String,
    onProfileClicked: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior
) {
    val isDarkTheme = isSystemInDarkTheme()
    val view = LocalView.current

    val headerColor = if (isDarkTheme) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = headerColor.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkTheme
        }
    }

    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall) },
        actions = {
            IconButton(onClick = onProfileClicked) {
                Icon(imageVector = Icons.Default.AccountCircle, contentDescription = "Profile", modifier = Modifier.size(28.dp))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = headerColor,
            scrolledContainerColor = headerColor,
            titleContentColor = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else Color.White,
            actionIconContentColor = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else Color.White,
            navigationIconContentColor = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else Color.White
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
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        items.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.title) },
                label = { Text(screen.title, style = MaterialTheme.typography.labelSmall) },
                selected = selectedItem == screen,
                onClick = { onItemSelected(screen) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
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
    }
}

@Composable
fun HomeFeedScreen(
    profileState: UserProfileState,
    viewModel: HomeFeedViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val createPostLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.fetchPosts()
        }
    }
    val reactorUiState by viewModel.reactorUiState.collectAsState()

    if (reactorUiState.isLoading || reactorUiState.reactors.amen.isNotEmpty() || reactorUiState.reactors.hallelujah.isNotEmpty() || reactorUiState.reactors.praiseGod.isNotEmpty()) {
        ReactorListDialog(
            uiState = reactorUiState,
            onDismiss = { viewModel.clearReactorState() }
        )
    }

    if (reactorUiState.error != null) {
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

    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = uiState.isLoading)
    val onRefresh = {
        coroutineScope.launch {
            val startTime = System.currentTimeMillis()
            viewModel.fetchPosts()
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            if (duration < MIN_REFRESH_DURATION) {
                delay(MIN_REFRESH_DURATION - duration)
            }
        }
        Unit
    }

    var openReactionPostId by remember { mutableStateOf<String?>(null) }

    when {
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
                Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
            }
        }
        else -> {
            SwipeRefresh(state = swipeRefreshState, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        PostCreationBar(
                            profileState = profileState,
                            onBarClick = {
                                val intent = Intent(context, CreatePostActivity::class.java)
                                createPostLauncher.launch(intent)
                            }
                        )
                    }

                    items(uiState.posts, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            viewModel = viewModel,
                            onCommentClicked = {
                                val intent = Intent(context, CommentsActivity::class.java)
                                intent.putExtra("POST_ID", post.id)
                                context.startActivity(intent)
                            },
                            showReactionSelector = openReactionPostId == post.id,
                            onSelectorOpen = { openReactionPostId = it },
                            onSelectorDismiss = { openReactionPostId = null },
                            onProfileClicked = { authorId ->
                                val intent = Intent(context, ProfileActivity::class.java).apply {
                                    putExtra("USER_ID", authorId)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PostCreationBar(
    profileState: UserProfileState,
    onBarClick: () -> Unit
) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBarClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
            Text(
                text = "What is on your heart?",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = "Add Photo",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun PostCard(
    post: com.arua.lonyichat.data.Post,
    viewModel: HomeFeedViewModel,
    onCommentClicked: () -> Unit,
    showReactionSelector: Boolean,
    onSelectorOpen: (String) -> Unit,
    onSelectorDismiss: () -> Unit,
    onProfileClicked: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val currentUserId = ApiService.getCurrentUserId()
    val isAuthor = post.authorId == currentUserId
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
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

    fullScreenImageUrl?.let { imageUrl ->
        FullScreenImageDialog(
            imageUrl = imageUrl,
            onDismiss = { fullScreenImageUrl = null }
        )
    }


    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onProfileClicked(post.authorId) }
                    ) {
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
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { fullScreenImageUrl = post.imageUrl },
                        contentScale = ContentScale.Crop
                    )
                }
            }

            PostReactionSummary(
                post = post,
                onSummaryClicked = {
                    viewModel.fetchReactors(post.id)
                }
            )

            Box(
                modifier = Modifier.fillMaxWidth()
            ) {

                PostInteractionBar(
                    post = post,
                    viewModel = viewModel,
                    onCommentClicked = onCommentClicked,
                    onLikeLongPressed = { onSelectorOpen(post.id) },
                    isSelectorVisible = showReactionSelector
                )

                if (showReactionSelector) {
                    val density = LocalDensity.current
                    Popup(
                        alignment = Alignment.BottomStart,
                        offset = with(density) {
                            IntOffset(16.dp.roundToPx(), -72.dp.roundToPx())
                        },
                        onDismissRequest = onSelectorDismiss
                    ) {
                        ReactionSelectorMenu(
                            onReactionSelected = { reactionType ->
                                viewModel.reactToPost(post.id, reactionType)
                                onSelectorDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditPostDialog(
    postContent: String,
    onDismiss: () -> Unit,
    onPost: (newContent: String) -> Unit
) {
    var newContent by remember { mutableStateOf(postContent) }
    val isSaveEnabled = newContent.isNotBlank() && newContent != postContent

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Post") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                OutlinedTextField(
                    value = newContent,
                    onValueChange = { newContent = it },
                    label = { Text("Post Content") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 100.dp),
                    colors = textFieldColors
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPost(newContent) },
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
fun FullScreenImageDialog(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Full-screen post image",
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clickable(onClick = onDismiss),
                contentScale = ContentScale.Fit
            )

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
        Icon(icon, contentDescription = text, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PostReactionSummary(post: com.arua.lonyichat.data.Post, onSummaryClicked: () -> Unit) {
    val totalReactions = post.reactions.amen + post.reactions.hallelujah + post.reactions.praiseGod

    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), thickness = 1.dp)

    if (totalReactions > 0) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSummaryClicked)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Text(
                text = "${post.commentCount} Comments · ${post.shareCount} Shares",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), thickness = 1.dp)
    }
}


@Composable
fun PostInteractionBar(
    post: com.arua.lonyichat.data.Post,
    viewModel: HomeFeedViewModel,
    onCommentClicked: () -> Unit,
    onLikeLongPressed: () -> Unit,
    isSelectorVisible: Boolean
) {
    val currentReaction = when {
        post.userReactions.amen -> "amen"
        post.userReactions.hallelujah -> "hallelujah"
        post.userReactions.praiseGod -> "praiseGod"
        else -> null
    }

    val (icon, text) = when (currentReaction) {
        "amen" -> Pair(Icons.Default.ThumbUp, "Amen")
        "hallelujah" -> Pair(Icons.Default.Star, "Hallelujah")
        "praiseGod" -> Pair(Icons.Default.Favorite, "Praise God")
        else -> Pair(Icons.Default.ThumbUp, "Like")
    }

    val contentColor by animateColorAsState(
        targetValue = if (currentReaction != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        label = "reactionColor"
    )

    val reactionTypeForShortClick = currentReaction ?: "amen"

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    onClick = {
                        viewModel.reactToPost(post.id, reactionTypeForShortClick)
                    },
                    onLongClick = onLikeLongPressed
                )
                .padding(vertical = 8.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = text, tint = contentColor)
            Spacer(Modifier.width(8.dp))
            Text(text, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
        }

        InteractionButton(
            icon = Icons.Default.Comment,
            text = "Comment",
            onClick = onCommentClicked,
            modifier = Modifier.weight(1f)
        )

        InteractionButton(
            icon = Icons.Default.Share,
            text = "Share",
            onClick = { viewModel.sharePost(post.id) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ReactionSelectorMenu(
    onReactionSelected: (reactionType: String) -> Unit
) {
    var focusedReactionIndex by remember { mutableStateOf<Int?>(null) }
    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    val reactionCount = LonyiReactions.size

    val indexFromPosition: (x: Float) -> Int? = { x ->
        if (rowSize.width == 0) null else {
            val itemWidth = rowSize.width.toFloat() / reactionCount
            (x / itemWidth).toInt().coerceIn(0, reactionCount - 1)
        }
    }

    Card(
        elevation = CardDefaults.cardElevation(8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            focusedReactionIndex?.let { index ->
                val reaction = LonyiReactions[index]
                Text(
                    text = reaction.label,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier
                    .onSizeChanged { rowSize = it }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset -> focusedReactionIndex = indexFromPosition(offset.x) },
                            onDragEnd = { focusedReactionIndex = null },
                            onDragCancel = { focusedReactionIndex = null },
                            onDrag = { change, _ ->
                                focusedReactionIndex = indexFromPosition(change.position.x)
                                change.consume()
                            }
                        )
                    }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LonyiReactions.forEachIndexed { index, reaction ->
                    val isFocused = index == focusedReactionIndex
                    val scale by animateFloatAsState(
                        targetValue = if (isFocused) 1.5f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "reactionScale"
                    )
                    Box(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onReactionSelected(reaction.type) }
                            )
                            .size(40.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = reaction.emoji,
                            fontSize = 24.sp,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReactorListDialog(
    uiState: ReactorUiState,
    onDismiss: () -> Unit
) {
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

            val allReactors = (amenReactors + hallelujahReactors + praiseGodReactors)
                .distinctBy { it.userId }
                .toMutableList()

            Column(modifier = Modifier.fillMaxWidth()) {
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
fun GroupsChurchScreen(viewModel: ChurchesViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = uiState.isLoading)

    val onRefresh = {
        coroutineScope.launch {
            val startTime = System.currentTimeMillis()
            viewModel.fetchChurches()
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            if (duration < MIN_REFRESH_DURATION) {
                delay(MIN_REFRESH_DURATION - duration)
            }
        }
        Unit
    }

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
    val coroutineScope = rememberCoroutineScope()

    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = uiState.isLoading)

    val onRefresh = {
        coroutineScope.launch {
            val startTime = System.currentTimeMillis()
            viewModel.fetchConversations()
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            if (duration < MIN_REFRESH_DURATION) {
                delay(MIN_REFRESH_DURATION - duration)
            }
        }
        Unit
    }

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
    val coroutineScope = rememberCoroutineScope()

    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = uiState.isLoading)

    val onRefresh = {
        coroutineScope.launch {
            val startTime = System.currentTimeMillis()
            viewModel.fetchMedia()
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            if (duration < MIN_REFRESH_DURATION) {
                delay(MIN_REFRESH_DURATION - duration)
            }
        }
        Unit
    }

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