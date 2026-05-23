# CLAUDE.md

Project notes for working on **Triplo** (a KorGE 6.0 game). See `README.md` for
the full overview, build commands, and the AdMob ads setup.

## Build essentials

- KorGE 6 needs **JDK 21**; it is not on PATH, so prefix Gradle calls:
  `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew <task>`
- Android `adb` is not on PATH either — full path:
  `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`
- Android application id (package): `com.allmeatgames.triplo`

## Debugging a crash on a connected Android device

When the app crashes on the phone, pull the logs with `adb`:

```bash
ADB=/opt/homebrew/share/android-commandlinetools/platform-tools/adb

# Confirm the device is connected and authorized
$ADB devices -l

# Dump the dedicated crash buffer (Kotlin/Java stack traces land here)
$ADB logcat -b crash -d -t 200

# Or filter the full log for this app / KorGE
$ADB logcat -d -t 400 | grep -iE 'FATAL|AndroidRuntime|com.allmeatgames.triplo|korge|triplo'
```

Tips:
- Clear the log buffers first (`$ADB logcat -c`), reproduce the crash, then dump — keeps it short.
- Stack frames like `AnimationKt$animateBomb$1...(Animation.kt:212)` map directly to source files
  in `src/` (debug builds are not obfuscated).
- Reinstall after a fix: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew installAndroidDebug`
- Relaunch without touching the phone:
  `$ADB shell monkey -p com.allmeatgames.triplo -c android.intent.category.LAUNCHER 1`
