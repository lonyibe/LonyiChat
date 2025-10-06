// app/src/main/java/com/arua/lonyichat/MessageActivity.kt

package com.arua.lonyichat

import android.Manifest
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.arua.lonyichat.ui.theme.LonyiChatTheme
import com.arua.lonyichat.ui.viewmodel.MessageViewModel

class MessageActivity : ComponentActivity() {

    private val viewModel: MessageViewModel by viewModels()

    // ✨ ADDED: Launchers for picking media
    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>
    private lateinit var videoPickerLauncher: ActivityResultLauncher<String>
    private lateinit var audioPickerLauncher: ActivityResultLauncher<String>
    private lateinit var voiceRecorderLauncher: ActivityResultLauncher<String>
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    private var chatId: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        chatId = intent.getStringExtra("CHAT_ID")
        val otherUserName = intent.getStringExtra("OTHER_USER_NAME") ?: "Chat"
        val otherUserId = intent.getStringExtra("OTHER_USER_ID")

        if (chatId == null || otherUserId == null) {
            finish()
            return
        }

        // ✨ ADDED: Initialize the launchers
        setupResultLaunchers(chatId!!)

        setContent {
            LonyiChatTheme {
                MessageScreen(
                    chatId = chatId!!,
                    viewModel = viewModel,
                    otherUserName = otherUserName,
                    onBackPressed = { finish() },
                    // ✨ ADDED: Pass lambdas to trigger the launchers from the MessageScreen
                    onPickImage = { imagePickerLauncher.launch("image/*") },
                    onPickVideo = { videoPickerLauncher.launch("video/*") },
                    onPickAudio = { audioPickerLauncher.launch("audio/*") },
                    onRecordVoice = {
                        // ✨ ADDED: Check for permission before launching the voice recorder
                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
            }
        }
    }

    // ✨ NEW: Function to set up all the ActivityResultLaunchers
    private fun setupResultLaunchers(chatId: String) {
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { viewModel.sendMediaMessage(chatId, it, "image", this) }
        }

        videoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { viewModel.sendMediaMessage(chatId, it, "video", this) }
        }

        audioPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { viewModel.sendMediaMessage(chatId, it, "audio", this) }
        }

        voiceRecorderLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { viewModel.sendMediaMessage(chatId, it, "voice", this) }
        }

        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // We'll launch a voice recorder intent here later. For now, we'll use the audio picker as a placeholder.
                voiceRecorderLauncher.launch("audio/*")
            } else {
                // Handle the case where the user denies the permission
            }
        }
    }
}