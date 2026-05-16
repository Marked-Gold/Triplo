import korlibs.korge.view.Views
import kotlinx.coroutines.CompletableDeferred
import trilliumads.TrilliumAds

/**
 * iOS ad provider, backed by the Google Mobile Ads (AdMob) SDK through the Objective-C bridge in
 * native/ios/TrilliumAds.{h,m} (exposed to Kotlin via the GoogleMobileAdsBridge cinterop).
 *
 * Google's official sample interstitial ad unit for iOS - always serves test ads, so it is safe
 * while developing. Replace with the real AdMob ad unit id before a production release, and update
 * GADApplicationIdentifier in the patched Info.plist (see the patchIosProject task) to match.
 */
private const val TEST_INTERSTITIAL_AD_UNIT = "ca-app-pub-3940256099942544/4411468910"

actual fun Views.installPlatformAds() {
    TrilliumAds.initializeSdk()
    val provider = IosInterstitialAds()
    Ads.provider = provider
    provider.preload()
}

private class IosInterstitialAds : InterstitialAdProvider {
    override fun preload() {
        if (TrilliumAds.isInterstitialReady()) return
        TrilliumAds.loadInterstitial(TEST_INTERSTITIAL_AD_UNIT)
    }

    override suspend fun show() {
        if (!TrilliumAds.isInterstitialReady()) return
        val dismissed = CompletableDeferred<Unit>()
        TrilliumAds.showInterstitial { dismissed.complete(Unit) }
        dismissed.await()
    }
}
