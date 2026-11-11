## Forma Store 0.2.0

- Pinned search, recent searches, filter reset and per-tab scroll state.
- App screenshot gallery and release notes when provided by F-Droid; expandable descriptions, Open, Share and system app settings.
- Installed-to-available version comparison and download-all.
- Persistent download queue with foreground transfer notification and individual queued-item removal.
- Light/dark/system themes, wallpaper or branded colors, reduced dock motion, JSON settings export/import.
- Offline indicator, cached catalog timestamp and icon loading fallback.

### Known limitations
- Interrupted APK downloads restart from zero after Android terminates the process. Not a byte-range resume implementation.
- Install outcome is reconciled on return. It does not yet provide detailed PackageInstaller status codes.
- Media metadata currently re-reads the verified full repository index on opening a detail sheet; this can be slow.
- Foreground service limits and battery policies still apply. No silent installation.
- Basic platform accessibility is retained; no dedicated TalkBack overhaul in this version.

The release verifier requires the same package and signing certificate as 0.1.0 and a larger versionCode. Do not uninstall 0.1.0 to update.
