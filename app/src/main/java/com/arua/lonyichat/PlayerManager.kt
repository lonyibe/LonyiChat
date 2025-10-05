package com.arua.lonyichat.data

import android.content.Context
import androidx.media3.common.MediaItem as Media3MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.arua.lonyichat.LonyiChatApp

@UnstableApi
class PlayerManager(private val context: Context) {

    // The key is now the unique ID of the media item (a String) instead of its position.
    private val playerMap = mutableMapOf<String, ExoPlayer>()

    private val mediaSourceFactory = DefaultMediaSourceFactory(LonyiChatApp.getCacheDataSourceFactory())

    /**
     * Retrieves an existing player for the given media item or creates a new one.
     * The media item's unique ID is used as the key for reliable player management.
     */
    fun getPlayer(mediaItem: MediaItem): ExoPlayer {
        return playerMap.getOrPut(mediaItem.id) { // Use the stable mediaItem.id as the key
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build().apply {
                    val media3Item = Media3MediaItem.fromUri("${ApiService.BASE_URL}/uploads/videos/${mediaItem.url.split("/").last()}")
                    setMediaItem(media3Item)
                    prepare()
                    repeatMode = Player.REPEAT_MODE_ONE
                    playWhenReady = false
                }
        }
    }

    /**
     * This is the core of the fix. It now intelligently manages players based on a sliding
     * window of videos around the current one, using their unique IDs. This prevents crashes
     * when the list order changes.
     */
    fun updatePlayers(currentPage: Int, mediaItems: List<MediaItem>) {
        if (mediaItems.isEmpty()) return

        val currentMediaId = mediaItems[currentPage].id
        // Preload the players for the videos immediately before and after the current one.
        val preloadIds = setOf(
            mediaItems.getOrNull(currentPage - 1)?.id,
            currentMediaId,
            mediaItems.getOrNull(currentPage + 1)?.id
        ).filterNotNull()

        // Release players that are no longer in the preload window to conserve memory.
        val playersToRelease = playerMap.filterKeys { it !in preloadIds }
        playersToRelease.forEach { (id, player) ->
            player.release()
            playerMap.remove(id)
        }

        // Ensure only the current video is playing and the preloaded ones are paused.
        playerMap.forEach { (id, player) ->
            if (id == currentMediaId) {
                if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
                    player.play()
                }
            } else {
                player.pause()
            }
        }
    }

    /**
     * Pauses all managed player instances.
     * This is useful for when the media screen is no longer visible but the app is still in the foreground.
     */
    fun pauseAllPlayers() {
        playerMap.values.forEach { it.pause() }
    }


    fun releaseAllPlayers() {
        playerMap.values.forEach { it.release() }
        playerMap.clear()
    }
}

