package com.arua.lonyichat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.rememberCoroutineScope
import com.arua.lonyichat.data.ApiService
import com.arua.lonyichat.ui.theme.LonyiChatTheme
import kotlinx.coroutines.launch

class NewConversationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LonyiChatTheme {
                val coroutineScope = rememberCoroutineScope()

                NewConversationScreen(
                    onBackPressed = { finish() },
                    onStartChat = { userId ->
                        coroutineScope.launch {
                            try {
                                val chatId = ApiService.createChat(userId)
                                val intent = Intent(this@NewConversationActivity, MessageActivity::class.java).apply {
                                    putExtra("CHAT_ID", chatId)
                                }
                                startActivity(intent)
                                finish() // Finish this activity so the user doesn't come back to it when pressing back
                            } catch (e: Exception) {
                                // Handle error, maybe show a toast
                            }
                        }
                    }
                )
            }
        }
    }
}