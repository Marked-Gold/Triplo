#import "TriploAds.h"
#import <UIKit/UIKit.h>
@import GoogleMobileAds;
@import UserMessagingPlatform;
@import AppTrackingTransparency;

/**
 * Implementation of the AdMob interstitial bridge, the User Messaging Platform consent flow and
 * the App Tracking Transparency prompt.
 *
 * This file is compiled by Xcode (not by Kotlin/Native cinterop) so it is free to import the
 * third-party SDK headers. It is added to the generated iOS app target by the `patchIosProject`
 * Gradle task, and the SDKs are pulled in via Swift Package Manager (see build.gradle.kts).
 *
 * Written against Google Mobile Ads SDK 13.x and User Messaging Platform SDK 3.x. Objective-C
 * class names (GADInterstitialAd, GADRequest, ...) are unchanged from 11.x; only the Swift names
 * changed in v12.
 */
@interface TriploAds () <GADFullScreenContentDelegate>
@end

@implementation TriploAds {
    GADInterstitialAd *_interstitial;
    NSString *_adUnitId;
    void (^_completion)(void);
}

+ (instancetype)shared {
    static TriploAds *shared = nil;
    static dispatch_once_t once;
    dispatch_once(&once, ^{ shared = [[TriploAds alloc] init]; });
    return shared;
}

#pragma mark - Consent / ATT / startup

+ (void)requestConsentAndStartAds:(NSString *)adUnitId
                       completion:(void (^)(void))completion {
    dispatch_async(dispatch_get_main_queue(), ^{
        UMPRequestParameters *params = [[UMPRequestParameters alloc] init];
        // For production we use the user's real geo; debug-only test geographies are configured
        // in the AdMob console under Privacy & messaging.
        [UMPConsentInformation.sharedInstance
            requestConsentInfoUpdateWithParameters:params
                                 completionHandler:^(NSError *_Nullable error) {
            if (error != nil) {
                NSLog(@"[TriploAds] consent info update failed: %@", error);
            }
            UIViewController *root = [TriploAds rootViewController];
            [UMPConsentForm loadAndPresentIfRequiredFromViewController:root
                                                     completionHandler:^(NSError *_Nullable formError) {
                if (formError != nil) {
                    NSLog(@"[TriploAds] consent form error: %@", formError);
                }
                [TriploAds requestATTThenStartAds:adUnitId completion:completion];
            }];
        }];
    });
}

+ (void)requestATTThenStartAds:(NSString *)adUnitId
                    completion:(void (^)(void))completion {
    void (^proceed)(void) = ^{
        if (!UMPConsentInformation.sharedInstance.canRequestAds) {
            NSLog(@"[TriploAds] canRequestAds == NO; skipping Mobile Ads start");
            if (completion) completion();
            return;
        }
        [[GADMobileAds sharedInstance] startWithCompletionHandler:^(GADInitializationStatus *_) {
            if (completion) completion();
        }];
    };

    if (@available(iOS 14, *)) {
        if (ATTrackingManager.trackingAuthorizationStatus == ATTrackingManagerAuthorizationStatusNotDetermined) {
            [ATTrackingManager requestTrackingAuthorizationWithCompletionHandler:^(ATTrackingManagerAuthorizationStatus status) {
                dispatch_async(dispatch_get_main_queue(), proceed);
            }];
            return;
        }
    }
    proceed();
}

#pragma mark - Interstitial loading & presentation

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
                NSLog(@"[TriploAds] interstitial failed to load: %@", error);
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
        UIViewController *root = [TriploAds rootViewController];
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
    NSLog(@"[TriploAds] interstitial failed to present: %@", error);
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
