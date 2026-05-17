import korlibs.korge.view.Views

/**
 * Copies [text] to the system clipboard.
 *
 * KorGE 6's `GameWindow.clipboardWrite` is an unimplemented no-op on Android, so each platform
 * provides its own implementation here (same expect/actual pattern as [installPlatformAds]).
 */
expect fun Views.copyTextToClipboard(text: String)
