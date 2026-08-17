package com.newandromo.dev1660662.app2146388

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

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
    private var lastPlayingAt = 0L
    private lateinit var connectivityManager: ConnectivityManager

    private val reconnectRunnable = Runnable {
        if (shouldPlay) restartStream("Reconnexion au direct…")
    }

    private val healthWatchdog = object : Runnable {
        override fun run() {
            if (!shouldPlay) return

            val now = System.currentTimeMillis()
            when {
                player.isPlaying -> lastPlayingAt = now
                player.playbackState == Player.STATE_BUFFERING && now - lastPlayingAt > 25_000L -> {
                    restartStream("Connexion lente — reconnexion…")
                }
                player.playbackState == Player.STATE_ENDED || player.playbackState == Player.STATE_IDLE -> {
                    restartStream("Relance du direct…")
                }
            }
            if (shouldPlay) handler.postDelayed(this, 5_000L)
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (shouldPlay && !player.isPlaying) {
                handler.postDelayed({
                    if (shouldPlay && !player.isPlaying) restartStream("Réseau retrouvé — reconnexion…")
                }, 600L)
            }
        }

        override fun onLost(network: Network) {
            if (shouldPlay) {
                RadioStatus.update(playing = false, buffering = true, message = "Réseau interrompu…")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // Flux radio continu : pas de timeout de lecture qui coupe un direct lors d'un trou réseau.
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .addNetworkInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "SUD-FM-Sen-Radio/2.0.3 Android")
                    .header("Accept", "audio/mpeg,audio/aac,*/*")
                    .header("Cache-Control", "no-cache")
                    .build()
                chain.proceed(request)
            }
            .build()

        val httpFactory = OkHttpDataSource.Factory(okHttpClient)
        val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(12))

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 90_000, 1_500, 3_000)
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
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

                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) {
                            lastPlayingAt = System.currentTimeMillis()
                            reconnectAttempt = 0
                            cancelReconnect()
                        }
                        RadioStatus.update(
                            playing = isPlaying,
                            buffering = !isPlaying && shouldPlay && playbackState == Player.STATE_BUFFERING,
                            message = when {
                                isPlaying -> "En direct"
                                shouldPlay -> "Connexion…"
                                else -> "Lecture arrêtée"
                            }
                        )
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> {
                                RadioStatus.update(buffering = true, message = "Connexion au direct…")
                            }
                            Player.STATE_READY -> {
                                RadioStatus.update(buffering = false)
                                if (shouldPlay && playWhenReady && !isPlaying) {
                                    handler.postDelayed({
                                        if (shouldPlay && playbackState == Player.STATE_READY && !isPlaying) play()
                                    }, 500L)
                                }
                            }
                            Player.STATE_ENDED -> {
                                RadioStatus.update(playing = false, buffering = true, message = "Relance du direct…")
                                if (shouldPlay && !isRestarting) scheduleReconnect(250L)
                            }
                            Player.STATE_IDLE -> {
                                if (shouldPlay && !isRestarting && playerError == null) scheduleReconnect(700L)
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

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        runCatching { connectivityManager.registerDefaultNetworkCallback(networkCallback) }
    }

    private fun startStream() {
        shouldPlay = true
        reconnectAttempt = 0
        lastPlayingAt = System.currentTimeMillis()
        restartStream("Connexion au direct…")
        startHealthWatchdog()
    }

    private fun restartStream(status: String) {
        if (!shouldPlay || isRestarting) return
        isRestarting = true
        cancelReconnect()

        try {
            // Ne pas appeler player.stop() ici : un bref arrêt peut faire quitter le service média
            // du premier plan. On remplace directement la source et on garde playWhenReady actif.
            player.playWhenReady = true
            player.setMediaItem(buildLiveItem(), true)
            player.prepare()
            RadioStatus.update(playing = false, buffering = true, message = status)
        } finally {
            isRestarting = false
        }
    }

    private fun buildLiveItem(): MediaItem {
        // Une URL légèrement unique force une nouvelle connexion réseau après une coupure CDN/Zeno.
        val uri = Uri.parse(STREAM_URL)
            .buildUpon()
            .appendQueryParameter("_", System.currentTimeMillis().toString())
            .build()

        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId("sud-fm-live")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("SUD FM Sénégal")
                    .setArtist("En direct")
                    .setAlbumTitle("SUD FM Sen Radio")
                    .build()
            )
            .build()
    }

    private fun scheduleReconnect(delayMs: Long) {
        if (!shouldPlay) return
        cancelReconnect()
        handler.postDelayed(reconnectRunnable, delayMs)
    }

    private fun nextRetryDelay(): Long {
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(7)
        return when (reconnectAttempt) {
            1 -> 300L
            2 -> 700L
            3 -> 1_200L
            4 -> 2_000L
            5 -> 3_000L
            else -> 5_000L
        }
    }

    private fun startHealthWatchdog() {
        handler.removeCallbacks(healthWatchdog)
        handler.postDelayed(healthWatchdog, 5_000L)
    }

    private fun cancelReconnect() {
        handler.removeCallbacks(reconnectRunnable)
    }

    private fun stopStream() {
        shouldPlay = false
        reconnectAttempt = 0
        cancelReconnect()
        handler.removeCallbacks(healthWatchdog)
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
        handler.removeCallbacks(healthWatchdog)
        if (::connectivityManager.isInitialized) {
            runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        }
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
