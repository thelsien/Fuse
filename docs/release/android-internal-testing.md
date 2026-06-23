# Android release signing + AAB for Play Console internal testing (REL)

Goal: produce a **signed Android App Bundle (`.aab`)** to upload to **Google Play Console internal
testing**. An internal-testing build is the prerequisite for configuring the **`remove_ads`** in-app
product and observing a real Android billing round-trip (see `docs/iap/IAP-0-gotchas.md` →
"Android — real purchases").

This is a **build-config + runbook** task. No app code or behavior changes. Signing is read from a
**gitignored `keystore.properties`**; CI and fresh clones (no keystore) still configure and build an
unsigned release.

---

## How signing is wired (already done in the repo)

- `androidApp/build.gradle.kts` loads `keystore.properties` from the **repo root** at configuration
  time. If it exists, it creates a `release` signing config and assigns it to `buildTypes.release`.
  **If it's absent, the release build is unsigned and configuration still succeeds** — so CI and
  clones without a keystore are unaffected.
- `versionCode = 1`, `versionName = "1.0"`, `applicationId = "com.thelsien.fuse"` (the `namespace`
  stays `com.fuse.android` — internal R/BuildConfig only; Play keys on `applicationId`).
- `release.isMinifyEnabled = false` for now — R8/proguard keep-rules for the AdMob + Play Billing
  SDKs are a later REL task; not needed for internal testing.
- `keystore.properties` and `*.jks` / `*.keystore` are **gitignored**. Only
  `keystore.properties.template` is committed.

---

## 1. Generate an upload keystore

Run once. This creates `upload-keystore.jks` with an `upload` key alias (RSA 2048, ~27 years valid).
Put it in the repo root (it's gitignored) or anywhere you like — just point `storeFile` at it.

```bash
keytool -genkeypair \
  -keystore upload-keystore.jks \
  -alias upload \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -storetype JKS \
  -dname "CN=Fuse, OU=Mobile, O=Fuse, L=, ST=, C=US"
```

It will prompt for a **store password** and a **key password** (you may use the same for both).
Keep these safe — losing them means you can't sign updates with this upload key (though with Play
App Signing you can reset the upload key via Play support).

## 2. Create `keystore.properties` (gitignored)

Copy the template and fill in your values:

```bash
cp keystore.properties.template keystore.properties
```

Edit `keystore.properties` (repo root):

```properties
storeFile=upload-keystore.jks
storePassword=<your store password>
keyAlias=upload
keyPassword=<your key password>
```

`storeFile` is resolved relative to the **repo root**. Confirm `git status` does NOT list
`keystore.properties` or `upload-keystore.jks` (both are gitignored).

## 3. Build the signed bundle

AGP 8.9.3 needs **JDK 17** (it breaks on JDK 25). From the repo root:

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 17)" ./gradlew :androidApp:bundleRelease
```

Output:

```
androidApp/build/outputs/bundle/release/androidApp-release.aab
```

With `keystore.properties` present this `.aab` is **signed** with your upload key. (Without it, the
same command still builds an **unsigned** bundle — useful for CI smoke checks.)

Verify the signature (optional):

```bash
JAVA_HOME="$(/usr/libexec/java_home -v 17)" \
  "$ANDROID_HOME"/build-tools/*/apksigner verify --verbose \
  androidApp/build/outputs/bundle/release/androidApp-release.aab
# (apksigner works on AABs for jarsigner-style v1 checks; or use `jarsigner -verify`.)
```

---

## 4. Play Console — internal testing + `remove_ads` IAP

Do these in [Play Console](https://play.google.com/console):

1. **Create the app.** Its package name MUST be **`com.thelsien.fuse`** (matches `applicationId`).
   This is permanent in Play Console — get it right.
2. **Enable Play App Signing** (default for new apps). You upload with the **upload key** from step 1;
   Google holds the real app-signing key and re-signs. Keep your upload key safe but recoverable.
3. **Internal testing track** → **Create new release** → upload
   `androidApp/build/outputs/bundle/release/androidApp-release.aab`. Add release notes, roll out.
4. **Testers:** add an internal-testing tester list (Google accounts), and share the opt-in URL.
   Each tester must accept the invite and install from that link.
5. **License testers** (Setup → License testing, account-level): add the SAME tester accounts here so
   their in-app purchases are **free sandbox transactions** (not real charges).
6. **In-app product:** Monetize → Products → **In-app products** → Create product:
   - **Product ID: `remove_ads`** — this MUST match exactly what the app queries
     (`com.fuse.iap.Iap.PRODUCT_REMOVE_ADS = "remove_ads"`). The product ID is permanent.
   - Type: **non-consumable** (one-time / managed product). Set a price (~$3.99 placeholder).
   - **Activate** the product.

> Play only serves billing to a **recognized, uploaded** application id on a published-to-a-track
> build, with an **active** product. Until all three line up, `queryProductDetails("remove_ads")`
> returns nothing — see `docs/iap/IAP-0-gotchas.md` → "Android — real purchases".

## 5. Verify the billing round-trip

Install the app from the internal-testing opt-in link on a device signed into a **license-tester**
account. In the app: Settings → "Buy Remove Ads (spike)" (the IAP-0 debug trigger; requires
`IapDebug.enabled`). It should load the localized price from Play and complete a sandbox purchase.
No code change is needed — the Play Billing path is already implemented (IAP-0..3).

---

## Cross-references

- `docs/iap/IAP-0-gotchas.md` — billing seam, Play Billing 9.x API notes, the exact
  "Android — real purchases" prerequisites this doc satisfies.
- `keystore.properties.template` — the committed template for step 2.
