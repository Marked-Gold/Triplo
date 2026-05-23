import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import korlibs.korge.gradle.*
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.util.Properties

plugins {
    alias(libs.plugins.korge)
}

korge {
    id = "com.allmeatgames.triplo"
    name = "Triplo"
    version = "1.0.2"

    orientation = Orientation.PORTRAIT

    // iOS 26.5 dropped support for the iPhone 8 (KorGE 6.0's default simulator); bump to a
    // current-generation device so `xcrun simctl create` succeeds.
    preferredIphoneSimulatorVersion = 17

    // Apple Developer Program team ID for AllMeat Games (individual enrollment under
    // Mark Robertson Gaskin). Used as DEVELOPMENT_TEAM in the generated Xcode project for all
    // iOS variants. The corresponding distribution certificate / profile is auto-managed by
    // Xcode under this team and is what allows App Store Connect uploads.
    appleDevelopmentTeamId = "784936QA8D"

    // Google Play requires targetSdk 35+ for new apps (and 36+ from Aug 2026), so target 36.
    // minSdk 23 is the floor for Google Mobile Ads SDK 24+.
    androidSdk(compileSdk = 36, minSdk = 23, targetSdk = 36)

// To selectively enable targets
    targetJvm()
    targetJs()
    targetDesktop()
    targetIos()
    targetAndroid()

    // --- AdMob (Google Mobile Ads) ---
    // Required so the ads SDK can fetch ads.
    androidPermission("android.permission.INTERNET")
    // Lets the game play haptic feedback through the system Vibrator (see src@android/HapticsAndroid.kt).
    androidPermission("android.permission.VIBRATE")
    // AdMob application id for the production Android app.
    androidManifestApplicationChunk(
        "<meta-data android:name=\"com.google.android.gms.ads.APPLICATION_ID\" " +
            "android:value=\"ca-app-pub-7742910323184344~4789526938\" />"
    )
}

dependencies {
    add("commonMainApi", project(":deps"))
    // Google Mobile Ads SDK - only needed by the Android target (see src@android/AdsAndroid.kt).
    add("androidMainImplementation", "com.google.android.gms:play-services-ads:25.2.0")
}

// =====================================================================================
// iOS AdMob bridge
// -------------------------------------------------------------------------------------
// The iOS interstitial ads go through an Objective-C bridge (native/ios/TriploAds.{h,m}).
//  * The cinterop below exposes the bridge *header* to Kotlin/Native.
//  * patchIosProject injects the GoogleMobileAds SwiftPM package, the bridge .m source and the
//    required Info.plist keys into KorGE's generated Xcode project (KorGE regenerates it on every
//    build, so the patch re-runs every build).
// Building any iOS target requires a full Xcode install.
// =====================================================================================

// Production AdMob app id for the iOS app (AllMeat Games publisher).
val iosAdMobAppId = "ca-app-pub-7742910323184344~4136123498"
val googleMobileAdsSpmUrl = "https://github.com/googleads/swift-package-manager-google-mobile-ads.git"
val googleMobileAdsSpmVersion = "13.4.0"
val googleUmpSpmUrl = "https://github.com/googleads/swift-package-manager-google-user-messaging-platform.git"
val googleUmpSpmVersion = "3.0.0"

