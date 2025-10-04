package com.arua.lonyichat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.arua.lonyichat.ui.theme.LonyiChatTheme
import com.arua.lonyichat.ui.viewmodel.MessageViewModel

class MessageActivity : ComponentActivity() {

    private val viewModel: MessageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val chatId = intent.getStringExtra("CHAT_ID")
        // ADDED START: Extract new extras
        val otherUserId = intent.getStringExtra("OTHER_USER_ID")
        val friendshipStatus = intent.getStringExtra("FRIENDSHIP_STATUS") ?: "none"
        val otherUserName = intent.getStringExtra("OTHER_USER_NAME") ?: "Chat"
        // ADDED END

        if (chatId == null) {
            // Handle the error, maybe finish the activity
            finish()
            return
        }

        setContent {
            LonyiChatTheme {
                MessageScreen(
                    chatId = chatId,
                    viewModel = viewModel,
                    // ADDED START: Pass new required parameters
                    otherUserId = otherUserId,
                    friendshipStatus = friendshipStatus,
                    otherUserName = otherUserName,
                    // ADDED END
                    onBackPressed = { finish() }
                )
            }
        }
    }
}