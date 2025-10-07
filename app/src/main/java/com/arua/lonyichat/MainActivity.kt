@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.arua.lonyichat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels // FIX: This import is now available
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.core.view.WindowCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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
    AppReaction("hallelujah", "🥳", "Hallelujah")
)

data class UserProfileState(
    val userName: String = "Loading...",
    val photoUrl: String? = null,
    val isLoading: Boolean = true
)

sealed class Screen(val title: String, val icon: ImageVector) {
    object Home : Screen("Feed", Icons.Filled.Home)
    object Events : Screen("Events", Icons.Filled.Event)
    object Groups : Screen("Churches", Icons.Filled.Group)
    object Bible : Screen("Bible", Icons.Filled.Book)
    object Chat : Screen("Chat", Icons.AutoMirrored.Filled.Message)
    object Media : Screen("Media", Icons.Filled.LiveTv)
}

private const val EXTRA_NOTIFICATION_ID = "notification_id"

class MainActivity : ComponentActivity() {
    // These viewModels delegates should now resolve correctly
    private val homeFeedViewModel: HomeFeedViewModel by viewModels()
    private val churchesViewModel: ChurchesViewModel by viewModels()
    private val chatListViewModel: ChatListViewModel by viewModels()
    private val bibleViewModel: BibleViewModel by viewModels()
    private val mediaViewModel: MediaViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()
    private val eventViewModel: EventViewModel by viewModels()
    private val notificationViewModel: NotificationViewModel by viewModels()

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
                        profileViewModel,
                        eventViewModel,
                        notificationViewModel
                    )
                }
            }
        }
        handleNotificationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        ApiService.getCurrentUserId()?.let {
            profileViewModel.fetchProfile(it)
        }
        churchesViewModel.fetchChurches()
        notificationViewModel.fetchNotifications()
        // FIX: Ensure chat list view model also fetches on resume for unread count update
        // The read receipt is saved when the MessageActivity opens, so refetching here pulls the updated (zeroed) count.
        chatListViewModel.fetchConversations()
    }

    override fun onPause() {
        super.onPause()
        mediaViewModel.pauseAllPlayers()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaViewModel.releaseAllPlayers()
    }


    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.let { handleNotificationIntent(it) }
    }

    private fun handleNotificationIntent(intent: Intent) {
        val notificationId = intent.getStringExtra(EXTRA_NOTIFICATION_ID)
        if (!notificationId.isNullOrBlank()) {
            notificationViewModel.markAsRead(notificationId)
            intent.removeExtra(EXTRA_NOTIFICATION_ID)
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
    profileViewModel: ProfileViewModel,
    eventViewModel: EventViewModel,
    notificationViewModel: NotificationViewModel
) {
    val profileUiState by profileViewModel.uiState.collectAsState()
    val unreadNotificationCount by notificationViewModel.unreadCount.collectAsState()
    // FIX: Collect the unread chat count
    val unreadChatCount by chatListViewModel.unreadChatCount.collectAsState()
    val profileState = UserProfileState(
        userName = profileUiState.profile?.name ?: "Loading...",
        photoUrl = profileUiState.profile?.photoUrl,
        isLoading = profileUiState.isLoading
    )
    var selectedItem: Screen by remember { mutableStateOf(Screen.Home) }
    val bottomBarItems = listOf(Screen.Home, Screen.Events, Screen.Groups, Screen.Bible, Screen.Chat, Screen.Media)
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    val createMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            mediaViewModel.fetchMedia()
        }
    }

    LaunchedEffect(selectedItem) {
        if (selectedItem != Screen.Media) {
            mediaViewModel.pauseAllPlayers()
        }
    }

    LaunchedEffect(ApiService.getCurrentUserId()) {
        if (ApiService.getCurrentUserId() == null) {
            val intent = Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
    }

    val containerColor = if (selectedItem == Screen.Media) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.background
    }


    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = containerColor,
        topBar = {
            val isDarkTheme = isSystemInDarkTheme()
            val headerColor = if (isDarkTheme) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary
            Column {
                LonyiChatTopBar(
                    title = "LonyiChat",
                    onProfileClicked = {
                        val intent = Intent(context, ProfileActivity::class.java).apply {
                            putExtra("USER_ID", ApiService.getCurrentUserId())
                        }
                        context.startActivity(intent)
                    },
                    scrollBehavior = scrollBehavior,
                    unreadCount = unreadNotificationCount
                )
                if (selectedItem == Screen.Media) {
                    TabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = headerColor,
                        contentColor = if (isDarkTheme) MaterialTheme.colorScheme.onSurface else Color.White
                    ) {
                        Tab(
                            selected = pagerState.currentPage == 0,
                            onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                            text = { Text("Church Vibes") }
                        )
                        Tab(
                            selected = pagerState.currentPage == 1,
                            onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                            text = { Text("Livestreams") }
                        )
                    }
                }
            }
        },
        bottomBar = {
            LonyiChatBottomBar(
                items = bottomBarItems,
                selectedItem = selectedItem,
                onItemSelected = { selectedItem = it },
                // FIX: Pass the chat count to the bottom bar
                unreadChatCount = unreadChatCount
            )
        },
        floatingActionButton = {
            if (selectedItem == Screen.Media && pagerState.currentPage == 0) {
                FloatingActionButton(
                    onClick = {
                        createMediaLauncher.launch(Intent(context, CreateMediaActivity::class.java))
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = "Upload Video")
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
                eventViewModel = eventViewModel,
                pagerState = pagerState
            )
        }
    }
}

