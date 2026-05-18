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
    version = "1.0.1"

    orientation = Orientation.PORTRAIT

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

// Google's public *sample* AdMob app id for iOS - serves test ads only. Replace for production.
val iosAdMobAppId = "ca-app-pub-3940256099942544~1458002511"
val googleMobileAdsSpmUrl = "https://github.com/googleads/swift-package-manager-google-mobile-ads.git"
val googleMobileAdsSpmVersion = "11.13.0"

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

/** Inserts the AdMob keys KorGE's generated iOS Info.plist is missing. */
fun patchIosInfoPlist(plist: File) {
    if (!plist.exists()) return
    val text = plist.readText()
    if (text.contains("GADApplicationIdentifier")) return
    val additions = buildString {
        append("\t<key>GADApplicationIdentifier</key>\n")
        append("\t<string>").append(iosAdMobAppId).append("</string>\n")
        // SKAdNetworkItems improves ad attribution. Google's identifier is included here; for
        // production add the full list from https://developers.google.com/admob/ios/3p-skadnetworks
        append("\t<key>SKAdNetworkItems</key>\n")
        append("\t<array>\n")
        append("\t\t<dict>\n")
        append("\t\t\t<key>SKAdNetworkIdentifier</key>\n")
        append("\t\t\t<string>cstr6suwn9.skadnetwork</string>\n")
        append("\t\t</dict>\n")
        append("\t</array>\n")
    }
    plist.writeText(text.replaceFirst("</dict>", additions + "</dict>"))
}

/** Injects the GoogleMobileAds SwiftPM package and the bridge source into the generated project.yml. */
fun patchIosProjectYml(projectYml: File) {
    if (!projectYml.exists()) return
    val lines = projectYml.readText().lines()
    if (lines.any { it.contains("GoogleMobileAds") }) return
    val out = StringBuilder()
    for (line in lines) {
        out.append(line).append('\n')
        when {
            // Top-level SwiftPM package declaration, right after the project name.
            line.trim() == "name: app" -> {
                out.append("packages:\n")
                out.append("  GoogleMobileAds:\n")
                out.append("    url: ").append(googleMobileAdsSpmUrl).append('\n')
                out.append("    from: \"").append(googleMobileAdsSpmVersion).append("\"\n")
            }
            // Add the Objective-C bridge sources to every app target.
            Regex("^(\\s*)- app\\s*$").matches(line) -> {
                val indent = line.substringBefore("- app")
                out.append(indent).append("- ../../../native/ios\n")
            }
            // Link the GoogleMobileAds package into every app target.
            line.trimStart().startsWith("- framework:") -> {
                val indent = line.substringBefore("- framework:")
                out.append(indent).append("- package: GoogleMobileAds\n")
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
// We append a step that patches them for AdMob and re-runs xcodegen so the .xcodeproj picks it up.
tasks.matching { it.name == "prepareKotlinNativeIosProject" }.configureEach {
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
val androidVersionCode = 2

pluginManager.withPlugin("com.android.application") {
    extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
        .onVariants { variant ->
            variant.outputs.forEach { it.versionCode.set(androidVersionCode) }
        }
}
