# ADS-0 — Ad SDK spike: integration gotchas (Sprint 8, Ads)

Goal of this spike: retire the native-SDK risk before ADS-1..4 by proving a **test ad renders on
both platforms** through a minimal `AdProvider` `expect`/`actual` seam, and documenting every
integration gotcha so the follow-on stories are mechanical.

**Status:** Android is DONE and verified on a device (rewarded test ad rendered, reward earned,
result round-tripped to the UI — screenshots below). iOS is CODE-COMPLETE on both the Kotlin and
Swift sides but requires **one manual Xcode step** (add the SwiftPM package) that cannot be done
reliably by hand-editing `project.pbxproj`. See "iOS — the one manual step" below.

**No secrets policy:** this spike uses ONLY Google's PUBLIC AdMob test IDs (they serve only test
ads and are safe to commit). NO real AdMob account ID, ad-unit ID, API key, or signing material is
in the repo. Real IDs are a release-time concern injected from gitignored config (see "Release-time
real-ID injection").

---

## The seam (shared)

- `commonMain` — `com/fuse/ads/AdProvider.kt`: `interface AdProvider { fun initialize(); suspend fun
  showRewardedTestAd(): AdResult }`, the `AdResult` enum (`Shown/Rewarded/Dismissed/NoFill/Failed`),
  the `NoOpAdProvider` fake default, and `expect val platformAdsModule: Module` (mirrors
  `platformSharerModule`). `AdsDebug.enabled` is the feature flag.
- `androidMain` — `AdProvider.android.kt` wraps `play-services-ads`; `AdActivityHolder.kt` tracks the
  foreground Activity (set/cleared by `MainActivity`).
- `iosMain` — `AdProvider.ios.kt` does NOT touch GoogleMobileAds. Instead it exposes
  `IosAdProviderBridge` + the `IosAds` registration point; the **Swift** side
  (`iosApp/iosApp/AdsBridge.swift`) implements the bridge with GoogleMobileAds and registers it at
  launch. This keeps the Kotlin framework compiling with ZERO third-party iOS deps.
- `platformAdsModule` is registered in `com/fuse/di/Modules.kt`'s `appModules`.
- Trigger: a debug-only "Show test ad" row in `SettingsScreen` (stateful wrapper), shown only when
  `AdsDebug.enabled`. NOT wired to game-over/replay (that is ADS-2/4).

---

## Android — done

- **SDK:** `com.google.android.gms:play-services-ads:24.4.0` (version catalog: `play-services-ads`),
  added to **`:shared` androidMain** (the Android actual lives there). Builds against compileSdk 35 /
  AGP 8.9.3 with no compileSdk bump.
- **Manifest (`androidApp/src/main/AndroidManifest.xml`):**
  ```xml
  <meta-data
      android:name="com.google.android.gms.ads.APPLICATION_ID"
      android:value="ca-app-pub-3940256099942544~3347511713" /> <!-- PUBLIC SAMPLE App ID -->
  ```
  This is **required** — the SDK crashes at `MobileAds.initialize()` if it is missing.
- **Activity requirement:** a rewarded ad must be *shown* from an `Activity`, but Koin only provides
  the application `Context`. `AdActivityHolder` (weak ref, set in `MainActivity.onResume`, cleared in
  `onDestroy`) bridges that gap. ADS-1 may replace it with an `ActivityLifecycleCallbacks`
  registration in `FuseApplication`.
- **Build-size/time impact:** the debug APK assembles fine; `play-services-ads` pulls
  `play-services-ads-lite` + measurement transitively. Expect roughly **+2–3 MB** of method/library
  weight on the APK (mitigated in release by R8). `assembleDebug` time is unchanged in practice.
- **No `INTERNET` permission needed to add** — `play-services-ads` already declares `INTERNET` /
  `ACCESS_NETWORK_STATE` in its own manifest, merged automatically.
- **Verified:** test rewarded ad rendered on `emulator-5554` (Google-Play emulator), the "Reward
  granted" pill appeared, and the UI updated to `Result: Rewarded` after dismiss. Screenshots:
  - `docs/ads/screenshots/ads0-android-debug-trigger.png`
  - `docs/ads/screenshots/ads0-android-test-ad.png`
  - `docs/ads/screenshots/ads0-android-result-rewarded.png`

---

## iOS — the one manual step (SwiftPM)

The iOS GoogleMobileAds SDK is added to the **Xcode app** (not the Kotlin framework) via Swift
Package Manager. Adding an SPM package mutates `project.pbxproj` with several interlocking objects
(`XCRemoteSwiftPackageReference`, `XCSwiftPackageProductDependency`, the project `packageReferences`,
the target `packageProductDependencies`, and a `PBXBuildFile` in the Frameworks phase) plus
`project.xcworkspace/xcshareddata/swiftpm/Package.resolved`. Hand-editing that into the current
minimal pbxproj risks a broken, un-openable project, so this is left as a deliberate manual step.

**Do this once in Xcode (≈2 minutes):**

1. Open `iosApp/iosApp.xcodeproj` in Xcode.
2. **File ▸ Add Package Dependencies…**
3. Enter the URL:
   `https://github.com/googleads/swift-package-manager-google-mobile-ads`
4. Dependency Rule: "Up to Next Major" (current major is fine for the spike).
5. Click **Add Package**; when prompted for products, check **`GoogleMobileAds`** and assign it to
   the **`iosApp`** target. Click **Add Package**.
6. Add the already-on-disk Swift bridge to the target: in the Project navigator, select
   `iosApp/iosApp/AdsBridge.swift`, and in the File Inspector tick **Target Membership ▸ iosApp**
   (it is committed but not yet a target member, since membership also lives in pbxproj).
7. Register the bridge at launch — edit `iosApp/iosApp/iOSApp.swift`:
   ```swift
   import SwiftUI

   @main
   struct iOSApp: App {
       init() {
           AdsBridge.register() // ADS-0: wire the Swift GoogleMobileAds bridge into the Kotlin seam
       }
       var body: some Scene {
           WindowGroup { ContentView() }
       }
   }
   ```
8. Build & run on a simulator, open Settings ▸ "Show test ad", and confirm a "Test Ad" rewarded ad
   presents. Commit the resulting pbxproj + `Package.resolved` changes.

Everything else is already in place:
- **Info.plist** already has the required `GADApplicationIdentifier` (PUBLIC SAMPLE iOS App ID
  `ca-app-pub-3940256099942544~1458002511`). The SDK crashes at start without it.
- **Kotlin actual** (`AdProvider.ios.kt`) already delegates to the registered bridge and safely
  reports `Failed` until one is registered — so the framework compiles and tests pass NOW, before
  the package exists (verified: `:shared:iosSimulatorArm64Test` green).
- **Swift bridge** (`AdsBridge.swift`) is written against the current GoogleMobileAds API
  (`MobileAds.shared.start`, `RewardedAd.load(with:request:)`, `ad.present(from:)`, the
  `FullScreenContentDelegate`). It will compile once the package import resolves.

### ATT & SKAdNetwork (NOT needed for test ads — release/Sprint 10 concern)
- **App Tracking Transparency (ATT):** real ads at release should request ATT
  (`ATTrackingManager.requestTrackingAuthorization`) and add `NSUserTrackingUsageDescription` to
  Info.plist. NOT required to render TEST ads, so intentionally omitted from this spike — add it in
  the release/privacy story (Sprint 10).
- **SKAdNetwork:** real ads at release need an `SKAdNetworkItems` array in Info.plist (Google
  publishes the current network-ID list). NOT required for test ads; omitted here, add at release.
- Both are tracked as ADS-1+/Sprint-10 follow-ups, not ADS-0.

---

## CI implications

- **Android job (ubuntu):** picks up `play-services-ads` transparently from Google's Maven (already a
  configured repo). No workflow change. `assembleDebug` / `lintDebug` / unit tests stay green.
