package com.arua.lonyichat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.arua.lonyichat.ui.theme.LonyiChatTheme
import com.arua.lonyichat.ui.viewmodel.MessageViewModel

class MessageActivity : ComponentActivity() {

    private val viewModel: MessageViewModel by viewModels()

    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>
    private lateinit var videoPickerLauncher: ActivityResultLauncher<String>
    private lateinit var audioPickerLauncher: ActivityResultLauncher<String>
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    private var chatId: String? = null

    // ✨ NEW: Instance of our voice recorder manager
    private val voiceRecorderManager by lazy { VoiceRecorderManager(this) }


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

        setupResultLaunchers(chatId!!)

        setContent {
            LonyiChatTheme {
                MessageScreen(
                    chatId = chatId!!,
                    viewModel = viewModel,
                    otherUserName = otherUserName,
                    onBackPressed = { finish() },
                    onPickImage = { imagePickerLauncher.launch("image/*") },
                    onPickVideo = { videoPickerLauncher.launch("video/*") },
                    onPickAudio = { audioPickerLauncher.launch("audio/*") },
                    // ✨ MODIFIED: Pass the new recording handlers to the UI
                    onStartRecording = { handleStartRecording() },
                    onStopRecording = { handleStopRecording() },
                    onCancelRecording = { handleCancelRecording() }
                )
            }
        }
    }

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

        // ✨ MODIFIED: The permission launcher now just shows a toast. The user will tap the record button again if they grant permission.
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (!isGranted) {
                Toast.makeText(this, "Permission to record audio is required for this feature.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ✨ NEW: Function to handle starting the recording
    private fun handleStartRecording(): Boolean {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return voiceRecorderManager.startRecording()
        } else {
            // Request permission
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return false // Recording cannot start until permission is granted
        }
    }

    // ✨ NEW: Function to handle stopping the recording and sending the message
    private fun handleStopRecording() {
        val audioUri = voiceRecorderManager.stopRecording()
        audioUri?.let {
            chatId?.let { cid ->
                viewModel.sendMediaMessage(cid, it, "voice", this)
            }
        }
    }

    // ✨ NEW: Function to handle canceling the recording
    private fun handleCancelRecording() {
        voiceRecorderManager.cancelRecording()
    }
}