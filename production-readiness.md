# Triplo — Production Readiness

Tracking the launch of **Triplo** (a KorGE 6 powers-of-three puzzle game) to the Google Play
Store and the Apple App Store, published by **AllMeat Games**.

Legend: ✅ done · ⏳ in progress / waiting · ❌ not started

## Key references

| Item | Value |
|---|---|
| App name | Triplo (renamed from "Trillium") |
| Application / bundle ID | `com.allmeatgames.triplo` |
| Studio | AllMeat Games — allmeatgames.com |
| Marketing / redirect domain | triplo.club |
| Developer-account email | mark@allmeatgames.com |
| App version | 1.0.2 (versionCode 4) — bump `androidVersionCode` in `build.gradle.kts` for every Play upload |
| Android AdMob app ID | `ca-app-pub-7742910323184344~4789526938` |
| Android interstitial ad unit | `ca-app-pub-7742910323184344/7551421648` |
| Upload keystore | `triplo-upload-key.jks` — gitignored; file + password stored in 1Password; alias `triplo-upload` |

## Toolchain

- KorGE 6.0.0 (latest stable), Kotlin 2.0, JDK 21.
- Android: `compileSdk`/`targetSdk` 36, `minSdk` 23; Google Mobile Ads SDK 25.2.0.
  - GMA 25.x ships Kotlin 2.2 metadata; KorGE pins Kotlin 2.0, so the Android compile passes
    `-Xskip-metadata-version-check` (build-time only, no runtime effect).
- iOS: Google Mobile Ads SDK 13.4.0 + UMP SDK 3.0.0 (Swift Package Manager), bundle-bridged through
  `native/ios/TriploAds.{h,m}`; `preferredIphoneSimulatorVersion = 17` (iOS 26.5 dropped iPhone 8).
- Build the signed release bundle: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew bundleRelease`

---

## Work completed

### Rebrand & Android build — ✅
- ✅ Renamed Trillium → Triplo throughout; application ID → `com.allmeatgames.triplo`.
- ✅ Modernized: API 36, GMA 25.2.0, minSdk 23, app version 1.0.0.
- ✅ Release signing wired in (upload keystore + `keystore.properties`, both gitignored).
- ✅ Signed release AAB builds and verified (`jarsigner` clean).
- ✅ R8-minified release build smoke-tested on a physical device — no crashes.

### Android AdMob — ✅ code complete
- ✅ Real AdMob app ID + interstitial ad unit ID wired in.
- ✅ UMP (User Messaging Platform) consent flow integrated — gathers consent before ad init.
- ✅ Test device registered (Pixel 10) so ads can be tested without invalid-traffic risk.

### Developer accounts & domains — ✅ partial
- ✅ Cloudflare: `triplo.club` + `allmeatgames.com` registered, email routing configured.
- ✅ Google Play Console — enrolled as Individual; $25 paid.
- ✅ AdMob account created; Android app added.

---

## Work remaining

### Accounts — ⏳ / ❌
- ⏳ Google Play Console — government photo ID verification pending (~days).
- ⏳ AdMob account — under review.
- ❌ AdMob: complete the **Payments** section (address + tax info).
- ❌ AdMob: create **Privacy & messaging** consent messages (GDPR + US states) — until then
  the UMP `requestConsentInfoUpdate` call logs a "Publisher misconfiguration" warning.
- ❌ Apple Developer Program — enroll as Individual ($99/yr).

### iOS — ⏳ in progress
- ✅ Xcode 26.5 installed; iOS 26.5 simulator runtime installed; license accepted.
- ✅ iOS simulator build verified — app boots on iOS 26.5 and the UMP consent pre-prompt fires.
- ✅ Bumped Google Mobile Ads SPM 11.13.0 → **13.4.0**.
- ✅ Added UMP (User Messaging Platform) SPM 3.0.0 via the same project.yml patch.
- ✅ Obj-C bridge now drives the UMP consent flow → ATT prompt → `MobileAds.start` end-to-end
  (`requestConsentAndStartAds:completion:` in `native/ios/TriploAds.m`).
- ✅ `NSUserTrackingUsageDescription` added to the patched `Info.plist`.
- ✅ Full `SKAdNetworkItems` list (50 identifiers from Google's 3p list) in the Info.plist patch.
- ✅ Renamed Kotlin `enum class Number` → `Rank` to avoid the `GameMainNumber` Obj-C symbol
  collision with `kotlin.Number` at framework-link time.
- ✅ `native/GoogleMobileAdsBridge.def` now passes `-undefined dynamic_lookup` so the Kotlin/Native
  framework defers `TriploAds` symbol resolution to the app-binary link step.
- ❌ Add the iOS app in AdMob; wire its real app ID + interstitial ad unit ID (currently using
  Google's *sample* IDs).
- ❌ Privacy & messaging UMP consent messages configured in the AdMob console (GDPR + US states).
- ❌ Verify on a physical iPhone (USB) — simulator only so far.
- ❌ iOS signing / provisioning via the Apple Developer account.

### Store listings — ❌ not started
- ✅ Privacy policy drafted — served at `triplo.club/privacy` by the Worker once deployed.
- ❌ Screenshots (Android phone; iOS 6.7").
- ❌ 512×512 icon, 1024×500 feature graphic.
- ❌ Short + full store description.
- ❌ Content rating questionnaire (Google) / age rating (Apple).
- ❌ Data safety form (Google) / App Privacy labels (Apple) — declare AdMob + advertising ID.
- ❌ `app-ads.txt` on `allmeatgames.com` (optional, improves ad fill).

### triplo.club redirect — ✅ code written, ⏳ deploy
- ✅ Cloudflare Worker written (`cloudflare-worker/`): platform-detecting redirect
  (iOS → App Store, Android → Play Store, desktop → landing page) + `/privacy` page.
- ❌ Deploy it: `npx wrangler deploy` from `cloudflare-worker/` (needs Cloudflare login).
- ❌ Fill in `APP_STORE_URL` in `worker.js` once the iOS app's App Store ID exists.

### Launch — ❌ blocked on the above
- ❌ Google **closed test**: 12+ testers opted in for 14 continuous days — required for new
  Individual accounts before production access is granted. This is the critical-path long pole.
- ❌ Google production submission & rollout.
- ❌ Apple TestFlight + App Store submission.

---

## Notes

- The 14-day Google closed test is the long pole; it cannot start until the Play account is
  verified and a signed AAB is uploaded to a closed-testing track.
- Rebuild the final release AAB once iOS AdMob and any consent changes are in, so a single
  build serves both the closed test and production.
