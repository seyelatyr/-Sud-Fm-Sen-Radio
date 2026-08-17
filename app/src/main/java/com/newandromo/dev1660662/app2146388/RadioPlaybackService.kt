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
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
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

@UnstableApi
class RadioPlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var shouldPlay = false
    private var isRestarting = false
    private var reconnectAttempt = 0

    private val reconnectRunnable = Runnable {
        if (shouldPlay) restartStream("Reconnexion au direct…")
    }

    private val bufferingWatchdog = Runnable {
        if (shouldPlay && player.playbackState == Player.STATE_BUFFERING) {
            restartStream("Connexion lente — reconnexion…")
        }
    }

    override fun onCreate() {
        super.onCreate()

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("SUD-FM-Sen-Radio/2.0.1 Android")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setDefaultRequestProperties(
                mapOf(
                    "Icy-MetaData" to "1",
                    "Accept" to "*/*",
                    "Connection" to "keep-alive"
                )
            )

        val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(20))

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_NETWORK)
                repeatMode = Player.REPEAT_MODE_ONE

                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        RadioStatus.update(
                            playing = isPlaying,
                            message = when {
                                isPlaying -> "En direct"
                                shouldPlay -> "Connexion…"
                                else -> "Lecture arrêtée"
                            }
                        )
                        if (isPlaying) {
                            reconnectAttempt = 0
                            cancelReconnect()
                            cancelBufferingWatchdog()
                        }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> {
                                RadioStatus.update(buffering = true, message = "Connexion au direct…")
                                scheduleBufferingWatchdog()
                            }
                            Player.STATE_READY -> {
                                RadioStatus.update(buffering = false)
                                cancelReconnect()
                                cancelBufferingWatchdog()
                            }
                            Player.STATE_ENDED -> {
                                RadioStatus.update(playing = false, buffering = true, message = "Relance du direct…")
                                if (shouldPlay && !isRestarting) scheduleReconnect(350)
                            }
                            Player.STATE_IDLE -> {
                                RadioStatus.update(buffering = false)
                                if (shouldPlay && !isRestarting && player.playerError == null) scheduleReconnect(800)
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        RadioStatus.update(playing = false, buffering = true, message = "Reconnexion…")
                        if (shouldPlay) scheduleReconnect(nextRetryDelay())
                    }
                })
            }

        mediaSession = MediaSession.Builder(this, player).build()
    }

    private fun startStream() {
        shouldPlay = true
        reconnectAttempt = 0
        restartStream("Connexion au direct…")
    }

    private fun restartStream(status: String) {
        if (!shouldPlay || isRestarting) return
        isRestarting = true
        cancelReconnect()
        cancelBufferingWatchdog()

        try {
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(buildLiveItem(), true)
            player.prepare()
            player.playWhenReady = true
            RadioStatus.update(playing = false, buffering = true, message = status)
            scheduleBufferingWatchdog()
        } finally {
            isRestarting = false
        }
    }

    private fun buildLiveItem(): MediaItem = MediaItem.Builder()
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

    private fun scheduleReconnect(delayMs: Long) {
        if (!shouldPlay) return
        cancelReconnect()
        handler.postDelayed(reconnectRunnable, delayMs)
    }

    private fun nextRetryDelay(): Long {
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(6)
        return when (reconnectAttempt) {
            1 -> 500L
            2 -> 1_000L
            3 -> 1_500L
            4 -> 2_500L
            else -> 4_000L
        }
    }

    private fun scheduleBufferingWatchdog() {
        cancelBufferingWatchdog()
        handler.postDelayed(bufferingWatchdog, 18_000)
    }

    private fun cancelReconnect() {
        handler.removeCallbacks(reconnectRunnable)
    }

    private fun cancelBufferingWatchdog() {
        handler.removeCallbacks(bufferingWatchdog)
    }

    private fun stopStream() {
        shouldPlay = false
        reconnectAttempt = 0
        cancelReconnect()
        cancelBufferingWatchdog()
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
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        shouldPlay = false
        cancelReconnect()
        cancelBufferingWatchdog()
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
