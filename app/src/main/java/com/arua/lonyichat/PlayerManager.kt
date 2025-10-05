package com.arua.lonyichat.data

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem as Media3MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory // FIX: Correct Factory Type
import androidx.media3.common.util.UnstableApi // FIX: Opt-In Annotation
import com.arua.lonyichat.LonyiChatApp

// FIX: Apply the Opt-In annotation for unstable API usage
@UnstableApi
/**
 * Manages the ExoPlayer instances for the Vertical Video Feed (Church Vibes).
 * It uses the 'Player Pooling' pattern to allow for seamless switching between videos
 * by keeping the current and next players ready and uses caching for instant replay.
 */
class PlayerManager(private val context: Context) {

    // Store players in a map where the key is the index of the video (page index)
    private val playerMap = mutableMapOf<Int, ExoPlayer>()

    // FIX: Get the global Caching DataSource Factory and wrap it in DefaultMediaSourceFactory
    private val mediaSourceFactory = DefaultMediaSourceFactory(LonyiChatApp.getCacheDataSourceFactory())


    /**
     * Retrieves an existing player for the given index or creates a new one.
     * Initializes the player with the media item and uses caching.
     */
    fun getPlayer(index: Int, mediaItem: MediaItem): ExoPlayer {
        return playerMap.getOrPut(index) {
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory) // FIX: Pass the MediaSource.Factory
                .build().apply {
                    val media3Item = Media3MediaItem.fromUri("${ApiService.BASE_URL}/uploads/videos/${mediaItem.url.split("/").last()}")
                    setMediaItem(media3Item)
                    prepare()
                    repeatMode = Player.REPEAT_MODE_ONE
                    playWhenReady = false // Start paused, let the manager handle playback
                }
        }
    }

    /**
     * Updates playback state based on the current page index.
     */
    fun updatePlayers(currentPage: Int, totalItems: Int) {
        val keysToDispose = mutableListOf<Int>()

        playerMap.forEach { (index, player) ->
            when {
                // If it's the current page, ensure it plays
                index == currentPage -> {
                    if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
                        player.play()
                    }
                }
                // If it's outside the preload range (currentPage - 1 to currentPage + 1)
                index < currentPage - 1 || index > currentPage + 1 -> {
                    keysToDispose.add(index)
                    player.release()
                }
                // If it's a neighboring page (preload), ensure it's paused
                else -> {
                    player.pause()
                }
            }
        }

        keysToDispose.forEach { playerMap.remove(it) }
    }

    /**
     * Must be called when the main screen composable is disposed.
     */
    fun releaseAllPlayers() {
        playerMap.values.forEach { it.release() }
        playerMap.clear()
    }
}