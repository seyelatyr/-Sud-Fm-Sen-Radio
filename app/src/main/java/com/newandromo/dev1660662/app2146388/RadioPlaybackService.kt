package com.newandromo.dev1660662.app2146388

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object RadioStatus {
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering
    private val _message = MutableStateFlow("Prêt à écouter")
    val message: StateFlow<String> = _message

    internal fun update(playing: Boolean? = null, buffering: Boolean? = null, message: String? = null) {
        playing?.let { _isPlaying.value = it }
        buffering?.let { _isBuffering.value = it }
        message?.let { _message.value = it }
    }
}

class RadioPlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var shouldPlay = false

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true
            )
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_NETWORK)
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    RadioStatus.update(playing = isPlaying, message = if (isPlaying) "En direct" else if (shouldPlay) "Connexion…" else "Lecture arrêtée")
                }
                override fun onPlaybackStateChanged(state: Int) {
                    RadioStatus.update(buffering = state == Player.STATE_BUFFERING)
                }
                override fun onPlayerError(error: PlaybackException) {
                    RadioStatus.update(playing = false, buffering = true, message = "Reconnexion…")
                    if (shouldPlay) handler.postDelayed({ startStream() }, 4000)
                }
            })
        }
        mediaSession = MediaSession.Builder(this, player).build()
    }

    private fun startStream() {
        shouldPlay = true
        val item = MediaItem.Builder()
            .setUri(STREAM_URL)
            .setMediaId("sud-fm-live")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("SUD FM Sénégal")
                    .setArtist("En direct")
                    .setAlbumTitle("SUD FM Sen Radio")
                    .build()
            )
            .build()
        if (player.mediaItemCount == 0) player.setMediaItem(item)
        player.prepare()
        player.play()
        RadioStatus.update(buffering = true, message = "Connexion au direct…")
    }

    private fun stopStream() {
        shouldPlay = false
        handler.removeCallbacksAndMessages(null)
        player.stop()
        player.clearMediaItems()
        RadioStatus.update(playing = false, buffering = false, message = "Lecture arrêtée")
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> startStream()
            ACTION_STOP -> stopStream()
            ACTION_TOGGLE -> if (player.isPlaying || shouldPlay) stopStream() else startStream()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        mediaSession?.release()
        player.release()
        super.onDestroy()
    }

    companion object {
        const val STREAM_URL = "https://stream.zeno.fm/8hddc402zbruv"
        const val ACTION_PLAY = "com.sudfm.radio.PLAY"
        const val ACTION_STOP = "com.sudfm.radio.STOP"
        const val ACTION_TOGGLE = "com.sudfm.radio.TOGGLE"
    }
}
