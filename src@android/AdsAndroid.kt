import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import korlibs.korge.view.Views
import korlibs.render.gameWindowAndroidContext
import kotlinx.coroutines.CompletableDeferred

/**
 * Google's official sample interstitial ad unit. Always serves test ads, so it is safe to ship
 * while developing. Replace with the real AdMob ad unit id before a production release, and update
 * the AdMob application id in build.gradle.kts to match.
 */
private const val TEST_INTERSTITIAL_AD_UNIT = "ca-app-pub-3940256099942544/1033173712"

actual fun Views.installPlatformAds() {
    val activity = gameWindow.gameWindowAndroidContext as? Activity ?: return
    MobileAds.initialize(activity)
    val provider = AndroidInterstitialAds(activity)
    Ads.provider = provider
    provider.preload()
}

private class AndroidInterstitialAds(private val activity: Activity) : InterstitialAdProvider {
    private var loaded: InterstitialAd? = null
    private var loading = false

    override fun preload() {
        if (loaded != null || loading) return
        loading = true
        activity.runOnUiThread {
            InterstitialAd.load(
                activity,
                TEST_INTERSTITIAL_AD_UNIT,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        loaded = ad
                        loading = false
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        loaded = null
                        loading = false
                    }
                },
            )
        }
    }

    override suspend fun show() {
        val ad = loaded ?: return
        loaded = null
        val dismissed = CompletableDeferred<Unit>()
        activity.runOnUiThread {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    dismissed.complete(Unit)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    dismissed.complete(Unit)
                }
            }
            ad.show(activity)
        }
        dismissed.await()
    }
}
