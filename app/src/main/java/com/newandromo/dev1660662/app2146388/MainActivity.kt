package com.newandromo.dev1660662.app2146388

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
import androidx.compose.foundation.layout.weight
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
import androidx.compose.runtime.remember
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
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
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
        MobileAds.initialize(this) { adsReady = true }
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
                                    val action = if (isPlaying || isBuffering) RadioPlaybackService.ACTION_STOP else RadioPlaybackService.ACTION_PLAY
                                    val intent = Intent(context, RadioPlaybackService::class.java).setAction(action)
                                    if (action == RadioPlaybackService.ACTION_PLAY) androidx.core.content.ContextCompat.startForegroundService(context, intent)
                                    else context.startService(intent)
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
                        AdaptiveAdMobBanner()
                    }
                }
            }
        }
    }
}

@Composable
private fun AdaptiveAdMobBanner() {
    val candidates = remember { listOf(
        "ca-app-pub-0241595114429536/1090661138",
        "ca-app-pub-0241595114429536/1652933768",
        "ca-app-pub-0241595114429536/3150095684"
    ) }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(10.dp)),
        factory = { ctx ->
            FrameLayout(ctx).apply {
                fun tryLoad(index: Int) {
                    if (index >= candidates.size) return
                    removeAllViews()
                    val ad = AdView(ctx).apply {
                        adUnitId = candidates[index]
                        setAdSize(AdSize.BANNER)
                        adListener = object : AdListener() {
                            override fun onAdFailedToLoad(error: LoadAdError) { tryLoad(index + 1) }
                        }
                    }
                    addView(ad, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
                    ad.loadAd(AdRequest.Builder().build())
                }
                tryLoad(0)
            }
        }
    )
}