@Suppress("DEPRECATION")
@Composable
fun LonyiChatTopBar(
    title: String,
    onProfileClicked: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    unreadCount: Int
) {
    val isDarkTheme = isSystemInDarkTheme()
    val view = LocalView.current
    val headerColor = if (isDarkTheme) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary
    val context = LocalContext.current

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
            IconButton(onClick = {
                context.startActivity(Intent(context, SearchActivity::class.java))
            }) {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
            }

            BadgedBox(
                badge = {
                    if (unreadCount > 0) {
                        Badge(containerColor = Color.Red) {
                            Text(text = if (unreadCount > 99) "99+" else unreadCount.toString())
                        }
                    }
                }
            ) {
                IconButton(onClick = {
                    context.startActivity(Intent(context, NotificationsActivity::class.java))
                }) {
                    Icon(imageVector = Icons.Default.Notifications, contentDescription = "Notifications")
                }
            }

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
fun LonyiChatBottomBar(items: List<Screen>, selectedItem: Screen, onItemSelected: (Screen) -> Unit, unreadChatCount: Int) {
    NavigationBar(
        modifier = Modifier.navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        items.forEach { screen ->
            val isChatScreen = screen == Screen.Chat
            NavigationBarItem(
                icon = {
                    // FIX: Use BadgedBox for the Chat icon if it's the chat screen
                    if (isChatScreen) {
                        BadgedBox(
                            badge = {
                                if (unreadChatCount > 0) {
                                    Badge(containerColor = Color.Red) {
                                        // Ensure "99+" for high counts
                                        Text(text = if (unreadChatCount > 99) "99+" else unreadChatCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(screen.icon, contentDescription = screen.title)
                        }
                    } else {
                        // Standard icon for other screens
                        Icon(screen.icon, contentDescription = screen.title)
                    }
                },
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
    mediaViewModel: MediaViewModel,
    eventViewModel: EventViewModel,
    pagerState: PagerState
) {
    when (screen) {
        Screen.Home -> HomeFeedScreen(profileState, homeFeedViewModel)
        Screen.Events -> EventsScreen(eventViewModel)
        Screen.Groups -> GroupsChurchScreen(churchesViewModel)
        Screen.Bible -> BibleStudyScreen(bibleViewModel)
        Screen.Chat -> com.arua.lonyichat.ChatScreen(chatListViewModel)
        Screen.Media -> MediaScreen(mediaViewModel, pagerState)
    }
}

@Composable
fun HomeFeedScreen(profileState: UserProfileState, viewModel: HomeFeedViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val createPostLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.fetchPosts()
        }
    }
    val reactorUiState by viewModel.reactorUiState.collectAsState()

    if (reactorUiState.isLoading || reactorUiState.reactors.amen.isNotEmpty() || reactorUiState.reactors.hallelujah.isNotEmpty() || reactorUiState.reactors.praiseGod.isNotEmpty() || reactorUiState.reactors.praying.isNotEmpty()) {
        ReactorListDialog(uiState = reactorUiState, onDismiss = { viewModel.clearReactorState() })
    }

    if (reactorUiState.error != null) {
        Toast.makeText(LocalContext.current, "Error loading reactors: ${reactorUiState.error}", Toast.LENGTH_LONG).show()
        viewModel.clearReactorState()
    }

    val lazyListState = rememberLazyListState()
    LaunchedEffect(uiState.posts.firstOrNull()?.id) {
        if (uiState.posts.isNotEmpty()) {
            lazyListState.animateScrollToItem(0)
        }
    }

    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = uiState.isLoading)
    val onRefresh = { viewModel.fetchPosts() }
    var openReactionPostId by remember { mutableStateOf<String?>(null) }

    when {
        uiState.isLoading && uiState.posts.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        uiState.error != null -> {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
            }
        }
        else -> {
            SwipeRefresh(state = swipeRefreshState, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        PostCreationBar(profileState = profileState) {
                            val intent = Intent(context, CreatePostActivity::class.java)
                            createPostLauncher.launch(intent)
                        }
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
fun EventsScreen(viewModel: EventViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val onRefresh = { viewModel.fetchEvents() }
    val context = LocalContext.current
    val createEventLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.fetchEvents()
        }
        viewModel.resetSuccessState()
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Church Events", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = {
                val intent = Intent(context, CreateEventActivity::class.java)
                createEventLauncher.launch(intent)
            }) {
                Icon(Icons.Default.Add, contentDescription = "Create Event"); Spacer(modifier = Modifier.width(4.dp)); Text("Post Event")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        when {
            uiState.isLoading && uiState.events.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            uiState.error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error) }
            uiState.events.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(imageVector = Icons.Default.Event, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Text("No upcoming events.", style = MaterialTheme.typography.titleLarge)
                        Button(onClick = {
                            val intent = Intent(context, CreateEventActivity::class.java)
                            createEventLauncher.launch(intent)
                        }) { Text("Create First Event") }
                    }
                }
            }
            else -> {
                SwipeRefresh(state = rememberSwipeRefreshState(isRefreshing = uiState.isLoading), onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(uiState.events, key = { it.id }) { event ->
                            EventCard(
                                event = event,
                                onDelete = { eventId -> viewModel.deleteEvent(eventId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EventCard(event: Event, onDelete: (String) -> Unit) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val currentUserId = ApiService.getCurrentUserId()
    val isAuthor = event.createdBy == currentUserId

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Event") },
            text = { Text("Are you sure you want to permanently delete the event '${event.title}'? This cannot be undone.") },
            confirmButton = { Button(onClick = {
                if (runCatching { event.id.isNotBlank() }.getOrDefault(false)) {
                    onDelete(event.id)
                }
                showDeleteConfirmDialog = false
            }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") } }
        )
    }

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            event.imageUrl?.let { url ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(url).build(),
                    contentDescription = "Event Image for ${event.title}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = ContentScale.Crop
                )
            } ?: Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = SimpleDateFormat("EEEE, MMM dd, yyyy \u2022 h:mm a", Locale.getDefault()).format(event.date.toDate()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(event.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = "Location", modifier = Modifier.size(16.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(event.location, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(event.description, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)

                Divider(Modifier.padding(vertical = 12.dp))

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = event.authorPhotoUrl,
                            contentDescription = "Event Creator",
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Posted by ${event.authorName}", style = MaterialTheme.typography.labelMedium)
                    }

                    if (isAuthor) {
                        OutlinedButton(
                            onClick = { showDeleteConfirmDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Event", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PostCreationBar(profileState: UserProfileState, onBarClick: () -> Unit) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onBarClick).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(profileState.photoUrl).crossfade(true).placeholder(R.drawable.ic_person_placeholder).error(R.drawable.ic_person_placeholder).build(),
                contentDescription = "Your Profile Photo",
                modifier = Modifier.size(40.dp).clip(CircleShape)
            )
            Text("What is on your heart?", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = "Add Photo", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun PostCard(post: Post, viewModel: HomeFeedViewModel, onCommentClicked: () -> Unit, showReactionSelector: Boolean, onSelectorOpen: (String) -> Unit, onSelectorDismiss: () -> Unit, onProfileClicked: (String) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val currentUserId = ApiService.getCurrentUserId()
    val isAuthor = post.authorId == currentUserId
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }

    if (isAuthor && showEditDialog) {
        EditPostDialog(postContent = post.content, onDismiss = { showEditDialog = false }) { newContent ->
            viewModel.updatePost(post.id, newContent)
            showEditDialog = false
        }
    }

    if (isAuthor && showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Post") },
            text = { Text("Are you sure you want to permanently delete this post?") },
            confirmButton = { Button(onClick = { viewModel.deletePost(post.id); showDeleteConfirmDialog = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") } }
        )
    }

    fullScreenImageUrl?.let { imageUrl ->
        FullScreenImageDialog(imageUrl = imageUrl, onDismiss = { fullScreenImageUrl = null })
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(0.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onProfileClicked(post.authorId) }) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(post.authorPhotoUrl).crossfade(true).placeholder(R.drawable.ic_person_placeholder).error(R.drawable.ic_person_placeholder).build(),
                            contentDescription = "Author's Profile Photo",
                            modifier = Modifier.size(40.dp).clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(post.authorName, fontWeight = FontWeight.Bold)
                            Text(post.createdAt.toDate().toRelativeTimeString(), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                    if (isAuthor) {
                        Box {
                            IconButton(onClick = { showMenu = !showMenu }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("Edit") }, onClick = { showEditDialog = true; showMenu = false })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { showDeleteConfirmDialog = true; showMenu = false })
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(post.content, style = MaterialTheme.typography.bodyMedium)
                post.imageUrl?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    AsyncImage(model = it, contentDescription = "Post image", modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { fullScreenImageUrl = it }, contentScale = ContentScale.Crop)
                }
                if (post.type == "poll" && post.poll != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    PollView(poll = post.poll, onVote = { optionId -> viewModel.voteOnPoll(post.id, optionId) })
                }
            }
            PostReactionSummary(post = post) { viewModel.fetchReactors(post.id) }
            Box(modifier = Modifier.fillMaxWidth()) {
                PostInteractionBar(post, viewModel, onCommentClicked, { onSelectorOpen(post.id) }, showReactionSelector)
                if (showReactionSelector) {
                    val density = LocalDensity.current
                    Popup(alignment = Alignment.BottomStart, offset = with(density) { IntOffset(16.dp.roundToPx(), -72.dp.roundToPx()) }, onDismissRequest = onSelectorDismiss) {
                        ReactionSelectorMenu { reactionType ->
                            viewModel.reactToPost(post.id, reactionType)
                            onSelectorDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PollView(poll: Poll, onVote: (String) -> Unit) {
    val totalVotes = poll.options.sumOf { it.votes.size }
    val currentUser = ApiService.getCurrentUserId()
    // FIX: The error was here. It should check if any option's votes list contains the currentUser ID.
    val userVote = poll.options.find { it.votes.contains(currentUser) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        poll.options.forEach { option ->
            val voteCount = option.votes.size
            val percentage = if (totalVotes > 0) (voteCount.toFloat() / totalVotes) else 0f
            val isSelected = option.id == userVote?.id

            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onVote(option.id) }) {
                Box(modifier = Modifier.fillMaxWidth(percentage).height(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(option.option, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$voteCount votes", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
fun EditPostDialog(postContent: String, onDismiss: () -> Unit, onPost: (newContent: String) -> Unit) {
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
                OutlinedTextField(value = newContent, onValueChange = { newContent = it }, label = { Text("Post Content") }, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 100.dp), colors = textFieldColors)
            }
        },
        confirmButton = { Button(onClick = { onPost(newContent) }, enabled = isSaveEnabled) { Text("Save Changes") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.background,
        textContentColor = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
fun FullScreenImageDialog(imageUrl: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            AsyncImage(model = imageUrl, contentDescription = "Full-screen post image", modifier = Modifier.fillMaxWidth().wrapContentHeight().clickable(onClick = onDismiss), contentScale = ContentScale.Fit)
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

@Composable
fun InteractionButton(painter: Painter, text: String, onClick: () -> Unit, modifier: Modifier = Modifier, contentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 16.dp), horizontalArrangement = Arrangement.Center) {
        Icon(painter, contentDescription = text, tint = contentColor)
        Spacer(Modifier.width(8.dp))
        Text(text, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun InteractionButton(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 16.dp), horizontalArrangement = Arrangement.Center) {
        Icon(icon, contentDescription = text, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Spacer(Modifier.width(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PostReactionSummary(post: Post, onSummaryClicked: () -> Unit) {
    val totalReactions = post.reactions.amen + post.reactions.hallelujah + post.reactions.praiseGod + post.reactions.praying

    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), thickness = 1.dp)

    if (totalReactions > 0) {
        Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onSummaryClicked).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (post.reactions.praying > 0) {
                    Icon(painterResource(id = R.drawable.ic_prayer), contentDescription = "Praying", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                if (post.reactions.amen > 0) { Text("🙏", fontSize = 16.sp); Spacer(Modifier.width(4.dp)) }
                if (post.reactions.hallelujah > 0) { Text("🥳", fontSize = 16.sp); Spacer(Modifier.width(4.dp)) }
                if (post.reactions.praiseGod > 0) { Text("🙌", fontSize = 16.sp); Spacer(Modifier.width(4.dp)) }
                Text(totalReactions.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.padding(start = 4.dp))
            }
            Text("${post.commentCount} Comments · ${post.shareCount} Shares", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), thickness = 1.dp)
    }
}

@Composable
fun PostInteractionBar(post: Post, viewModel: HomeFeedViewModel, onCommentClicked: () -> Unit, onLikeLongPressed: () -> Unit, isSelectorVisible: Boolean) {
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

    val contentColor by animateColorAsState(targetValue = if (currentReaction != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), label = "reactionColor")
    val reactionTypeForShortClick = currentReaction ?: "amen"

    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
        if (post.type == "prayer") {
            val prayingColor by animateColorAsState(targetValue = if (post.userReactions.praying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), label = "prayingColor")
            InteractionButton(painter = painterResource(id = R.drawable.ic_prayer), text = if (post.userReactions.praying) "Praying" else "Pray", onClick = { viewModel.reactToPost(post.id, "praying") }, modifier = Modifier.weight(1f), contentColor = prayingColor)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).combinedClickable(onClick = { viewModel.reactToPost(post.id, reactionTypeForShortClick) }, onLongClick = onLikeLongPressed).padding(vertical = 8.dp, horizontal = 16.dp), horizontalArrangement = Arrangement.Center) {
                Icon(icon, contentDescription = text, tint = contentColor)
                Spacer(Modifier.width(8.dp))
                Text(text, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            }
        }
        InteractionButton(icon = Icons.Default.Comment, text = "Comment", onClick = onCommentClicked, modifier = Modifier.weight(1f))
        InteractionButton(icon = Icons.Default.Share, text = "Share", onClick = { viewModel.sharePost(post.id) }, modifier = Modifier.weight(1f))
    }
}

@Composable
fun ReactionSelectorMenu(onReactionSelected: (reactionType: String) -> Unit) {
    var focusedReactionIndex by remember { mutableStateOf<Int?>(null) }
    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    val reactionCount = LonyiReactions.size
    val indexFromPosition: (x: Float) -> Int? = { x -> if (rowSize.width == 0) null else { (x / (rowSize.width.toFloat() / reactionCount)).toInt().coerceIn(0, reactionCount - 1) } }

    Card(elevation = CardDefaults.cardElevation(8.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            focusedReactionIndex?.let { index ->
                val reaction = LonyiReactions[index]
                Text(reaction.label, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(modifier = Modifier.onSizeChanged { rowSize = it }.pointerInput(Unit) { detectDragGestures(onDragStart = { offset -> focusedReactionIndex = indexFromPosition(offset.x) }, onDragEnd = { focusedReactionIndex = null }, onDragCancel = { focusedReactionIndex = null }, onDrag = { change, _ -> focusedReactionIndex = indexFromPosition(change.position.x); change.consume() }) }.padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LonyiReactions.forEachIndexed { index, reaction ->
                    val isFocused = index == focusedReactionIndex
                    val scale by animateFloatAsState(targetValue = if (isFocused) 1.5f else 1.0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "reactionScale")
                    Box(modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { onReactionSelected(reaction.type) }).size(40.dp).scale(scale).clip(CircleShape).background(if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent), contentAlignment = Alignment.Center) {
                        Text(reaction.emoji, fontSize = 24.sp, modifier = Modifier.padding(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ReactorListDialog(uiState: ReactorUiState, onDismiss: () -> Unit) {
    val content: @Composable () -> Unit = {
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (uiState.error != null) {
            Text("Error loading reactions: ${uiState.error}", color = MaterialTheme.colorScheme.error)
        } else {
            val allReactors = (uiState.reactors.amen + uiState.reactors.hallelujah + uiState.reactors.praiseGod + uiState.reactors.praying).distinctBy { it.userId }.toMutableList()
            Column(modifier = Modifier.fillMaxWidth()) {
                var selectedTab by remember { mutableStateOf(0) }
                val tabs = listOf(
                    Triple("All", allReactors.size, null),
                    Triple("Amen", uiState.reactors.amen.size, "🙏"),
                    Triple("Hallelujah", uiState.reactors.hallelujah.size, "🥳"),
                    Triple("Praise God", uiState.reactors.praiseGod.size, "🙌"),
                    Triple("Praying", uiState.reactors.praying.size, "🛐")
                )
                ScrollableTabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) {
                    tabs.forEachIndexed { index, tabData ->
                        val (title, count, emoji) = tabData
                        Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text("$title ($count)", style = MaterialTheme.typography.labelLarge) }, icon = if (emoji != null) { { Text(emoji, fontSize = 20.sp) } } else null)
                    }
                }
                Spacer(Modifier.height(8.dp))
                val currentList = when (selectedTab) { 1 -> uiState.reactors.amen; 2 -> uiState.reactors.hallelujah; 3 -> uiState.reactors.praiseGod; 4 -> uiState.reactors.praying; else -> allReactors }
                if (currentList.isEmpty()) {
                    Text("No one has left this reaction yet.", modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 300.dp)) {
                        items(currentList, key = { it.userId }) { reactor -> ReactorItem(reactor = reactor) }
                    }
                }
            }
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Post Reactions") }, text = content, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }, containerColor = MaterialTheme.colorScheme.background, textContentColor = MaterialTheme.colorScheme.onBackground)
}

@Composable
fun ReactorItem(reactor: Reactor) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(reactor.photoUrl).crossfade(true).placeholder(R.drawable.ic_person_placeholder).error(R.drawable.ic_person_placeholder).build(), contentDescription = "Reactor Profile Photo", modifier = Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        Spacer(modifier = Modifier.width(12.dp))
        Text(reactor.name, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun GroupsChurchScreen(viewModel: ChurchesViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val onRefresh = { viewModel.fetchChurches() }
    val context = LocalContext.current
    val createChurchLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.fetchChurches()
        }
    }

    var searchQuery by remember { mutableStateOf("") }

    val validChurches = uiState.churches.filter { it.id.isNotBlank() }

    val filteredChurches = remember(validChurches, searchQuery) {
        if (searchQuery.isBlank()) {
            validChurches
        } else {
            validChurches.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.description.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Churches & Groups", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = { createChurchLauncher.launch(Intent(context, CreateChurchActivity::class.java)) }) {
                Icon(Icons.Default.Add, contentDescription = "Create Church"); Spacer(modifier = Modifier.width(4.dp)); Text("Create")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search Church or Group") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(24.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))


        when {
            uiState.isLoading && uiState.churches.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            uiState.error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error) }
            uiState.churches.isEmpty() -> EmptyChurchesView { createChurchLauncher.launch(Intent(context, CreateChurchActivity::class.java)) }
            filteredChurches.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No results found for \"$searchQuery\"", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                }
            }
            else -> {
                SwipeRefresh(state = rememberSwipeRefreshState(isRefreshing = uiState.isLoading), onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(items = filteredChurches, key = { it.id }) { church ->
                            ChurchCard(
                                church = church,
                                onJoinClicked = { viewModel.joinChurch(church.id) },
                                onCardClicked = {
                                    val intent = Intent(context, ChurchChatActivity::class.java).apply {
                                        putExtra("CHURCH_EXTRA", church)
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
}

@Composable
fun EmptyChurchesView(onCreateClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(imageVector = Icons.Default.Group, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Text("No churches yet.", style = MaterialTheme.typography.titleLarge)
            Text("Be the first to create a community or find one to join.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Button(onClick = onCreateClick) { Text("Create a Church or Group") }
        }
    }
}

@Composable
fun ChurchCard(church: Church, onJoinClicked: () -> Unit, onCardClicked: () -> Unit) {
    val currentUserId = ApiService.getCurrentUserId()
    val isMember = church.members.contains(currentUserId)

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onCardClicked), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(church.photoUrl)
                    .crossfade(true)
                    .placeholder(R.drawable.ic_person_placeholder)
                    .error(R.drawable.ic_person_placeholder)
                    .build(),
                contentDescription = "Church Profile Photo",
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(church.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(church.description, style = MaterialTheme.typography.bodyMedium, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(8.dp))
                Text("${church.followerCount} Members", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = onJoinClicked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMember) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                    contentColor = if (isMember) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(if (isMember) "Joined" else "Join")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BibleStudyScreen(viewModel: BibleViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var bookSelectorOpen by remember { mutableStateOf(false) }
    var chapterSelectorOpen by remember { mutableStateOf(false) }
    var versionSelectorOpen by remember { mutableStateOf(false) }
    val chapterListState = rememberLazyListState()

    LaunchedEffect(uiState.chapterContent) {
        if (uiState.chapterContent.isNotEmpty()) {
            chapterListState.scrollToItem(0)
        }
    }

    if (chapterSelectorOpen) {
        ChapterSelectorDialog(
            chapterCount = uiState.chapterCount,
            onDismiss = { chapterSelectorOpen = false },
            onChapterSelected = {
                viewModel.selectChapter(it)
                chapterSelectorOpen = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(4.dp),
            shape = RoundedCornerShape(0.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExposedDropdownMenuBox(
                        expanded = bookSelectorOpen,
                        onExpandedChange = { bookSelectorOpen = !bookSelectorOpen },
                        modifier = Modifier.weight(1.5f)
                    ) {
                        OutlinedTextField(
                            value = uiState.selectedBook,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bookSelectorOpen) },
                            modifier = Modifier.menuAnchor(),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        ExposedDropdownMenu(
                            expanded = bookSelectorOpen,
                            onDismissRequest = { bookSelectorOpen = false }
                        ) {
                            uiState.books.forEach { book ->
                                DropdownMenuItem(
                                    text = { Text(book) },
                                    onClick = {
                                        viewModel.selectBook(book)
                                        bookSelectorOpen = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { chapterSelectorOpen = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ch. ${uiState.selectedChapter}")
                    }
                }
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = versionSelectorOpen,
                    onExpandedChange = { versionSelectorOpen = !versionSelectorOpen }
                ) {
                    OutlinedTextField(
                        value = uiState.availableVersions.find { it.first == uiState.selectedVersion }?.second ?: "Select Version",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = versionSelectorOpen) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    ExposedDropdownMenu(
                        expanded = versionSelectorOpen,
                        onDismissRequest = { versionSelectorOpen = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        uiState.availableVersions.forEach { version ->
                            DropdownMenuItem(
                                text = { Text(version.second) },
                                onClick = {
                                    viewModel.selectVersion(version.first)
                                    versionSelectorOpen = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            val chapterError = uiState.chapterError
            when {
                uiState.isChapterLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                chapterError != null -> {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(chapterError, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { viewModel.selectBook(uiState.selectedBook) }) { Text("Retry") }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        state = chapterListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        item {
                            Text(
                                uiState.chapterReference,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }
                        items(uiState.chapterContent, key = { it.verse }) { verse ->
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                                        append("${verse.verse} ")
                                    }
                                    append(verse.text.replace("\n", "").trim())
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(bottom = 8.dp),
                                lineHeight = 24.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChapterSelectorDialog(
    chapterCount: Int,
    onDismiss: () -> Unit,
    onChapterSelected: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select a Chapter") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 64.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(chapterCount) { chapter ->
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(CircleShape)
                            .clickable { onChapterSelected(chapter + 1) }
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${chapter + 1}",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@Composable
fun MediaScreen(
    viewModel: MediaViewModel,
    pagerState: PagerState
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        when (page) {
            0 -> ChurchVibesScreen(viewModel)
            1 -> LivestreamsScreen()
        }
    }
}

@Composable
fun ChurchVibesScreen(viewModel: MediaViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val mediaItems = remember(uiState.mediaItems) {
        uiState.mediaItems.filter { it.mediaType == "video" }
    }
    val pagerState = rememberPagerState(pageCount = { mediaItems.size })
    val playerManager = viewModel.getPlayerManager()
    val currentUserId = ApiService.getCurrentUserId()

    LaunchedEffect(pagerState.currentPage, mediaItems) {
        if (mediaItems.isNotEmpty()) {
            playerManager.updatePlayers(pagerState.currentPage, mediaItems)
        }
    }

    SwipeRefresh(
        state = rememberSwipeRefreshState(isRefreshing = uiState.isLoading),
        onRefresh = { viewModel.fetchMedia() },
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            uiState.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error loading media: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                }
            }
            mediaItems.isEmpty() && !uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No vibes yet. Be the first to upload!", style = MaterialTheme.typography.titleMedium)
                }
            }
            else -> {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { index -> mediaItems[index].id }
                ) { page ->
                    val mediaItem = mediaItems[page]
                    VideoPlayerItem(
                        player = playerManager.getPlayer(mediaItem),
                        mediaItem = mediaItem,
                        viewModel = viewModel,
                        isMyVideo = mediaItem.uploaderId == currentUserId,
                        onDelete = { videoId -> viewModel.deleteMedia(videoId) }
                    )
                }
            }
        }
    }
}


@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerItem(
    player: ExoPlayer,
    mediaItem: com.arua.lonyichat.data.MediaItem,
    viewModel: MediaViewModel,
    isMyVideo: Boolean,
    onDelete: (String) -> Unit
) {
    val context = LocalContext.current
    var showIndicator by remember { mutableStateOf(false) }
    val isPlaying by remember { derivedStateOf { player.isPlaying } }
    var progress by remember { mutableStateOf(0f) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Coroutine to smoothly update the progress bar
    LaunchedEffect(player) {
        while (true) {
            progress = if (player.duration > 0) {
                player.currentPosition.toFloat() / player.duration.toFloat()
            } else {
                0f
            }
            delay(50) // Update every 50ms for a smooth progress bar
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                super.onEvents(player, events)
                progress = if (player.duration > 0) {
                    player.currentPosition.toFloat() / player.duration.toFloat()
                } else {
                    0f
                }
            }
        }
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
        }
    }


    LaunchedEffect(showIndicator) {
        if (showIndicator) {
            delay(800)
            showIndicator = false
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Video") },
            text = { Text("Are you sure you want to delete this video?") },
            confirmButton = {
                Button(onClick = {
                    onDelete(mediaItem.id)
                    showDeleteDialog = false
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                player.playWhenReady = !player.playWhenReady
                showIndicator = true
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            update = { view ->
                view.player = player
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = showIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                contentDescription = "Playback Status",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(72.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            VideoActionButton(
                icon = if (mediaItem.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                text = mediaItem.likes.toString(),
                onClick = { viewModel.likeMedia(mediaItem.id) },
                tint = if (mediaItem.isLiked) Color.Red else Color.White
            )
            VideoActionButton(
                icon = Icons.Filled.Comment,
                text = mediaItem.comments.toString(),
                onClick = {
                    val intent = Intent(context, CommentsActivity::class.java)
                    intent.putExtra("POST_ID", mediaItem.id) // We can reuse POST_ID for media ID
                    context.startActivity(intent)
                }
            )
            VideoActionButton(
                icon = Icons.Filled.Share,
                text = mediaItem.shareCount.toString(),
                onClick = { viewModel.shareMedia(mediaItem.id) }
            )
            VideoActionButton(
                icon = Icons.Filled.Download,
                text = mediaItem.downloadCount.toString(),
                onClick = { viewModel.downloadMedia(context, mediaItem) }
            )
            if (isMyVideo) {
                VideoActionButton(
                    icon = Icons.Filled.Delete,
                    text = "Delete",
                    onClick = { showDeleteDialog = true }
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 80.dp, bottom = 16.dp, top = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = mediaItem.uploaderPhotoUrl,
                    contentDescription = "Uploader",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = mediaItem.uploaderName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = mediaItem.description,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}


@Composable
fun VideoActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    tint: Color = Color.White
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(32.dp)
        )
        Text(text, color = Color.White, fontSize = 12.sp)
    }
}


@Composable
fun LivestreamsScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.LiveTv, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Livestreams Coming Soon!", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

fun Date.toRelativeTimeString(): String {
    val now = System.currentTimeMillis()
    val diff = now - this.time
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = minutes / (60 * 24)

    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hours ago"
        days < 7 -> "$days days ago"
        else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(this)
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
        LonyiChatApp(HomeFeedViewModel(), ChurchesViewModel(), ChatListViewModel(), BibleViewModel(), MediaViewModel(), ProfileViewModel(), EventViewModel(), NotificationViewModel())
    }
}