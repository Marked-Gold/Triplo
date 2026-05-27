#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * Thin Objective-C bridge over the Google Mobile Ads (AdMob) interstitial API plus the User
 * Messaging Platform (UMP) consent flow and App Tracking Transparency (ATT) prompt.
 *
 * The KorGE / Kotlin-Native side talks to this class through cinterop. This header deliberately
 * imports only Foundation - the GoogleMobileAds, UserMessagingPlatform and AppTrackingTransparency
 * SDKs are imported by TriploAds.m only - so the cinterop step does not need any third-party
 * headers to be present.
 *
 * All methods are safe to call from any thread; UI work is dispatched to the main thread.
 *
 * The `visibility("default")` attribute is load-bearing: Xcode's default build setting
 * `GCC_SYMBOLS_PRIVATE_EXTERN = YES` hides every Obj-C class symbol from the binary's export
 * table, so the `_OBJC_CLASS_$_TriploAds` referenced by GameMain.framework via
 * `-undefined dynamic_lookup` is invisible to dyld at launch and crashes the app with
 * `symbol not found in flat namespace`. Marking the @interface forces the class symbol into the
 * export table so the flat-namespace lookup resolves.
 */
__attribute__((visibility("default")))
@interface TriploAds : NSObject

/**
 * Runs the full ad-startup flow: gathers UMP consent (showing the privacy form if required),
 * triggers the ATT prompt on iOS 14+, and starts the Google Mobile Ads SDK. The completion block
 * fires once the SDK is ready (or has been skipped because the user has not granted consent).
 * Safe to call once at app start.
 */
+ (void)requestConsentAndStartAds:(NSString *)adUnitId
                       completion:(void (^)(void))completion;

/** Starts loading an interstitial for the given AdMob ad unit id. Safe to call repeatedly. */
+ (void)loadInterstitial:(NSString *)adUnitId;

/** Returns YES when an interstitial has finished loading and is ready to be shown. */
+ (BOOL)isInterstitialReady;

/**
 * Shows the loaded interstitial. The completion handler is invoked when the ad is dismissed,
 * or immediately if no ad is currently loaded.
 */
+ (void)showInterstitial:(void (^)(void))completion;

/**
 * Returns YES when GDPR / US-state privacy laws require us to surface a "manage consent"
 * affordance somewhere in the app (per Google's UMP guidance).
 */
+ (BOOL)privacyOptionsRequired;

/**
 * Presents the UMP privacy-options form so the user can revoke or change their previous consent
 * choice. The completion block fires when the form is dismissed (or immediately if it could not
 * be shown).
 */
+ (void)presentPrivacyOptions:(void (^)(void))completion;

@end

NS_ASSUME_NONNULL_END
