import korlibs.korge.gradle.*
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.korge)
}

korge {
    id = "com.sample.demo"
    name = "Trillium"

    orientation = Orientation.PORTRAIT

    // compileSdk 34 is required by the AdMob SDK's transitive AndroidX dependencies.
    androidSdk(compileSdk = 34, minSdk = 21, targetSdk = 34)

// To selectively enable targets
    targetJvm()
    targetJs()
    targetDesktop()
    targetIos()
    targetAndroid()

    // --- AdMob (Google Mobile Ads) ---
    // Required so the ads SDK can fetch ads.
    androidPermission("android.permission.INTERNET")
    // AdMob application id. This is Google's public *sample* app id which only serves test ads.
    // Replace with your real AdMob app id before a production release.
    androidManifestApplicationChunk(
        "<meta-data android:name=\"com.google.android.gms.ads.APPLICATION_ID\" " +
            "android:value=\"ca-app-pub-3940256099942544~3347511713\" />"
    )
}

dependencies {
    add("commonMainApi", project(":deps"))
    // Google Mobile Ads SDK - only needed by the Android target (see src@android/AdsAndroid.kt).
    add("androidMainImplementation", "com.google.android.gms:play-services-ads:23.6.0")
}

// =====================================================================================
// iOS AdMob bridge
// -------------------------------------------------------------------------------------
// The iOS interstitial ads go through an Objective-C bridge (native/ios/TrilliumAds.{h,m}).
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
