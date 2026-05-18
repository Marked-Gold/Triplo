import korlibs.korge.view.Views

/**
 * Two haptic intensities the game asks for. Kept deliberately small so every platform can map
 * them onto whatever feedback primitives it has.
 */
enum class HapticFeedback {
    /** A short, light tick - selecting a board cell or a power-up. */
    TAP,

    /** A stronger, more noticeable pulse - a successful merge or firing a power-up. */
    SUCCESS,
}

/** Platform hook for playing a haptic effect. */
interface HapticProvider {
    fun play(feedback: HapticFeedback)
}

/**
 * Game-facing haptics facade. Call sites stay platform-agnostic.
 *
 * Android registers a real [android.os.Vibrator]-backed implementation and iOS uses UIKit's
 * feedback generators; platforms without haptics (JVM/JS) leave [provider] null, so every call
 * becomes a safe no-op. Same expect/actual pattern as [Ads] / [copyTextToClipboard].
 */
object Haptics {
    /** Set by [installPlatformHaptics]; null means "no haptics on this platform". */
    var provider: HapticProvider? = null

    /** Player setting, toggled from the pause menu. When false every haptic call is suppressed. */
    var enabled: Boolean = true

    /** A light tick for selecting a cell or a power-up. */
    fun tap() {
        if (enabled) provider?.play(HapticFeedback.TAP)
    }

    /** A stronger pulse for a successful merge or a fired power-up. */
    fun success() {
        if (enabled) provider?.play(HapticFeedback.SUCCESS)
    }
}

/**
 * Installs the platform-specific haptic provider. Real implementation on Android and iOS; no-op
 * on every other platform.
 */
expect fun Views.installPlatformHaptics()
