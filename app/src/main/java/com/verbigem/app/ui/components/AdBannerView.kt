package com.verbigem.app.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.verbigem.app.BuildConfig
import com.verbigem.app.R
import com.verbigem.app.ads.AdsConsent
import com.verbigem.app.ui.theme.VerbigemTheme

private const val TAG = "AdBannerView"

/**
 * Baner reklamowy na dole ekranu Tłumacza. Widoczny TYLKO dla kont Free.
 *
 * ⚠️ Kolejność widoczności: baner nic nie ładuje, póki `AdsConsent.adsReady` nie
 * będzie `true` — czyli póki UMP nie powie „można" i SDK nie zostanie zainicjowany.
 * To nie jest ostrożność na wyrost: AdMob potrafi zamknąć konto za request
 * reklamowy wysłany przed uzyskaniem zgody w EOG.
 *
 * Do czasu, aż to nastąpi (i gdy reklama się nie wczyta), zostaje poprzedni
 * placeholder — puste miejsce z etykietą, bez migotania i bez skakania layoutu.
 */
@Composable
fun AdBannerView(
    isPro: Boolean,
    modifier: Modifier = Modifier
) {
    if (isPro) return

    val adsReady by AdsConsent.adsReady.collectAsState()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.ad_banner_label),
            color = VerbigemTheme.colors.muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        if (adsReady) {
            RealBanner(modifier = Modifier.fillMaxWidth())
        } else {
            AdPlaceholder(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RealBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Podgląd w Android Studio nie ma SDK ani sieci — bez tego każdy preview rzuca.
    if (LocalInspectionMode.current) {
        AdPlaceholder(modifier = modifier)
        return
    }

    // Baner adaptacyjny: wysokość dobiera serwer (50–150 dp), szerokość = ekran.
    // `screenWidthDp` zamiast 360 na sztywno — inaczej na tablecie baner byłby wąski.
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val adSize = remember(context, screenWidthDp) {
        AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, screenWidthDp)
    }
    // Przełącznik z Profilu → „Diagnostyka reklam". Testowa jednostka Google
    // wypełnia się zawsze, więc jeśli zadziała ona, a nasza nie — winny jest slot
    // albo konto AdMob, nie kod aplikacji.
    val testAds by AdsConsent.testAdsEnabled.collectAsState()
    val unitId = remember(testAds) {
        if (testAds) AdsConsent.TEST_BANNER_UNIT_ID else BuildConfig.ADMOB_BANNER_UNIT_ID
    }
    // ⚠️ AndroidView MUSI być komponowany zawsze — `loadAd` jest wołane w środku
    // factory, więc każde sterowanie widocznością „z zewnątrz" (np. `if (loaded)`)
    // odcina request i baner nigdy nie dostaje odpowiedzi. Stan „wczytano" jest
    // tylko w logach: AdView bez reklamy nie ma treści, więc nie ma co ukrywać.
    // `key(unitId)` — po przełączeniu na reklamy testowe AdView musi powstać
    // OD NOWA, inaczej `factory` nie odpali się ponownie i zobaczymy starą
    // jednostkę (albo pustkę) do końca sesji.
    key(unitId) {
        AndroidView(
            modifier = modifier
                .fillMaxWidth()
                .height(adSize.height.dp),
            factory = { ctx ->
                AdView(ctx).apply {
                    val request = BannerAdRequest.Builder(unitId, adSize).build()
                    Log.i(TAG, "loadAd: unit=$unitId size=${adSize.width}x${adSize.height}")
                    loadAd(
                        request,
                        object : AdLoadCallback<BannerAd> {
                            override fun onAdLoaded(ad: BannerAd) {
                                Log.i(TAG, "Banner loaded $unitId ${adSize.width}x${adSize.height}")
                                AdsConsent.reportBannerLoaded()
                            }

                            override fun onAdFailedToLoad(error: LoadAdError) {
                                // Ciche logi, nie crash: brak sieci, no-fill (kod 3) albo
                                // świeży slot bez kampanii to normalny stan, nie błąd.
                                // Kod trafia do karty „Diagnostyka reklam" w Profilu.
                                Log.w(
                                    TAG,
                                    "Banner failed: code=${error.code} " +
                                        "msg=${error.message}",
                                )
                                AdsConsent.reportBannerError(
                                    error.code.toString(),
                                    error.message
                                )
                            }
                        }
                    )
                }
            },
            onRelease = { it.destroy() }
        )
    }
}

/** Placeholder sprzed wczytania (i po błędzie) — ten sam wygląd co stara atrapa. */
@Composable
private fun AdPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(VerbigemTheme.colors.surface)
            .border(1.dp, VerbigemTheme.colors.border, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.ad_banner_text),
            color = VerbigemTheme.colors.muted,
            fontSize = 12.sp
        )
    }
}
