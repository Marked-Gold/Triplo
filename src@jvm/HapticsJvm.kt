import korlibs.korge.view.Views

/** Desktop has no haptics - leaves [Haptics.provider] null so every call is a no-op. */
actual fun Views.installPlatformHaptics() {}
