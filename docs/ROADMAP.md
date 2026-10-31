# NexLock — Implementation Roadmap

Everything below is **implemented and now compiles**. The authoring sandbox had no JDK/Android SDK,
so compilation and the JVM test suite run on GitHub Actions (JDK 17, SDK 34). The "Build verified"
column records that: `:app:assembleDebug` and `:app:assembleRelease` both succeed and
`testDebugUnitTest` is green. Runtime behaviour on a physical device is still unverified.

| Phase | Scope | Modules | Code | Build verified |
|---|---|---|---|---|
| 0 | Gradle Kotlin DSL, version catalog, 13-module skeleton, docs | root | ✅ | ✅ |
| 1 | Domain models, lockout/credential/schedule/automation/scoring policies + 6 JVM test classes | core-common | ✅ | ✅ |
| 2 | Keystore AES-256-GCM, peppered PBKDF2, biometrics, risk assessment, secure files, screenshot/clipboard guards, intruder capture | core-security | ✅ | ✅ |
| 3 | Room schema (9 tables), DAOs, converters, Hilt DB module | core-database | ✅ | ✅ |
| 4 | DataStore settings, auth, locked apps, unlock sessions, lock engine, vault, intruders, automation, profiles, backup, notifications, posture, DI | core | ✅ | ✅ |
| 5 | Design system: dark/light themes, gradients, glass cards, shield animation, skeletons, keypad, pattern grid, 3-language strings | core-design | ✅ | ✅ |
| 6 | Dashboard + security posture | feature-dashboard | ✅ | ✅ |
| 7 | App picker, filters, bulk locking, lock screen | feature-app-lock | ✅ | ✅ |
| 8 | Credential setup/change/recovery, biometric toggle | feature-authentication | ✅ | ✅ |
| 9 | Photo/video/document vault, encrypted notes, import/export | feature-vault | ✅ | ✅ |
| 10 | Intruder detection settings + private incident history | feature-intruder-detection | ✅ | ✅ |
| 11 | Schedules, trusted Wi-Fi/places, profiles, temporary unlock | feature-automation | ✅ | ✅ |
| 12 | Settings hub, appearance, security, notifications, log, backup, legal | feature-settings | ✅ | ✅ |
| 13 | Manifest (no INTERNET), navigation shell, lock activity, accessibility + polling services, boot receiver, DI glue | app | ✅ | ✅ |
| 14 | JVM + instrumented tests, privacy invariants test, README/TESTING, release signing + R8 config | all | ✅ | ✅ |

## What must happen next (in order)

1. ~~`./gradlew :app:assembleDebug` on a machine with JDK 17 + SDK 34~~ — done on GitHub Actions.
2. Run the instrumented suites (`connectedAndroidTest`) on an emulator via the workflow's manual
   dispatch, then triage failures.
3. Execute the manual matrix in [TESTING.md](../TESTING.md) on at least one stock-Android device and
   one OEM skin (background-activity-start behaviour of accessibility services differs).
4. Fill `keystore.properties` + `-Pnexlock.certSha256`, then `assembleRelease` and repeat the matrix
   on the minified build (R8 can break reflection: Room, Hilt, enums).
5. Only then consider the release-ready claim in the README to be satisfied.

## Deliberately out of scope

* Cloud sync, accounts, analytics, ads (would require INTERNET and contradict the privacy stance).
* Device-admin / device-owner anti-uninstall (not appropriate for a consumer app; the README says so).
* Hidden or background camera/audio capture (illegal in many jurisdictions and unethical).
* Guaranteeing protection on rooted devices — NexLock reports indicators and states its limits.
