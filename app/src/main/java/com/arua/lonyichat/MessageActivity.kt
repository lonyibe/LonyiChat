// app/src/main/java/com/arua/lonyichat/MessageActivity.kt

package com.arua.lonyichat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import com.arua.lonyichat.ui.theme.LonyiChatTheme
import com.arua.lonyichat.ui.theme.LonyiDarkSurface
import com.arua.lonyichat.ui.theme.LonyiOrange
import com.arua.lonyichat.ui.viewmodel.MessageViewModel

class MessageActivity : ComponentActivity() {

    private val viewModel: MessageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val chatId = intent.getStringExtra("CHAT_ID")
        val otherUserName = intent.getStringExtra("OTHER_USER_NAME") ?: "Chat"

        if (chatId == null) {
            finish()
            return
        }

        setContent {
            val isDarkTheme = isSystemInDarkTheme()

            // This effect will update the system bar colors to match the theme
            LaunchedEffect(isDarkTheme) {
                val darkScrim = LonyiDarkSurface.toArgb()
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        lightScrim = LonyiOrange.toArgb(),
                        darkScrim = darkScrim,
                    ) { isDarkTheme }
                )
            }

            LonyiChatTheme {
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