# ANL-1 — Analytics seam (debug now, Firebase later)

Sprint 9. Product decision (locked): build the analytics plumbing now behind a **debug-verifiable**
logger; wire **real Firebase LATER** when the user creates the Firebase project. No Firebase SDK or
config is added in ANL-1. **No data leaves the device** under the debug logger. **No PII.**

## What ANL-1 shipped

- `com.fuse.analytics.AnalyticsLogger` — the generic seam: `logEvent(name, params)` (+ optional
  `setUserProperty(name, value)`, never an identifier).
- `NoOpAnalyticsLogger` — safe default, does nothing.
- `DebugAnalyticsLogger` — prints each event to the device console via the
  `expect fun analyticsDebugPrint(line: String)` seam:
  - **Android** → `android.util.Log.d("Fuse-Analytics", …)` → logcat.
  - **iOS** → `NSLog("%@", …)` → Xcode console / Console.app.
  Lines are prefixed `[Fuse-Analytics] event=<name> {k=v …}`.
- `FakeAnalyticsLogger` (commonMain) — records `loggedEvents` / `userProperties` for tests.
- `sanitizeParams(params)` — defensive no-PII backstop that strips identifier-keyed params.
- `expect val platformAnalyticsModule: Module` (commonMain) with `androidMain`/`iosMain` actuals
  binding `DebugAnalyticsLogger` on **both** platforms. Registered in `appModules`
  (`com.fuse.di.Modules`). This satisfies the "client analytics initialized on both platforms"
  acceptance criterion — a live, resolvable, debug-verifiable logger.

Verify it: trigger any event (ANL-2 adds real call sites) and look for the `[Fuse-Analytics]` line in
`adb logcat -s Fuse-Analytics` (Android) or the Xcode console (iOS).

## The Firebase-later swap (when the user creates the project)

Everything in the app depends only on the `AnalyticsLogger` interface, so wiring Firebase is a
**single binding change** — no call site changes.

1. Create the Firebase project; add the Android app + iOS app.
2. Drop `google-services.json` (Android) and `GoogleService-Info.plist` (iOS) into the
   **already-gitignored** config locations (same pattern as the ads/billing real IDs). Do NOT commit
   them.
3. Add the analytics SDK. Two viable routes:
   - **GitLive KMP wrapper** — `dev.gitlive:firebase-analytics` in `:shared` commonMain; one
     `FirebaseAnalyticsLogger` in commonMain mapping `logEvent` → `Firebase.analytics.logEvent(...)`.
   - **Native per-platform** — `com.google.firebase:firebase-analytics` (Android) +
     FirebaseAnalytics via SwiftPM/CocoaPods (iOS), with a `FirebaseAnalyticsLogger` actual on each
     platform.
4. Replace the `single<AnalyticsLogger> { DebugAnalyticsLogger() }` binding in
   `platformAnalyticsModule` (`PlatformAnalytics.android.kt` / `PlatformAnalytics.ios.kt`) with the
   `FirebaseAnalyticsLogger`. Optionally keep `DebugAnalyticsLogger` for debug builds (compose both,
   or pick by build flag).
5. Call Firebase init at app start if the SDK requires it (Android: google-services plugin
   auto-inits; iOS: `FirebaseApp.configure()` in the app delegate / app entry).

## No-PII rules (carry into ANL-2)

- Events carry **no personal identifiers**: no device ids, IDFA/GAID, emails, account/user ids,
  names, phone, location, or user-entered text.
- Params are **enums / counts / booleans** only. The ANL-2 taxonomy enforces this at the call sites;
  `sanitizeParams` is only a backstop.
- `setUserProperty` is for **coarse, non-identifying segments** only (e.g. equipped theme), never an
  identifier.

## ANL-2 (next) — instrument events at their call sites via this logger

`game_start`, `game_over`, `daily_completed`, `share_tapped`, `ad_impression`,
`ad_reward_granted`, `iap_purchase`, `ftue_step`. Sample high-frequency events; keep params
PII-free (enums/counts). Inject `AnalyticsLogger` via Koin at each site; assert with
`FakeAnalyticsLogger`.
