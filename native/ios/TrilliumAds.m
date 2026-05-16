#import "TrilliumAds.h"
#import <UIKit/UIKit.h>
@import GoogleMobileAds;

/**
 * Implementation of the AdMob interstitial bridge.
 *
 * This file is compiled by Xcode (not by Kotlin/Native cinterop) so it is free to import the
 * GoogleMobileAds SDK. It is added to the generated iOS app target by the `patchIosProject`
 * Gradle task, and the SDK is pulled in via Swift Package Manager (see build.gradle.kts).
 *
 * Written against Google Mobile Ads SDK 11.x. If a newer SDK renames these Objective-C symbols,
 * adjust them here.
 */
@interface TrilliumAds () <GADFullScreenContentDelegate>
@end

@implementation TrilliumAds {
    GADInterstitialAd *_interstitial;
    NSString *_adUnitId;
    void (^_completion)(void);
}

+ (instancetype)shared {
    static TrilliumAds *shared = nil;
    static dispatch_once_t once;
    dispatch_once(&once, ^{ shared = [[TrilliumAds alloc] init]; });
    return shared;
}

+ (void)initializeSdk {
    dispatch_async(dispatch_get_main_queue(), ^{
        [[GADMobileAds sharedInstance] startWithCompletionHandler:nil];
    });
}

+ (void)loadInterstitial:(NSString *)adUnitId {
    [[self shared] loadInterstitial:adUnitId];
}

+ (BOOL)isInterstitialReady {
    return [self shared]->_interstitial != nil;
}

+ (void)showInterstitial:(void (^)(void))completion {
    [[self shared] showInterstitial:completion];
}

- (void)loadInterstitial:(NSString *)adUnitId {
    _adUnitId = adUnitId;
    dispatch_async(dispatch_get_main_queue(), ^{
        GADRequest *request = [GADRequest request];
        [GADInterstitialAd loadWithAdUnitID:adUnitId
                                    request:request
                          completionHandler:^(GADInterstitialAd *_Nullable ad, NSError *_Nullable error) {
            if (error != nil || ad == nil) {
                NSLog(@"[TrilliumAds] interstitial failed to load: %@", error);
                self->_interstitial = nil;
                return;
            }
            ad.fullScreenContentDelegate = self;
            self->_interstitial = ad;
        }];
    });
}

- (void)showInterstitial:(void (^)(void))completion {
    dispatch_async(dispatch_get_main_queue(), ^{
        GADInterstitialAd *ad = self->_interstitial;
        if (ad == nil) {
            if (completion) completion();
            return;
        }
        self->_interstitial = nil;
        self->_completion = [completion copy];
        UIViewController *root = [TrilliumAds rootViewController];
        if (root == nil) {
            [self finishWithCompletion];
            return;
        }
        [ad presentFromRootViewController:root];
    });
}

- (void)finishWithCompletion {
    void (^completion)(void) = _completion;
    _completion = nil;
    if (completion) completion();
}

#pragma mark - GADFullScreenContentDelegate

- (void)adDidDismissFullScreenContent:(id<GADFullScreenPresentingAd>)ad {
    [self finishWithCompletion];
}

- (void)ad:(id<GADFullScreenPresentingAd>)ad
    didFailToPresentFullScreenContentWithError:(NSError *)error {
    NSLog(@"[TrilliumAds] interstitial failed to present: %@", error);
    [self finishWithCompletion];
}

#pragma mark - Helpers

+ (nullable UIViewController *)rootViewController {
    for (UIScene *scene in UIApplication.sharedApplication.connectedScenes) {
        if ([scene isKindOfClass:[UIWindowScene class]]) {
            UIWindowScene *windowScene = (UIWindowScene *)scene;
            for (UIWindow *window in windowScene.windows) {
                if (window.isKeyWindow && window.rootViewController != nil) {
                    return window.rootViewController;
                }
            }
        }
    }
    return UIApplication.sharedApplication.windows.firstObject.rootViewController;
}

@end
