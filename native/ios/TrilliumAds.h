#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * Thin Objective-C bridge over the Google Mobile Ads (AdMob) interstitial API.
 *
 * The KorGE / Kotlin-Native side talks to this class through cinterop. This header deliberately
 * imports only Foundation - the GoogleMobileAds SDK is imported by TrilliumAds.m only - so the
 * cinterop step does not need the AdMob SDK headers to be present.
 *
 * All methods are safe to call from any thread; UI work is dispatched to the main thread.
 */
@interface TrilliumAds : NSObject

/** Initialises the Google Mobile Ads SDK. Call once at app start. */
+ (void)initializeSdk;

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
