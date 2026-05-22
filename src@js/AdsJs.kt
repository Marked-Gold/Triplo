import korlibs.korge.view.Views

/** Web/JS has no ad SDK wired up - ads are a no-op here. */
actual fun Views.installPlatformAds() {}

actual fun adsPrivacyOptionsRequired(): Boolean = false

actual fun adsPresentPrivacyOptions(onClose: () -> Unit) { onClose() }
