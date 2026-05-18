import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import korlibs.korge.view.Views
import korlibs.render.gameWindowAndroidContext

/**
 * Android haptics via the system [Vibrator]. Requires the VIBRATE permission (declared in
 * build.gradle.kts). Vibrator calls are thread-safe, so they run straight from the game thread.
 */
actual fun Views.installPlatformHaptics() {
    val context = gameWindow.gameWindowAndroidContext as? Context ?: return
    val vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    if (vibrator == null || !vibrator.hasVibrator()) return
    Haptics.provider = AndroidHaptics(vibrator)
}

private class AndroidHaptics(private val vibrator: Vibrator) : HapticProvider {
    override fun play(feedback: HapticFeedback) {
        when {
            // API 29+: predefined effects let the OS pick a crisp tick scaled to the device.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                val effectId = when (feedback) {
                    HapticFeedback.TAP -> VibrationEffect.EFFECT_TICK
                    HapticFeedback.SUCCESS -> VibrationEffect.EFFECT_HEAVY_CLICK
                }
                vibrator.vibrate(VibrationEffect.createPredefined(effectId))
            }
            // API 26-28: one-shot with an explicit duration and amplitude.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                val (durationMs, amplitude) = when (feedback) {
                    HapticFeedback.TAP -> 12L to 70
                    HapticFeedback.SUCCESS -> 40L to VibrationEffect.DEFAULT_AMPLITUDE
                }
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            }
            // API 23-25: the only option is a plain timed buzz.
            else -> {
                @Suppress("DEPRECATION")
                vibrator.vibrate(if (feedback == HapticFeedback.TAP) 12L else 40L)
            }
        }
    }
}
