# NexLock — Architecture

> **Verification note:** this document describes the code as written. The project has not been
> compiled here (no Android SDK/JDK/network in the authoring sandbox); see the verification section
> in the [README](../README.md).

NexLock is a **multi-module, offline-first Android application** built with Kotlin, Jetpack Compose,
Material 3, Clean Architecture, MVVM, Hilt, Room, DataStore, Android Keystore and BiometricPrompt.

## 1. Module graph

```
                           ┌───────────────────────────┐
                           │            app            │  (Manifest, nav graph, monitor services)
                           └────────────┬──────────────┘
        ┌───────────────┬───────────────┼────────────────┬────────────────┬─────────────────┐
        │               │               │                │                │                 │
 feature-dashboard  feature-app-lock  feature-vault  feature-intruder  feature-automation  feature-settings
        │               │               │ detection      │                │                 │
        └───────────────┴───────┬───────┴────────────────┴────────────────┴─────────────────┘
                                │
                    ┌───────────┴────────────┐
                    │      feature-authentication (shared auth screens / components)
                    └───────────┬────────────┘
                                │
                ┌───────────────┼────────────────┐
                │               │                │
              core       core-design      (composition root: repositories,
                │               │           engines, automation, backup)
        ┌───────┼───────────────┼───────────────┐
        │       │               │               │
 core-security  core-database  core-common   (pure Kotlin contracts)
```

* **core-common** — pure Kotlin: domain enums, `LockoutPolicy`, `SecurityScoreCalculator`,
  `AutomationEvaluator`, formatting helpers, dispatchers, logging façade. No Android UI, no Room.
* **core-security** — Android Keystore AES-256-GCM crypto, peppered PBKDF2 credential hashing,
  BiometricPrompt wrapper, root/debug/emulator/signature risk assessment, secure file store,
  secure clipboard + screenshot protection, CameraX intruder capture.
* **core-database** — Room entities, DAOs, converters, `NexLockDatabase`, Hilt database module.
* **core** — repositories and domain use-cases shared by every feature: settings (DataStore),
  authentication + lockout, locked-app registry, unlock sessions, vault, incidents, automation,
  backup/restore, notifications and permission status. This is the only module features depend on
  for business logic.
* **core-design** — theme, typography, gradients, glass surfaces, skeleton shimmer, animated shield,
  keypad, pattern grid, settings rows, localized string table (`LocalStrings`).
* **feature-*** — one compose screen set + `@HiltViewModel` per feature. Features never depend on
  each other; the `app` module wires navigation.
* **app** — `NexLockApplication` (Hilt), `MainActivity`, `LockActivity`, navigation, the accessibility
  service and the foreground monitor service.

## 2. Layer rules

| Layer | Module | May depend on | Must never |
|---|---|---|---|
| Domain primitives | core-common | Kotlin stdlib | Android UI, IO |
| Security primitives | core-security | core-common | Room, Compose feature code |
| Persistence | core-database | core-common | Compose |
| Repositories / use cases | core | core-common, core-security, core-database | Compose |
| Presentation | core-design, feature-* | core, core-design | Room DAOs directly |
| Composition root | app | everything | business logic |

**SOLID in practice**
* *Single responsibility* — a DAO never decides policy, a repository never renders, a ViewModel never
  touches the Keystore.
* *Open/closed* — new authentication modes extend `AuthMode` + a validator, not `AuthRepository`.
* *Liskov* — `CryptoManager`, `SecretHasher`, `PepperProvider` are interfaces with one Android impl and
  test doubles.
* *Interface segregation* — small repositories (`LockedAppsRepository`, `VaultRepository`, …) rather
  than one god-repository.
* *Dependency inversion* — Hilt binds implementations; the only place concrete Android crypto classes
  are named is the DI module.

## 3. State & data flow

```
Room (Flow)  ─┐
DataStore    ─┼─► Repository ─► ViewModel (StateFlow<UiState>) ─► Composable (stateless)
Keystore     ─┘
```

* ViewModels expose a single immutable `UiState` plus one-shot `Channel`/`SharedFlow` events.
* Composables are stateless where possible (`Screen(state, onEvent)`), which makes them UI-testable.
* Every suspending repository call runs on `DispatcherProvider.io`; UI collection is lifecycle-aware.

## 4. Lock enforcement pipeline

```
AccessibilityService (real-time window changes) ─┐
                                                 ├─► LockEngine ─► decision ─► LockActivity (FLAG_SECURE)
Foreground monitor service (UsageStats poll) ────┘        │
                                                          ├─ AutomationEvaluator (schedule / Wi-Fi / location)
                                                          ├─ UnlockSessionManager (session + temp unlock)
                                                          └─ LockedAppsRepository
```

* Enforcement requires **either** the Accessibility service (recommended, real-time) **or** the
  usage-access special permission (polling, ~1 s latency). Both are user-granted, revocable, and
  openly explained in-app. NexLock never claims these are unbypassable.
* `LockEngine` is the single decision point, which keeps the two triggers consistent and makes the
  policy unit-testable.

## 5. Threading

* `Dispatchers.IO` for disk/Keystore, `Dispatchers.Default` for crypto-heavy PBKDF2, `Main` for UI.
* Services own a `SupervisorJob` scope cancelled in `onDestroy`.
* No global mutable singletons except the documented `@Singleton` in-memory `UnlockSessionManager`
  (cleared on process death by design).

## 6. Error handling

* Repositories return `Result<T>`/sealed outcomes — no exceptions leak to the UI.
* Permission denial, Keystore invalidation, corrupt vault files and Lockout produce typed states the
  UI renders as actionable cards, never crashes.
