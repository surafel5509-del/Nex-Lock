# NexLock

A private, offline-first Android app locker: authentication gate for the apps you choose, an
encrypted local vault for photos/videos/documents/notes, a local security journal, optional
intruder detection, and schedule/Wi-Fi/place automation.

NexLock has **no INTERNET permission**. Nothing is uploaded, synced or sold; every byte stays in
the app sandbox.

> **Build status:** the project is code-complete, but it has **not been compiled in the environment
> where it was authored** (no Android SDK and no access to Google/Maven repositories there). See
> [Verification status](#verification-status) before relying on it for a release.

---

## Feature overview

| Area | What it does |
|---|---|
| **Dashboard** | Animated shield, security score with contributing factors, protection switch, protected-app overview, recent local activity, recommendations, temporary-unlock countdown, quick lock/unlock. |
| **App locking** | Lists launchable apps, search, category filter, multi-select bulk lock/unlock, per-app toggle, configurable re-lock timeout, immediate re-lock when leaving an app, temporary unlock, protection kill-switch. |
| **Authentication** | PIN, 3×3 pattern or password; BiometricPrompt (fingerprint/face) with credential fallback; change credential; recovery code; per-attempt lockout with progressive backoff (30 s → 1 min → 5 min → 15 min, capped at 1 h); lockout countdown on the lock screen. |
| **Lock screen** | Animated logo, protected app icon/name, PIN keypad, pattern grid, password field, biometric button, error feedback, emergency "go home" action, back never reveals the app. |
| **Vault** | AES-256-GCM encrypted photo/video/document storage and encrypted notes, private gallery, in-app preview, export through the system file picker, secure delete. |
| **Intruder detection (opt-in)** | Captures an incident after N consecutive failures, optional front-camera photo (explicit permission), local-only encrypted incident history, per-record and bulk deletion. |
| **Automation** | Time schedules with day masks, trusted Wi-Fi networks (SSID), trusted places (geofence-style distance check), named lock profiles, temporary unlock. |
| **Security** | Android Keystore AES-256-GCM (StrongBox → TEE → software fallback), PBKDF2-HMAC-SHA256 (210 000 iterations + device-bound pepper), FLAG_SECURE, sensitive clipboard flags, root/debug/emulator/tamper indicators, local security log with retention policy. |
| **Backup** | Passphrase-encrypted portable backup (PBKDF2 + AES-256-GCM) written through the Storage Access Framework; restore merges configuration and notes. |
| **Settings** | Authentication, appearance (dark/light/system, Material You, haptics), language (English/አማርኛ/Español), security, notifications, automation, backup, security log, about, privacy policy, terms. |

### Required special access (explained, never hidden)

| Access | Why | If you refuse |
|---|---|---|
| **Accessibility service** (optional, recommended) | Learns *which package* came to the foreground so the lock screen appears instantly. Configured with `canRetrieveWindowContent="false"`, `canPerformGestures="false"`, event type `typeWindowStateChanged` only. | NexLock falls back to usage-access polling (~0.9 s latency). |
| **Usage access** (optional) | Foreground detection fallback using `UsageStatsManager`. | Only the accessibility path is available. |
| **Camera** (optional) | One silent-UI front-camera frame after repeated failed unlocks, only if intruder photos are enabled. | Intruder incidents are still recorded, just without a photo. |
| **Location** (optional) | Read the Wi-Fi SSID and evaluate trusted places. Foreground-only, never in the background. | Wi-Fi/place automation is unavailable. |
| **Notifications** | Security alerts and a quiet "protection running" notification. | Alerts are silent; enforcement still works. |
| **Battery optimisation exemption** (optional) | Prevents the OS from killing the polling fallback monitor. | Monitoring may pause while the device is idle. |

NexLock never requests `INTERNET`, `READ_EXTERNAL_STORAGE`, `READ_SMS`, `READ_CONTACTS`,
`RECORD_AUDIO`, `ACCESS_BACKGROUND_LOCATION` or `QUERY_ALL_PACKAGES`; a test
(`ManifestPolicyTest`) fails the build if any of them reappear.

---

## Architecture

Clean Architecture with MVVM, unidirectional data flow and Hilt throughout:

```
app                     Android shell: manifest, navigation, lock Activity, services, DI glue
 ├── feature-dashboard         dashboard + security posture UI
 ├── feature-app-lock          app picker + lock screen
 ├── feature-authentication    credential setup / change / recovery
 ├── feature-vault             encrypted vault + notes
 ├── feature-intruder-detection
 ├── feature-automation
 ├── feature-settings
 ├── core-design               theme, typography, components, localized strings
 ├── core                      repositories + use-case-style coordinators (the domain layer)
 │    ├── core-database        Room entities, DAOs, converters
 │    ├── core-security        Keystore crypto, hashing, biometrics, risk, privacy guards
 │    └── core-common          pure Kotlin models + policies (lockout, scoring, schedules)
```

* **UI** never touches Room or the Keystore directly; it observes `StateFlow` from a ViewModel.
* **Repositories** own persistence and crypto; `LockEngine` is the single decision point shared by
  the accessibility service and the polling monitor, which makes the lock policy testable.
* **Domain policies** in `core-common` are pure Kotlin and covered by JVM unit tests.
* Full details: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

### How locking actually works

1. A monitoring trigger (accessibility event or usage-stats poll) reports a foreground package.
2. `LockEngine.onForegroundApp()` asks: protection on? app locked? session already unlocked?
   automation forcing a lock? → `Ignore` or `Lock(packageName, appName)`.
3. `LockLauncher` shows `LockActivity` (own task, excluded from recents, translucent).
4. On success `LockEngine.onUnlockSucceeded()` opens a session bounded by the configured timeout;
   leaving the app and screen-off relock as configured.

---

## Security model (read this)

Implemented protections:

* AES-256-GCM via `AndroidKeyStore` with per-file random IVs; StrongBox used when the device
  reports it, otherwise TEE, otherwise software-backed keys (surfaced in Settings → Security).
* Credentials: PBKDF2-HMAC-SHA256, 210 000 iterations, 128-bit random salt, plus an HMAC pepper held
  in the Keystore. Plaintext credentials are never written, logged or exported.
* Constant-time hash comparison; progressive lockout; recovery code with its own hash.
* Vault media is encrypted before it touches disk; temporary viewer files are written to
  `filesDir/secure_tmp` and wiped after use.
* `FLAG_SECURE` on sensitive screens (toggleable), clipboard sensitivity flag on Android 13+.
* Backups are opt-in, passphrase-encrypted, and never contain the Keystore-bound credential hashes
  or vault media.
* No network permission at all, no analytics, no third-party SDKs.

Known limits — NexLock cannot and does not claim to:

* stop a rooted device owner, a custom ROM, ADB with an unlocked bootloader, or a modified APK from
  bypassing an app-level gate;
* prevent uninstallation (Android gives no such guarantee to non-device-owner apps);
* recover data if you forget both your credential and your recovery code — hashes are one-way;
* protect screenshots taken with an external camera;
* guarantee that manufacturer battery management will not kill the fallback monitor (hence the
  battery-optimisation guidance);
* guarantee that every OEM allows a foreground service to start the lock activity on Android 10+
  (the accessibility service is the reliable path and is therefore the recommended one; the
  usage-access fallback is best-effort and surfaces its own state in the UI).

Root/emulator/debugger/signature results are **indicators**, not verdicts.

---

## Building

### Requirements

* Android Studio Ladybug (or newer) / command-line Android SDK
* JDK 17 (`JAVA_HOME` pointing at it)
* Android SDK Platform 34 + Build-Tools 34.0.0
* Gradle 8.7 (wrapper included — `./gradlew`)
* ~4 GB RAM for the Gradle daemon

### Debug build

```bash
git clone https://github.com/surafel5509-del/Nex-Lock.git
cd Nex-Lock
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

First launch opens credential setup; pick PIN, pattern or password, then enable protection and grant
the accessibility service from the permission cards.

### Release build (signed, minified)

1. Create your key:

   ```bash
   keytool -genkeypair -v -keystore ~/nexlock-release.jks -alias nexlock \
           -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Copy `keystore.properties.sample` to `keystore.properties` and fill it in (git-ignored).

3. Supply the certificate digest used for the runtime integrity check:

   ```bash
   keytool -list -v -keystore ~/nexlock-release.jks -alias nexlock | grep SHA256
   ./gradlew :app:assembleRelease -Pnexlock.certSha256=AA:BB:...   # colon-separated or hex
   ```

   Leaving it empty disables the signature comparison (the app reports "Not configured").

4. Build:

   ```bash
   ./gradlew :app:assembleRelease
   # APK: app/build/outputs/apk/release/app-release.apk  (signed when keystore.properties exists)
   ./gradlew :app:bundleRelease     # Play-ready AAB
   ```

R8/ProGuard rules live in `app/proguard-rules.pro` (Room, Hilt, enums, Compose).

### Release hardening checklist

* [ ] Fill `keystore.properties` and `-Pnexlock.certSha256`.
* [ ] `./gradlew lint` is clean and `assembleRelease` shrinks successfully.
* [ ] Test on a physical device: accessibility lock, usage-access fallback, biometrics, vault,
      intruder capture, backup/restore.
* [ ] Confirm the accessibility fallback behaviour on the OEM skins you support.
* [ ] Publish the privacy policy (also embedded in-app).

---

## Testing

```bash
./gradlew test                 # JVM unit tests (policies, crypto helpers)
./gradlew connectedAndroidTest # instrumented tests (needs a device/emulator)
```

* Unit tests: `core-common` (lockout policy, credential validation, schedule/automation evaluation,
  security scoring), `core-security` (hashing, portable crypto, backup codec, risk assessment,
  chunked stream), `core-database`-adjacent helpers.
* Instrumented: Room DAO behaviour (`core-database`), Hilt dependency-graph smoke test, manifest
  privacy invariants (app).
* Inventory and manual test matrix: [`TESTING.md`](TESTING.md).

---

## Verification status

This repository was generated in a sandbox **without** a JDK/Android SDK and **without** access to
`dl.google.com` or Maven Central. Consequently:

* ❌ `./gradlew assembleDebug` has **not** been executed against this tree.
* ❌ No unit or instrumented test has been executed.
* ✅ Everything is statically consistent: imports resolve to declarations in the tree, module
  dependencies are declared, and the manifest/DI wiring is complete — but expect normal
  first-build issues (dependency versions, a lint warning) when you build locally.

If a build fails, open an issue with the Gradle output; the most likely causes are dependency
version drift and Kotlin/Compose compiler version alignment.

---

## Privacy policy and terms

Both are embedded in the app (Settings → About) and stored in
`feature-settings/src/main/java/com/nexlock/feature/settings/LegalContent.kt`.

## Licence

Provided as-is for personal use. AndroidX, Compose, Hilt, Room and Kotlin coroutines keep their own
licences.
