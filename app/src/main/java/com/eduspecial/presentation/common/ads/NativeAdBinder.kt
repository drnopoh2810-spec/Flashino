package com.eduspecial.presentation.common.ads

import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.eduspecial.R
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

object NativeAdBinder {
    fun bind(nativeAdView: NativeAdView, nativeAd: NativeAd) {
        val headline = nativeAdView.findViewById<TextView>(R.id.ad_headline)
        val body = nativeAdView.findViewById<TextView>(R.id.ad_body)
        val advertiser = nativeAdView.findViewById<TextView>(R.id.ad_advertiser)
        val media = nativeAdView.findViewById<MediaView>(R.id.ad_media)
        val cta = nativeAdView.findViewById<Button>(R.id.ad_call_to_action)
        val icon = nativeAdView.findViewById<ImageView>(R.id.ad_app_icon)

        nativeAdView.headlineView = headline
        nativeAdView.bodyView = body
        nativeAdView.advertiserView = advertiser
        nativeAdView.mediaView = media
        nativeAdView.callToActionView = cta
        nativeAdView.iconView = icon

        headline.text = nativeAd.headline

        body.text = nativeAd.body
        body.visibility = if (nativeAd.body.isNullOrBlank()) View.GONE else View.VISIBLE

        advertiser.text = nativeAd.advertiser
        advertiser.visibility = if (nativeAd.advertiser.isNullOrBlank()) View.GONE else View.VISIBLE

        val iconDrawable = nativeAd.icon?.drawable
        icon.setImageDrawable(iconDrawable)
        icon.visibility = if (iconDrawable == null) View.GONE else View.VISIBLE

        media.mediaContent = nativeAd.mediaContent
        media.visibility = if (nativeAd.mediaContent == null) View.GONE else View.VISIBLE

        cta.text = nativeAd.callToAction
        cta.visibility = if (nativeAd.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE

        nativeAdView.setNativeAd(nativeAd)
    }
}