// SKAdNetwork identifiers recommended by Google for AdMob's mediation/3p partners. Apple uses
// these for install attribution under iOS 14+ privacy rules. Source:
// https://developers.google.com/admob/ios/3p-skadnetworks
val skAdNetworkIdentifiers = listOf(
    "cstr6suwn9", "4fzdc2evr5", "2fnua5tdw4", "ydx93a7ass", "p78axxw29g",
    "v72qych5uu", "ludvb6z3bs", "cp8zw746q7", "3sh42y64q3", "c6k4g5qg8m",
    "s39g8k73mm", "wg4vff78zm", "3qy4746246", "f38h382jlk", "hs6bdukanm",
    "mlmmfzh3r3", "v4nxqhlyqp", "wzmmz9fp6w", "su67r6k2v3", "yclnxrl5pm",
    "t38b2kh725", "7ug5zh24hu", "gta9lk7p23", "vutu7akeur", "y5ghdn5j9k",
    "v9wttpbfk9", "n38lu8286q", "47vhws6wlr", "kbd757ywx3", "9t245vhmpl",
    "a2p9lx4jpn", "22mmun2rn5", "44jx6755aq", "k674qkevps", "4468km3ulz",
    "2u9pt9hc89", "8s468mfl3y", "klf5c3l5u5", "ppxm28t8ap", "kbmxgpxpgc",
    "uw77j35x4d", "578prtvx9j", "4dzt52r2t5", "tl55sbb4fm", "c3frkrj4fj",
    "e5fvkxwrpn", "8c4e2ghe7u", "3rd42ekr43", "97r2b46745", "3qcr597p9d",
)

// Shown to the user the first time iOS asks for App Tracking Transparency permission.
val iosTrackingUsageDescription =
    "We use this identifier to show you relevant interstitial ads. You can change this any time in Settings."

kotlin {
    for (targetName in listOf("iosArm64", "iosX64", "iosSimulatorArm64")) {
        val target = targets.findByName(targetName) as? KotlinNativeTarget ?: continue
        target.compilations.getByName("main").cinterops.create("GoogleMobileAdsBridge") {
            defFile(project.file("native/GoogleMobileAdsBridge.def"))
            includeDirs(project.file("native/ios"))
        }
    }
}

// KorGE 6.0.0 pins the Kotlin 2.0 compiler, but Google Mobile Ads SDK 25.x ships modules built
// with Kotlin 2.2 metadata. Skip the metadata-version assertion so the 2.0 compiler will consume
// them; the flag only relaxes a version check and changes no generated code.
tasks.matching { it.name == "compileDebugKotlinAndroid" || it.name == "compileReleaseKotlinAndroid" }
    .configureEach {
        (this as org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>)
            .compilerOptions.freeCompilerArgs.add("-Xskip-metadata-version-check")
    }

/** Inserts the AdMob / ATT / SKAdNetwork keys KorGE's generated iOS Info.plist is missing, and
 *  rewrites the orientation arrays to portrait-only (KorGE writes all three/four orientations
 *  regardless of `orientation = Orientation.PORTRAIT`). */
fun patchIosInfoPlist(plist: File) {
    if (!plist.exists()) return
    var text = plist.readText()

    // AdMob / ATT / SKAdNetwork keys are inserted once - skip if already present.
    if (!text.contains("GADApplicationIdentifier")) {
        val additions = buildString {
            append("\t<key>GADApplicationIdentifier</key>\n")
            append("\t<string>").append(iosAdMobAppId).append("</string>\n")
            append("\t<key>NSUserTrackingUsageDescription</key>\n")
            append("\t<string>").append(iosTrackingUsageDescription).append("</string>\n")
            // Opt out of iPad multitasking (Slide Over / Split View / Stage Manager). Without
            // this, App Store Connect upload validation (error 90474) requires the bundle to
            // declare all four orientations in UISupportedInterfaceOrientations, which would
            // break the intentional portrait lock for a one-handed merge puzzle. The app still
            // runs on iPad — it just opens fullscreen rather than in a resizable window.
            append("\t<key>UIRequiresFullScreen</key>\n")
            append("\t<true/>\n")
            append("\t<key>SKAdNetworkItems</key>\n")
            append("\t<array>\n")
            for (id in skAdNetworkIdentifiers) {
                append("\t\t<dict>\n")
                append("\t\t\t<key>SKAdNetworkIdentifier</key>\n")
                append("\t\t\t<string>").append(id).append(".skadnetwork</string>\n")
                append("\t\t</dict>\n")
            }
            append("\t</array>\n")
        }
        text = text.replaceFirst("</dict>", additions + "</dict>")
    }

    // Lock both iPhone and iPad to portrait. Re-running idempotently because the right-hand side
    // already collapses to a single Portrait entry, so re-applying the regex is a no-op.
    text = text.replace(
        Regex("<key>UISupportedInterfaceOrientations(~ipad)?</key>\\s*<array>[\\s\\S]*?</array>")
    ) { match ->
        val suffix = match.groupValues[1]
        "<key>UISupportedInterfaceOrientations$suffix</key>\n" +
            "\t<array>\n" +
            "\t\t<string>UIInterfaceOrientationPortrait</string>\n" +
            "\t</array>"
    }

    plist.writeText(text)
}

