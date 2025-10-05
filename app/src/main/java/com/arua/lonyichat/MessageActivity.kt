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
        val otherUserId = intent.getStringExtra("OTHER_USER_ID")
        val otherUserName = intent.getStringExtra("OTHER_USER_NAME") ?: "Chat"

        if (chatId == null || otherUserId == null) {
            // Handle the error, maybe finish the activity
            finish()
            return
        }

        setContent {
            LonyiChatTheme {
                MessageScreen(
                    chatId = chatId,
                    viewModel = viewModel,
                    otherUserId = otherUserId,
                    otherUserName = otherUserName,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}