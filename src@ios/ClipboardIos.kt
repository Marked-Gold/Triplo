import korlibs.korge.view.Views
import platform.UIKit.UIPasteboard

/** iOS clipboard via UIPasteboard. */
actual fun Views.copyTextToClipboard(text: String) {
    UIPasteboard.generalPasteboard.string = text
}