/** Injects the GoogleMobileAds + UMP SwiftPM packages and the bridge source into the generated project.yml. */
fun patchIosProjectYml(projectYml: File) {
    if (!projectYml.exists()) return
    val lines = projectYml.readText().lines()
    if (lines.any { it.contains("GoogleMobileAds") }) return
    var currentTarget: String? = null
    val out = StringBuilder()
    for (line in lines) {
        // Track which top-level target block we're inside so target-specific patches can be
        // gated on the target name. Targets in KorGE's generated project.yml are indented by
        // exactly two spaces under `targets:` and have names like `app-Arm64-Release`.
        Regex("^  (app-[A-Za-z0-9_-]+):\\s*$").matchEntire(line)?.let {
            currentTarget = it.groupValues[1]
        }

        out.append(line).append('\n')
        when {
            // Top-level SwiftPM package declarations, right after the project name. Both packages
            // expose a single product whose name matches the package name, so xcodegen's
            // `- package: <name>` shorthand resolves to the correct library.
            line.trim() == "name: app" -> {
                out.append("packages:\n")
                out.append("  GoogleMobileAds:\n")
                out.append("    url: ").append(googleMobileAdsSpmUrl).append('\n')
                out.append("    from: \"").append(googleMobileAdsSpmVersion).append("\"\n")
                out.append("  GoogleUserMessagingPlatform:\n")
                out.append("    url: ").append(googleUmpSpmUrl).append('\n')
                out.append("    from: \"").append(googleUmpSpmVersion).append("\"\n")
            }
            // Add the Objective-C bridge sources to every app target.
            Regex("^(\\s*)- app\\s*$").matches(line) -> {
                val indent = line.substringBefore("- app")
                out.append(indent).append("- ../../../native/ios\n")
            }
            // Link the GoogleMobileAds and UMP packages into every app target.
            line.trimStart().startsWith("- framework:") -> {
                val indent = line.substringBefore("- framework:")
                out.append(indent).append("- package: GoogleMobileAds\n")
                out.append(indent).append("- package: GoogleUserMessagingPlatform\n")
            }
            // xcodegen's `bundleIdPrefix` + target name would otherwise produce
            // `com.allmeatgames.triplo.app-Arm64-Release`, which is not a registered App ID and
            // App Store Connect will refuse to accept. Override the bundle ID on the device-Release
            // target only — that's the one that gets archived and uploaded. The other variants
            // (debug/simulator/x64) keep their auto-suffixed IDs, which is useful for local testing
            // (multiple variants can be installed on the same device side-by-side).
            currentTarget == "app-Arm64-Release" && line.trim().startsWith("DEVELOPMENT_TEAM:") -> {
                val indent = line.substringBefore("DEVELOPMENT_TEAM:")
                out.append(indent).append("PRODUCT_BUNDLE_IDENTIFIER: com.allmeatgames.triplo\n")
            }
            // Force the binary to iPhone-only (TARGETED_DEVICE_FAMILY = 1). KorGE's xcodegen
            // template defaults to Universal (1,2) which makes App Store Connect demand 13"
            // iPad screenshots and pull the app into iPad review scrutiny (Stage Manager,
            // Split View, iPad-specific orientation handling). The game is designed for
            // one-handed iPhone play; iPad users still get the app via iPhone compatibility
            // mode. Applied at the project level (currentTarget is null before any target
            // block has been entered, i.e. when processing the top-level `settings:` block),
            // so all 6 variants inherit it.
            currentTarget == null && line.trim().startsWith("DEVELOPMENT_TEAM:") -> {
                val indent = line.substringBefore("DEVELOPMENT_TEAM:")
                out.append(indent).append("TARGETED_DEVICE_FAMILY: \"1\"\n")
            }
        }
    }
    projectYml.writeText(out.toString())
}

