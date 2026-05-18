import korlibs.korge.view.Views

/** The web build has no haptics - leaves [Haptics.provider] null so every call is a no-op. */
actual fun Views.installPlatformHaptics() {}
