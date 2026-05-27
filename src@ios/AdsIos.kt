import korlibs.korge.view.Views
import kotlinx.coroutines.CompletableDeferred
import triploads.TriploAds

/**
 * iOS ad provider, backed by the Google Mobile Ads (AdMob) SDK through the Objective-C bridge in
 * native/ios/TriploAds.{h,m} (exposed to Kotlin via the GoogleMobileAdsBridge cinterop). The
 * bridge also drives the UMP consent flow and the App Tracking Transparency prompt.
 */
/**
 * iOS AdMob interstitial ad unit. Chosen at build time by the `useTestAdIds` flag in
 * build.gradle.kts and exposed through the generated AdConfig object.
 */
private val INTERSTITIAL_AD_UNIT = AdConfig.IOS_INTERSTITIAL_AD_UNIT

actual fun Views.installPlatformAds() {
    // The bridge runs UMP consent → ATT prompt → MobileAds.start. Until the completion block
    // fires, Ads.provider stays null so all preload / show calls are safe no-ops - mirroring how
    // the Android side gates ads behind consent.
    TriploAds.requestConsentAndStartAds(INTERSTITIAL_AD_UNIT) {
        val provider = IosInterstitialAds()
        Ads.provider = provider
        provider.preload()
    }
}

actual fun adsPrivacyOptionsRequired(): Boolean = TriploAds.privacyOptionsRequired()

actual fun adsPresentPrivacyOptions(onClose: () -> Unit) {
    TriploAds.presentPrivacyOptions { onClose() }
}

private class IosInterstitialAds : InterstitialAdProvider {
    override fun preload() {
        if (TriploAds.isInterstitialReady()) return
        TriploAds.loadInterstitial(INTERSTITIAL_AD_UNIT)
    }

    override suspend fun show() {
        if (!TriploAds.isInterstitialReady()) return
        val dismissed = CompletableDeferred<Unit>()
        TriploAds.showInterstitial { dismissed.complete(Unit) }
        dismissed.await()
    }
}
