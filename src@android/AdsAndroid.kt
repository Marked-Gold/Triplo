import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import korlibs.korge.view.Views
import korlibs.render.gameWindowAndroidContext
import kotlinx.coroutines.CompletableDeferred

/** Production AdMob interstitial ad unit for the Android app. */
private const val INTERSTITIAL_AD_UNIT = "ca-app-pub-7742910323184344/7551421648"

/**
 * Devices that receive AdMob *test* ads instead of live ones. Test ads may be viewed and tapped
 * freely without risking the account for invalid traffic, so every developer's device belongs
 * here. A device's hashed id is printed in logcat as
 * `Use RequestConfiguration.Builder().setTestDeviceIds(Arrays.asList("..."))`.
 * Listing an id only affects that device; it is harmless to keep in release builds.
 */
private val TEST_DEVICE_IDS = listOf(
    "171B20DDBB9BCDFC01B463E631C10A6A", // Mark's Pixel 10
)

private var adsStarted = false

actual fun Views.installPlatformAds() {
    val activity = gameWindow.gameWindowAndroidContext as? Activity ?: return
    // Gather user consent via the User Messaging Platform before requesting ads. This is required
    // for users in the EEA/UK (GDPR) and for US state privacy laws; the consent messages themselves
    // are configured in the AdMob console. requestConsentInfoUpdate refreshes the consent state,
    // any required form is then shown, and canRequestAds() finally gates the Mobile Ads start-up.
    val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
    consentInformation.requestConsentInfoUpdate(
        activity,
        ConsentRequestParameters.Builder().build(),
        {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                if (formError != null) Napier.w("Ad consent form error: ${formError.message}")
                if (consentInformation.canRequestAds()) startAds(activity)
            }
        },
        { requestError ->
            Napier.w("Ad consent update failed: ${requestError.message}")
            // canRequestAds() still reflects any previously stored consent.
            if (consentInformation.canRequestAds()) startAds(activity)
        },
    )
}

/** Starts the Mobile Ads SDK and registers the interstitial provider. Runs at most once. */
private fun startAds(activity: Activity) {
    if (adsStarted) return
    adsStarted = true
    MobileAds.setRequestConfiguration(
        RequestConfiguration.Builder().setTestDeviceIds(TEST_DEVICE_IDS).build(),
    )
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
                INTERSTITIAL_AD_UNIT,
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
