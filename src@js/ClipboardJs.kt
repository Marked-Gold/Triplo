import korlibs.korge.view.Views

/** Web/JS clipboard via the browser Clipboard API. */
actual fun Views.copyTextToClipboard(text: String) {
    js("navigator.clipboard && navigator.clipboard.writeText(text)")
    Unit
}