- **iOS job (macos):** the `:shared` framework link + `:shared:iosSimulatorArm64Test` are UNAFFECTED
  by the SPM package (the Kotlin framework has no GoogleMobileAds dependency by design). However,
  **once the SPM package is added**, the `xcodebuild build` step must RESOLVE it. `gradle/actions`
  caching does not cover SwiftPM; expect xcodebuild to fetch the package on first run (network +
  ~minute). To keep CI deterministic, commit `Package.resolved` (it pins the version) — `xcodebuild`
  resolves from it. No extra CI flag is strictly required, but adding `-skipPackagePluginValidation`
  is harmless if a plugin-trust prompt ever appears. Until the package is added, CI is unchanged.

---

## Release-time real-ID injection plan (NO secrets committed)

- The real AdMob **App ID** and **ad-unit IDs** are account secrets. They must NOT be committed.
- Plan: inject them at release from gitignored config —
  - Android: a `secrets.properties` (gitignored) read in `build.gradle.kts` → `manifestPlaceholders`
    for the `APPLICATION_ID` meta-data, and `BuildConfig` fields for the unit IDs; CI provides them
    via repository secrets. (`google-services.json` is also gitignored in case it is ever needed.)
  - iOS: the real `GADApplicationIdentifier` via an xcconfig / build setting fed from a gitignored
    file (or `GoogleService-Info.plist`, gitignored); unit IDs via a generated config.
- `.gitignore` already excludes: `secrets.properties`, `google-services.json`,
  `GoogleService-Info.plist`, `*.keystore`, `*.jks` (added in this spike as a precaution).
- The test IDs stay as the *default*/debug values so debug builds keep showing test ads.

---

## Notes for ADS-1 (generalize the AdProvider — fakeable)

- Split `load` and `show` into separate suspend steps; cache a loaded ad and expose `isReady`.
- Add **interstitial** alongside rewarded (Android test interstitial
  `ca-app-pub-3940256099942544/1033173712`, iOS `…/4411468910`); generalize `AdResult` (separate
  load vs show errors; reward amount/type for rewarded).
- Keep `NoOpAdProvider` + add a configurable fake (return a scripted `AdResult` sequence) so
  presentation-layer logic (ADS-2/4 placements: game-over rewarded continue, interstitial cadence)
  is unit-testable in commonTest with no SDK.
- Replace `AdActivityHolder` with an `ActivityLifecycleCallbacks`-based provider in `FuseApplication`.
- Move the trigger off the debug Settings row into real placements behind a build-time flag.