/** Locates the xcodegen executable KorGE installed (or one on PATH). */
fun findXcodeGen(): String {
    val home = File(System.getProperty("user.home"))
    val candidates = (File(home, ".korge").listFiles { f -> f.name.startsWith("XcodeGen-") } ?: emptyArray())
        .flatMap {
            listOf(
                File(it, ".build/release/xcodegen"),
                File(it, ".build/apple/Products/Release/xcodegen"),
            )
        } + File("/usr/local/bin/xcodegen") + File("/opt/homebrew/bin/xcodegen")
    return candidates.firstOrNull { it.exists() }?.absolutePath ?: "xcodegen"
}

// KorGE's prepareKotlinNativeIosProject task generates project.yml + Info.plist and runs xcodegen.
// We append a step that patches them for AdMob/ATT/SKAdNetwork + portrait-lock and re-runs
// xcodegen so the .xcodeproj picks it up. The `upToDateWhen { false }` forces the task to run
// every build; otherwise gradle skips it when it thinks nothing changed, which also skips our
// doLast and leaves a stale Info.plist in the built .app bundle.
tasks.matching { it.name == "prepareKotlinNativeIosProject" }.configureEach {
    outputs.upToDateWhen { false }
    doLast {
        val iosDir = layout.buildDirectory.dir("platforms/ios").get().asFile
        patchIosInfoPlist(File(iosDir, "app/Info.plist"))
        patchIosProjectYml(File(iosDir, "project.yml"))
        exec {
            workingDir = iosDir
            commandLine(findXcodeGen())
        }
    }
}

// =====================================================================================
// Android adaptive launcher icon
// -------------------------------------------------------------------------------------
// KorGE 6 emits only a legacy bitmap launcher icon (@mipmap/icon). On Android 8+ the
// launcher wraps a legacy icon in a shrunken white circle. android/res/ adds a proper
// <adaptive-icon> (mipmap-anydpi-v26/icon.xml) that overrides @mipmap/icon for API 26+,
// so the artwork fills the launcher mask edge-to-edge. We register it as an extra res
// source dir; KorGE's generated androires (set at configuration time) is left untouched.
// =====================================================================================
project.afterEvaluate {
    val android = extensions.findByName("android") as? com.android.build.gradle.BaseExtension
    android?.sourceSets?.getByName("main")?.res?.srcDir(file("android/res"))
}

// Release signing (upload key). Configured when the Android application plugin is applied — early
// enough for AGP to read it (an afterEvaluate hook runs too late). keystore.properties and the
// .jks are gitignored and backed up in 1Password; without the file the release build is left
// unsigned so non-release tasks still work on machines without the key.
pluginManager.withPlugin("com.android.application") {
    val keystoreProps = file("keystore.properties")
    if (!keystoreProps.exists()) return@withPlugin
    val android = extensions.getByName("android") as com.android.build.gradle.BaseExtension
    val props = Properties().apply { keystoreProps.inputStream().use { load(it) } }
    val release = android.signingConfigs.maybeCreate("release")
    release.storeFile = file(props.getProperty("storeFile"))
    release.storePassword = props.getProperty("storePassword")
    release.keyAlias = props.getProperty("keyAlias")
    release.keyPassword = props.getProperty("keyPassword")
    android.buildTypes.getByName("release").signingConfig = release
}

// Android versionCode. Every Google Play upload (across ALL tracks — internal, closed,
// production) must use a strictly higher versionCode than the last, so bump this on each
// release. KorGE sets versionName from korge.version above but leaves versionCode alone; the
// AGP variant API sets it here, which binds late enough to be authoritative.
val androidVersionCode = 4

pluginManager.withPlugin("com.android.application") {
    extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
        .onVariants { variant ->
            variant.outputs.forEach { it.versionCode.set(androidVersionCode) }
        }
}
