import korlibs.korge.view.Views
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

/**
 * iOS haptics via UIKit's feedback generators. KorGE's iOS game loop runs on the main thread,
 * so these calls (which UIKit requires on the main thread) are safe from game code.
 */
actual fun Views.installPlatformHaptics() {
    Haptics.provider = IosHaptics()
}

private class IosHaptics : HapticProvider {
    private val lightImpact = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val notification = UINotificationFeedbackGenerator()

    override fun play(feedback: HapticFeedback) {
        when (feedback) {
            HapticFeedback.TAP -> {
                lightImpact.prepare()
                lightImpact.impactOccurred()
            }
            HapticFeedback.SUCCESS -> {
                notification.prepare()
                notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
            }
        }
    }
}
