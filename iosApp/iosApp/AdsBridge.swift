import Foundation
import UIKit
import Shared
// ⚠️ Requires the GoogleMobileAds Swift Package added to the iosApp target (see docs/ads/ADS-0-gotchas.md).
// Until the package is added this file will NOT compile (the `import GoogleMobileAds` below is
// unresolved), so it is NOT yet a member of the iosApp Xcode target. Add the package, THEN add this
// file to the target (it is already on disk next to ContentView.swift), and call
// `AdsBridge.register()` from `iOSApp.init()` — see the doc.
import GoogleMobileAds

/// ADS-0 (iOS) — the Swift side of the AdProvider seam.
///
/// The Kotlin `:shared` framework defines `IosAdProviderBridge` (an Obj-C protocol) and the
/// process-wide `IosAds` registration point (see `AdProvider.ios.kt`). This class implements the
/// bridge using `GoogleMobileAds` (which only Swift can see — the SDK is an SPM dependency of the
/// Xcode app, not of the Kotlin framework) and registers itself so the Kotlin `IosAdProvider`
/// resolved from Koin delegates here.
///
/// It loads + presents ONE Google-**test** rewarded ad from the top view controller and reports a
/// coarse result string ("Shown"/"Rewarded"/"Dismissed"/"NoFill"/"Failed") that Kotlin maps to
/// `AdResult`. Behind the spike's debug trigger — not a real placement.
final class AdsBridge: NSObject, IosAdProviderBridge {

    /// Google's PUBLIC test rewarded ad unit (iOS). Serves only test ads — safe to commit.
    private static let testRewardedUnitId = "ca-app-pub-3940256099942544/1712485313"

    private var rewardedAd: RewardedAd?
    private var fullScreenDelegate: FullScreenDelegate?

    /// Call once at app launch (e.g. from `iOSApp.init()`), before the first Compose view controller.
    static func register() {
        IosAds.shared.register(bridge: AdsBridge())
    }

    func initialize() {
        // App ID comes from `GADApplicationIdentifier` in Info.plist (the SAMPLE App ID).
        MobileAds.shared.start(completionHandler: nil)
    }

    func showRewardedTestAd(onResult: @escaping (String) -> Void) {
        initialize()
        let request = Request()
        RewardedAd.load(with: Self.testRewardedUnitId, request: request) { [weak self] ad, error in
            guard let self = self else { onResult("Failed"); return }
            if let error = error as NSError? {
                // GADErrorCode.noFill == 1 in the GoogleMobileAds error domain.
                onResult(error.code == 1 ? "NoFill" : "Failed")
                return
            }
            guard let ad = ad,
                  let root = Self.topViewController() else {
                onResult("Failed")
                return
            }
            self.rewardedAd = ad
            var rewarded = false
            let delegate = FullScreenDelegate(
                onDismiss: { onResult(rewarded ? "Rewarded" : "Dismissed") },
                onFailed: { onResult("Failed") }
            )
            self.fullScreenDelegate = delegate
            ad.fullScreenContentDelegate = delegate
            ad.present(from: root) { rewarded = true }
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

/// Bridges the full-screen content callbacks to the simple closures the spike needs.
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
