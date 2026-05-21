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
 */
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

@end

NS_ASSUME_NONNULL_END
