import korlibs.korge.view.Views
import korlibs.logger.Logger

/**
 * Platform hook for showing interstitial ads.
 *
 * Android registers a real Google Mobile Ads (AdMob) implementation. Platforms without an ad SDK
 * wired up (JVM/JS/iOS) leave [Ads.provider] null, so every call becomes a safe no-op.
 */
interface InterstitialAdProvider {
    /** Starts loading the next interstitial so it is ready to show later. Safe to call repeatedly. */
    fun preload()

    /**
     * Shows a loaded interstitial, suspending until the user dismisses it.
     * Returns immediately (without showing anything) if no ad is loaded yet.
     */
    suspend fun show()
}

/** Game-facing ads facade. Call sites stay platform-agnostic. */
object Ads {
    private val logger = Logger("Ads")

    /** Set by [installPlatformAds]; null means "no ads on this platform / not loaded yet". */
    var provider: InterstitialAdProvider? = null

    fun preloadInterstitial() {
        val p = provider ?: return
        logger.debug { "Preloading interstitial" }
        p.preload()
    }

    /** Shows an interstitial if one is ready, then preloads the next. No-op when no provider. */
    suspend fun showInterstitial() {
        val p = provider
        if (p == null) {
            logger.debug { "No ad provider registered - skipping interstitial" }
            return
        }
        logger.debug { "Showing interstitial" }
        p.show()
        p.preload()
    }
}

/**
 * Installs the platform-specific ad provider and kicks off the first preload.
 * Real implementation on Android; no-op on every other platform.
 */
expect fun Views.installPlatformAds()

/**
 * True when GDPR / US-state privacy laws require us to expose a "manage consent" affordance
 * somewhere in the app. False when the user is outside those jurisdictions; the pause-menu
 * "AD PERMISSIONS" button is hidden in that case to avoid showing a no-op control.
 */
expect fun adsPrivacyOptionsRequired(): Boolean

/**
 * Shows the UMP privacy-options form so users can revoke or change their previous ad-consent
 * choices. The callback fires when the form is dismissed (or immediately on platforms without
 * an ad SDK wired up).
 */
expect fun adsPresentPrivacyOptions(onClose: () -> Unit)
