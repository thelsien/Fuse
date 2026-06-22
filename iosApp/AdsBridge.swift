import Foundation
import UIKit
import Shared
// ⚠️ Requires the GoogleMobileAds Swift Package added to the iosApp target (see docs/ads/ADS-0-gotchas.md).
import GoogleMobileAds

/// ADS-1 (iOS) — the Swift side of the generalized AdProvider seam.
///
/// The Kotlin `:shared` framework defines `IosAdProviderBridge` (an Obj-C protocol) and the
/// process-wide `IosAds` registration point (see `AdProvider.ios.kt`). This class implements the
/// bridge using `GoogleMobileAds` (which only Swift can see — the SDK is an SPM dependency of the
/// Xcode app, not of the Kotlin framework) and registers itself so the Kotlin `IosAdProvider`
/// resolved from Koin delegates here.
///
/// It supports BOTH formats — rewarded AND interstitial — with separate load/isReady/show, using
/// Google's PUBLIC **test** units (from the Kotlin `AdUnitIds`). Result strings
/// ("Rewarded"/"Dismissed"/"Completed"/"NoFill"/"NotReady"/"Failed") map to Kotlin `AdResult`.
/// Behind the spike's debug trigger — not a real placement.
final class AdsBridge: NSObject, IosAdProviderBridge {

    private var rewardedAd: RewardedAd?
    private var interstitialAd: InterstitialAd?
    private var fullScreenDelegate: FullScreenDelegate?

    /// Call once at app launch (e.g. from `iOSApp.init()`), before the first Compose view controller.
    static func register() {
        IosAds.shared.register(bridge: AdsBridge())
    }

    func initialize() {
        // App ID comes from `GADApplicationIdentifier` in Info.plist (the SAMPLE App ID).
        MobileAds.shared.start(completionHandler: nil)
    }

    func load(format: String, onLoaded: @escaping (KotlinBoolean) -> Void) {
        initialize()
        let request = Request()
        if format == AdFormat.rewarded.name {
            RewardedAd.load(with: AdUnitIds.shared.IOS_REWARDED, request: request) { [weak self] ad, error in
                guard let self = self, error == nil, let ad = ad else { onLoaded(false); return }
                self.rewardedAd = ad
                onLoaded(true)
            }
        } else {
            InterstitialAd.load(with: AdUnitIds.shared.IOS_INTERSTITIAL, request: request) { [weak self] ad, error in
                guard let self = self, error == nil, let ad = ad else { onLoaded(false); return }
                self.interstitialAd = ad
                onLoaded(true)
            }
        }
    }

    func isReady(format_ format: String) -> Bool {
        if format == AdFormat.rewarded.name { return rewardedAd != nil }
        return interstitialAd != nil
    }

    func show(format: String, onResult: @escaping (String) -> Void) {
        guard let root = Self.topViewController() else { onResult("Failed"); return }
        if format == AdFormat.rewarded.name {
            guard let ad = rewardedAd else { onResult("NotReady"); return }
            rewardedAd = nil
            var rewarded = false
            let delegate = FullScreenDelegate(
                onDismiss: { onResult(rewarded ? "Rewarded" : "Dismissed") },
                onFailed: { onResult("Failed") }
            )
            fullScreenDelegate = delegate
            ad.fullScreenContentDelegate = delegate
            ad.present(from: root) { rewarded = true }
        } else {
            guard let ad = interstitialAd else { onResult("NotReady"); return }
            interstitialAd = nil
            let delegate = FullScreenDelegate(
                onDismiss: { onResult("Completed") },
                onFailed: { onResult("Failed") }
            )
            fullScreenDelegate = delegate
            ad.fullScreenContentDelegate = delegate
            ad.present(from: root)
        }
    }

    /// The front-most presented view controller of the key window (defensive).
    private static func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes
        let window = scenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }
        var controller = window?.rootViewController
        while let presented = controller?.presentedViewController {
            controller = presented
        }
        return controller
    }
}

/// Bridges the full-screen content callbacks to the simple closures the bridge needs.
private final class FullScreenDelegate: NSObject, FullScreenContentDelegate {
    private let onDismiss: () -> Void
    private let onFailed: () -> Void

    init(onDismiss: @escaping () -> Void, onFailed: @escaping () -> Void) {
        self.onDismiss = onDismiss
        self.onFailed = onFailed
    }

    func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) {
        onDismiss()
    }

    func ad(_ ad: FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        onFailed()
    }
}
