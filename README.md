# Forma Store

Native Android F-Droid client without an account. Kotlin, Jetpack Compose and Material 3 dynamic colors. Independent project; not affiliated with Google or F-Droid.

## Implemented in source
- Download and verify the signed F-Droid `index-v1.jar`, pin the official repository certificate SHA-256, then parse the signed JSON. Cache verified catalog; reject rollback versus cached timestamp.
- Search, categories, app details, license, source link, permissions and anti-feature metadata.
- Filter versions by SDK, Android target SDK installation floor and supported ABI.
- Installed-app matching, library and available updates (Android package visibility can limit discovery).
- Single APK download with progress/cancellation, SHA-256 verification, package/version checks, conservative signing-certificate continuity check and Android installer handoff.
- In-app GitHub Releases update sheet; self-update APK checked against current package/version/signing certificate before install handoff.
- Original Peek adaptive/themed icon, dark/light system theme and wallpaper colors.

**Status: first implementation, not yet build- or device-validated.** Do not treat this source as an audited production store. The first full index can use substantial memory; index-v2 streaming and incremental sync are future improvements. No screenshots carousel, parallel downloads, background update notification or unattended install yet. Downloading occurs in a ViewModel, not a persistent foreground service. F-Droid repository index signing proves metadata origin; Android performs final APK signature and installation checks. APK signer rotation is conservatively rejected instead of silently accepted.

## Build
JDK 17, Android SDK 35, Gradle 8.11.1:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

There is not yet a checked-in Gradle wrapper. The **Android APK** workflow installs Gradle and builds/tests, uploads the APK, then runs lint. Look under Actions for the actual status. A committed workflow is not proof of successful compilation.

## Updating without uninstalling
The production package is `dev.formastore.app`. Debug is deliberately separate: `dev.formastore.app.debug`. Do not distribute ephemeral CI debug APKs as a stable update channel.

Stable releases require the same long-lived private signing key every time. The release workflow **refuses** to generate a replacement automatically. Configure repository Actions secrets:
- `FORMA_KEYSTORE_BASE64`
- `FORMA_STORE_PASSWORD`
- `FORMA_KEY_ALIAS`
- `FORMA_KEY_PASSWORD`

Generate a JKS once on a trusted machine and securely back it up. Never commit it, print it in logs, or paste it into issues/chat. Removing a repository does not preserve its old secrets.

`versionCode = major * 10000 + minor * 100 + patch`, each component 0–99. For every release increment `versionName` AND `versionCode`, then push matching tag `v0.1.0`. **Signed release** verifies signing and publishes `forma-store.apk`. A new release is shown in-app, not every commit. First install must use this signed release if later in-place upgrades are desired.

The generic Android “App not installed” message can mean signature conflict, downgrade, incompatible ABI/SDK, storage or a damaged APK. It is not always a signing problem. Forma checks several of these before requesting installation and never recommends uninstalling as an automatic fix.

## Privacy
No account, analytics SDK or ads. Network requests go to F-Droid, GitHub and image/download hosts reached through their redirects; they receive ordinary connection metadata including IP. The app checks its GitHub stable release on startup at most every six hours after successful checks; manual checking is in About. No background push service.

## Branding and license
Code: Apache-2.0 (see LICENSE). Android-inspired robot artwork attribution: see NOTICE. Branding is provisional; no claim of affiliation or trademark clearance.
