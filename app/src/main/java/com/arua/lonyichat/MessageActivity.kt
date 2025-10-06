// app/src/main/java/com/arua/lonyichat/MessageActivity.kt

package com.arua.lonyichat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.arua.lonyichat.ui.theme.LonyiChatTheme
import com.arua.lonyichat.ui.viewmodel.MessageViewModel

class MessageActivity : ComponentActivity() {

    private val viewModel: MessageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This correctly prepares the activity to draw behind the system bars.
        enableEdgeToEdge()

        val chatId = intent.getStringExtra("CHAT_ID")
        val otherUserName = intent.getStringExtra("OTHER_USER_NAME") ?: "Chat"
        // It's good practice to also get the other user's ID if available
        val otherUserId = intent.getStringExtra("OTHER_USER_ID")

        if (chatId == null || otherUserId == null) {
            finish() // Exit if essential data is missing
            return
        }

        setContent {
            LonyiChatTheme {
                // The custom theme and system bar coloring are now handled by the theme itself.
                MessageScreen(
                    chatId = chatId,
                    viewModel = viewModel,
                    otherUserName = otherUserName,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}