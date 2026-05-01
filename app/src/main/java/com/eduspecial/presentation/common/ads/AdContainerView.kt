package com.eduspecial.presentation.common.ads

import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.eduspecial.R
import com.eduspecial.core.ads.AdMobConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

@Composable
fun AdContainerView(
    slotKey: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var nativeAd by remember(slotKey) { mutableStateOf<NativeAd?>(null) }
    var loadFailed by remember(slotKey) { mutableStateOf(false) }

    LaunchedEffect(slotKey) {
        if (!AdMobConfig.isNativeEnabled || nativeAd != null || loadFailed) return@LaunchedEffect

        AdLoader.Builder(context, AdMobConfig.nativeAdUnitId)
            .forNativeAd { loadedAd ->
                nativeAd?.destroy()
                nativeAd = loadedAd
                loadFailed = false
            }
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_LEFT)
                    .build()
            )
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    nativeAd?.destroy()
                    nativeAd = null
                    loadFailed = true
                }
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    DisposableEffect(slotKey) {
        onDispose {
            nativeAd?.destroy()
        }
    }

    val ad = nativeAd ?: return

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            LayoutInflater.from(viewContext).inflate(R.layout.view_native_ad, null, false)
        },
        update = { root ->
            NativeAdBinder.bind(root.findViewById<NativeAdView>(R.id.native_ad_view), ad)
        }
    )
}
