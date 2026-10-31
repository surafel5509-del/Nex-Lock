# Testing NexLock

> **Status:** the JVM unit tests run on every push in GitHub Actions and are green
> (`.github/workflows/android-build.yml`, job "Assemble APKs + unit tests"). The instrumented suites
> are wired into the same workflow behind manual dispatch and have **not** been executed yet. The
> manual matrix below is still a checklist, not a test report.

## Automated tests

### JVM unit tests (`./gradlew test`)

| Module | File | Covers |
|---|---|---|
| core-common | `LockoutPolicyTest` | free attempt budget, progressive delays, 1 h cap, no lockout before the budget is spent, remaining time never negative |
| core-common | `CredentialValidatorTest` | PIN 4–8 digits, pattern ≥ 4 unique cells, password length/variety, dispatch by `AuthMode` |
| core-common | `ScheduleCalculatorTest` | daytime window, overnight window across midnight, zero-length/disabled windows, first-active-window search, minute formatting |
| core-common | `AutomationEvaluatorTest` | temporary unlock precedence, trusted network/place relaxation, schedule forcing, disabled automation |
| core-common | `SecurityScoreCalculatorTest` | empty posture at risk + recommendations, perfect posture = 100/optimal, explainable factors |
| core-common | `PolicyPerformanceTest` | 100 schedules evaluated in microseconds, lockout delay growth |
| core-security | `SecretHasherTest` | PBKDF2 stability, per-salt uniqueness, constant-time verification |
| core-security | `PortableCryptoTest` | passphrase encryption round-trip, wrong passphrase rejection, tamper detection |
| core-security | `BackupCodecTest` | payload encode/decode round-trip, version handling, malformed input rejection |
| core-security | `SecurityRegressionTest` | IV uniqueness, ciphertext ≠ plaintext, no secrets in serialized output |

### Instrumented tests (`./gradlew connectedAndroidTest`)

| Module | File | Covers |
|---|---|---|
| core-database | `NexLockDatabaseTest` | locked-app flows, lock/unlock-all, security-event ordering/pruning, vault byte aggregation, ciphertext-only notes, incident deletion, enum converters and profile activation |
| core-security | `KeystoreCryptoManagerTest` | real Android Keystore AES-GCM encrypt/decrypt, random IVs, alias persistence |
| app | `DependencyGraphTest` | Hilt graph constructs every singleton repository + `LockEngine` + `MonitorCoordinator` |
| app | `ManifestPolicyTest` | merged manifest declares no INTERNET/network/storage/contacts/SMS/audio/background-location permissions, keeps biometric + foreground-service + boot permissions, and has OS backup disabled |

The app's instrumentation runner is `com.nexlock.HiltTestRunner` (swaps in `HiltTestApplication`).

## Manual test matrix (must be run on a physical device before release)

### Onboarding & credentials
1. Fresh install → credential setup appears; PIN, pattern and password each validate, reject weak input.
2. Strong password accepted, short PIN rejected, pattern with repeated cell rejected.
3. Recovery code is shown once, copied/recorded; recovery with the correct code resets the credential.
4. Wrong credential 5× → countdown appears; wait it out → unlock works again.
5. Change credential from Settings → Authentication; old credential no longer works.

### Locking
6. Enable accessibility, lock one app, open it → lock screen appears instantly with the right icon/name.
7. Repeat with accessibility disabled but usage access enabled → lock appears within ~1 s.
8. Unlock → app visible; press Home, reopen → locked again (per configured timeout).
9. Set timeout to "Instant reprompt" → switching away and back re-locks.
10. Temporary unlock from dashboard → locked apps open without prompt until the countdown ends.
11. Disable protection → no app is locked; re-enable → locking resumes.
12. Back from the lock screen → returns home, never reveals the protected app.
13. Toggle "Block screenshots" → recents thumbnail and screenshots are blank on the lock screen.

### Vault
14. Import photo, video, document via the system picker; verify files are unreadable in the app sandbox (encrypted).
15. Preview image/video, view document metadata, export one item, delete with confirmation.
16. Create and edit a note; verify the DB stores ciphertext only (`secure_notes.titleCipher`).
17. Import a file larger than 200 MB → rejected gracefully with a message.

### Intruder detection
18. Enable detection, deny camera → incidents still recorded, toggle re-requests the permission.
19. Grant camera, fail 3× → incident with photo listed; delete one and clear all → records and files gone.
20. Verify no camera usage when the feature is off.

### Automation
21. Schedule that covers "now" with today's day enabled → protection forced; temporary unlock overrides it.
22. Trusted Wi-Fi: connect to the saved SSID → protection relaxed; disconnect → locking resumes.
23. Trusted place with a small radius → distance evaluation matches; revoke location → rule reports unavailable.
24. Create and activate a lock profile; only one profile is active at a time.

### Settings, backup, notifications
25. Theme switch (system/dark/light), dynamic colour, haptics, language switch → all apply immediately.
26. Security log records lock/auth/permission events; filter by severity; clear log.
27. Create backup with an 8+ character passphrase → file written via SAF; restore on a reinstalled app with the same passphrase.
28. Wrong passphrase on restore → clear error, existing data untouched.
29. Notifications: disable → no alerts; re-enable → incidents alert; confirm protected app names never appear.
30. Revoke the accessibility permission while protected → dashboard/settings show the degraded state.

### Edge cases
31. Device reboot with protection on → monitor restored where the OS allows, otherwise state is reported honestly.
32. Kill the app from recents while protection is on → lock screen still enforced after reopening a protected app.
33. Rotate the device and use split-screen with a protected app.
34. Multiple user profiles / work profile: verify behaviour is documented (app-level lock is per profile).

## Gaps (explicitly not covered)

* No screenshot/golden tests for the Compose UI.
* No automated end-to-end test of the accessibility service or the polling monitor (needs UI-automation
  privileges; the policy itself is unit-tested through `LockEngine`'s collaborators).
* No performance/memory benchmarks on low-end devices.
* No OEM (Samsung/Xiaomi/Huawei/Techno/Infinix) matrix results yet.
