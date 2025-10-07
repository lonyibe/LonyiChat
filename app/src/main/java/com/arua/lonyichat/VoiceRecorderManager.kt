package com.arua.lonyichat

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class VoiceRecorderManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null

    // Starts the recording process
    fun startRecording(): Boolean {
        // Create a file to save the recording
        audioFile = createAudioFile()
        if (audioFile == null) {
            return false // Return false if file creation fails
        }

        // Initialize MediaRecorder
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mediaRecorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            // FIX: Switched to 3GPP/AMR_NB for maximum stability/compatibility with voice recording
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(audioFile?.absolutePath)

            return try {
                prepare()
                start()
                true // Recording started successfully
            } catch (e: IOException) {
                e.printStackTrace()
                // Clean up if preparation fails
                releaseRecorder()
                deleteCurrentFile() // ADDED: Robust file cleanup on failure
                false
            } catch (e: IllegalStateException) {
                e.printStackTrace()
                // Clean up if start fails
                releaseRecorder()
                deleteCurrentFile() // ADDED: Robust file cleanup on failure
                false
            }
        }
        return false
    }

    // Stops the recording and returns the Uri of the saved file
    fun stopRecording(): Uri? {
        return try {
            mediaRecorder?.stop()
            releaseRecorder()

            val finalFile = audioFile // ADDED: Hold reference to the file before nulling it

            // ADDED START: Robust file validation
            if (finalFile != null && finalFile.exists() && finalFile.length() > 0) {
                Log.d("VoiceRecorder", "Recording stopped successfully. File size: ${finalFile.length()} bytes.")
                return finalFile.toUri()
            } else {
                Log.e("VoiceRecorder", "Recording stopped but file is missing or empty. Path: ${finalFile?.absolutePath}, Exists: ${finalFile?.exists()}, Size: ${finalFile?.length()}.")
                deleteCurrentFile() // Delete corrupted/empty file
                return null
            }
            // ADDED END
        } catch (e: IllegalStateException) {
            // This can happen if stop is called in an invalid state
            e.printStackTrace()
            releaseRecorder() // Still try to release
            deleteCurrentFile() // The file might be corrupted
            null
        }
    }


    // Cancels the recording and deletes the file
    fun cancelRecording() {
        try {
            mediaRecorder?.stop()
        } catch (e: IllegalStateException) {
            // It might have already been stopped or not started
        }
        releaseRecorder()
        deleteCurrentFile()
    }

    // Releases MediaRecorder resources
    private fun releaseRecorder() {
        mediaRecorder?.release()
        mediaRecorder = null
    }

    // Deletes the audio file if it exists
    private fun deleteCurrentFile() {
        audioFile?.let {
            if (it.exists()) {
                it.delete()
            }
        }
        audioFile = null
    }

    // Creates a new audio file in the app's cache directory
    private fun createAudioFile(): File? {
        return try {
            val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val storageDir: File? = context.cacheDir
            File.createTempFile(
                "VOICE_${timeStamp}_",
                ".3gp", // FIX: Changed extension to .3gp to match THREE_GPP format
                storageDir
            )
        } catch (ex: IOException) {
            // Error occurred while creating the File
            ex.printStackTrace()
            null
        }
    }
}