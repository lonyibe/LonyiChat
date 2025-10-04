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
                    onBackPressed = { finish() }
                )
            }
        }
    }
}