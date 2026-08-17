package com.newandromo.dev1660662.app2146388

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

class MainActivity : ComponentActivity() {
    private var adsReady by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestConsentAndInitAds()
        setContent { SudFmApp(adsReady = adsReady) }
    }

    private fun requestConsentAndInitAds() {
        val consentInformation = UserMessagingPlatform.getConsentInformation(this)
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) {
                    if (consentInformation.canRequestAds()) initializeAds()
                }
                if (consentInformation.canRequestAds()) initializeAds()
            },
            { if (consentInformation.canRequestAds()) initializeAds() }
        )
    }

    private fun initializeAds() {
        if (adsReady) return
        MobileAds.initialize(this) {
            adsReady = true
            RadioAdManager.preloadInterstitial(this)
        }
    }
}

object RadioAdManager {
    const val BANNER_ID = "ca-app-pub-0241595114429536/9623468443"
    private const val INTERSTITIAL_ID = "ca-app-pub-0241595114429536/3150095684"
    private const val INTERSTITIAL_MIN_INTERVAL_MS = 5 * 60 * 1000L

    private var interstitial: InterstitialAd? = null
    private var loading = false
    private var lastInterstitialShownAt = 0L

    fun preloadInterstitial(context: Context) {
        if (loading || interstitial != null) return
        loading = true
        InterstitialAd.load(
            context,
            INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loading = false
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    interstitial = null
                }
            }
        )
    }

    /**
     * L'interstitiel est affiché uniquement lors d'une vraie coupure logique :
     * l'utilisateur vient d'appuyer sur STOP. Il n'est jamais déclenché au lancement,
     * en arrière-plan ou pendant l'écoute. Un délai de 5 minutes évite les répétitions.
     */
    fun onNaturalStop(activity: Activity) {
        val now = System.currentTimeMillis()
        if (lastInterstitialShownAt != 0L && now - lastInterstitialShownAt < INTERSTITIAL_MIN_INTERVAL_MS) {
            preloadInterstitial(activity)
            return
        }

        val ad = interstitial ?: run {
            preloadInterstitial(activity)
            return
        }

        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                lastInterstitialShownAt = System.currentTimeMillis()
            }

            override fun onAdDismissedFullScreenContent() {
                preloadInterstitial(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                preloadInterstitial(activity)
            }
        }
        ad.show(activity)
    }
}

@Composable
private fun SudFmApp(adsReady: Boolean) {
    val context = LocalContext.current
    val isPlaying by RadioStatus.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by RadioStatus.isBuffering.collectAsStateWithLifecycle()
    val message by RadioStatus.message.collectAsStateWithLifecycle()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = ComposeColor(0xFFF8F8F8)) {
            Column(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).background(ComposeColor(0xFFB00020)).padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("SUD FM", color = ComposeColor.White, fontSize = 48.sp, fontWeight = FontWeight.Black)
                        Text("SEN RADIO", color = ComposeColor.White.copy(alpha = .88f), fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                        Spacer(Modifier.height(34.dp))

                        Box(contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(190.dp)) {
                                drawCircle(ComposeColor.White.copy(alpha = .18f))
                                drawCircle(ComposeColor.White.copy(alpha = .32f), style = Stroke(width = 4.dp.toPx()))
                                if (isPlaying) {
                                    val bars = listOf(.32f, .58f, .85f, .50f, .72f, .40f, .66f)
                                    val gap = size.width / 11f
                                    bars.forEachIndexed { i, h ->
                                        val x = gap * (i + 2)
                                        val half = size.height * h * .20f
                                        drawLine(ComposeColor.White, start = androidx.compose.ui.geometry.Offset(x, center.y - half), end = androidx.compose.ui.geometry.Offset(x, center.y + half), strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
                                    }
                                }
                            }
                            Button(
                                onClick = {
                                    val stopping = isPlaying || isBuffering
                                    val action = if (stopping) RadioPlaybackService.ACTION_STOP else RadioPlaybackService.ACTION_PLAY
                                    val intent = Intent(context, RadioPlaybackService::class.java).setAction(action)

                                    // L'utilisateur déclenche la lecture pendant que l'Activity est visible.
                                    // On démarre donc le MediaSessionService normalement. Media3 le promeut
                                    // automatiquement en service de premier plan dès que la lecture commence.
                                    // Cela évite le crash Android "ForegroundServiceDidNotStartInTimeException"
                                    // lorsque le flux radio met quelques secondes à démarrer.
                                    context.startService(intent)

                                    if (action == RadioPlaybackService.ACTION_STOP) {
                                        (context as? Activity)?.let { RadioAdManager.onNaturalStop(it) }
                                    }
                                },
                                modifier = Modifier.size(112.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor.White, contentColor = ComposeColor(0xFFB00020))
                            ) {
                                if (isBuffering && !isPlaying) CircularProgressIndicator(modifier = Modifier.size(36.dp), color = ComposeColor(0xFFB00020), strokeWidth = 4.dp)
                                else Text(if (isPlaying) "❚❚" else "▶", fontSize = 36.sp, fontWeight = FontWeight.Black)
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        Text(if (isPlaying) "EN DIRECT" else message.uppercase(), color = ComposeColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("SUD FM Sénégal", color = ComposeColor.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Votre radio, partout avec vous", color = ComposeColor.White.copy(alpha = .82f), fontSize = 14.sp)
                    }
                }

                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (isPlaying) "La radio continue même lorsque l’écran est éteint ou lorsque vous utilisez une autre application."
                        else "Appuyez sur Lecture pour écouter SUD FM en direct.",
                        textAlign = TextAlign.Center,
                        color = ComposeColor(0xFF555555),
                        fontSize = 13.sp
                    )
                    if (adsReady) {
                        Spacer(Modifier.height(12.dp))
                        SudFmAdMobBanner()
                    }
                }
            }
        }
    }
}

@Composable
private fun SudFmAdMobBanner() {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(10.dp)),
        factory = { ctx ->
            FrameLayout(ctx).apply {
                val ad = AdView(ctx).apply {
                    adUnitId = RadioAdManager.BANNER_ID
                    setAdSize(AdSize.BANNER)
                    loadAd(AdRequest.Builder().build())
                }
                addView(ad, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
            }
        }
    )
}
