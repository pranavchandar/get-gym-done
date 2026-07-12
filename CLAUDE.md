# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Get Gym Done — a single-module, **local-first** Android workout logger. The core app is fully offline (no accounts, no cloud); the **only** networked feature is an *opt-in* Friends layer (anonymous Firebase identity + Firestore) that is **off by default** — see *Friends / social layer* under Architecture. Kotlin 2.0.21 · AGP 8.9.2 · JDK 17 · Compose (Material 3) · Hilt · Room · Firebase (Auth + Firestore). `minSdk` 26, `compile/targetSdk` 36. Package root `com.getgymdone.app`; the only module is `:app`. The README's stack table is partly stale — trust `gradle/libs.versions.toml` and `app/build.gradle.kts`.

## Build / run / deploy

The Gradle wrapper drives everything. `adb`/`emulator` are **not on PATH** — they live under `$LOCALAPPDATA\Android\Sdk\{platform-tools,emulator}`.

```bash
./gradlew :app:compileDebugKotlin     # fast type-check after edits (runs KSP: Hilt + Room)
./gradlew installDebug                # build + install the debug APK to a connected device
```

`installDebug` installs to **every** connected device and fails ambiguously when more than one is attached. Target one with the `ANDROID_SERIAL` env var (find serials via `adb devices`):

- PowerShell: `$env:ANDROID_SERIAL = "emulator-5554"; ./gradlew installDebug`
- Bash: `ANDROID_SERIAL=emulator-5554 ./gradlew installDebug`

Launch after install: `adb -s <serial> shell am start -n com.getgymdone.app/.MainActivity`.

**Building requires `app/google-services.json`** (git-ignored, so a fresh clone won't build until it's added). The `google-services` plugin fails the build if it's missing; the file configures the opt-in Friends backend. Get it from the Firebase console (project `get-gym-done`) → Project settings → the Android app. The core app is otherwise fully functional with Friends never enabled.

**Tests:** none exist yet (no `src/test` or `src/androidTest`), though JUnit/Espresso are wired. If you add them: `./gradlew test` (unit), `./gradlew test --tests "com.getgymdone.app.SomeTest"` (single), `./gradlew connectedAndroidTest` (instrumented). Prefer putting logic in the pure `domain/` layer so it's unit-testable without Android.

**Inspecting the on-device DB** (the app DB is app-private; pull it via `run-as`, and note PowerShell `>` corrupts binary — redirect in Bash or `adb pull`):
```bash
adb -s <serial> exec-out run-as com.getgymdone.app cat databases/gymdone.db > local.db   # plus -wal/-shm
```

## Architecture

**Single Activity, all Compose.** `MainActivity` → `GymDoneTheme` → `AppNavigation`. No fragments, no XML layouts. UI is hand-built from Compose primitives (`Box`/`Column` + `RoundedCornerShape` + custom tokens), not stock Material components — match the surrounding uppercase-label / outlined-card style when adding UI.

**Navigation** (`ui/navigation/`): type-safe Navigation Compose. Every destination is a `@Serializable` member of the `Route` sealed interface; args are constructor params read with `entry.toRoute<Route.X>()`. The start destination is gated on `UserPrefs.onboardingComplete` — `AppNavigation` renders nothing until that flag loads. The four bottom tabs (Today/Workouts/Profile/Settings) are **not** nav destinations; they live inside one `Route.Home` → `MainShell` and swap via a `Crossfade`.

**DI** (Hilt): `GymDoneApp` is `@HiltAndroidApp`. `di/AppModule` provides the Room DB + every DAO as `@Singleton`. Repositories and cross-screen signal holders are `@Singleton` with `@Inject` constructors (no module needed). Screens get their VM via `hiltViewModel()`; VMs are `@HiltViewModel`.

**Data layer** (`data/`): Room is the single source of truth, including user settings — `UserPrefs` is a **single-row Room table** (`UserPrefsDao`), not DataStore (the DataStore dependency is currently unused for prefs). Repositories wrap DAOs and own all business logic; ViewModels never touch DAOs directly. Weights are **always stored in kg** and converted only at the display boundary via `domain/Units.kt` (`WeightUnit`, `kgToDisplay`, `displayToKg`).

**Domain layer** (`domain/`): pure Kotlin, no Android imports — `DayRotation`, `Units`, `ProgressionAdvice`. Put new business rules here.

**ViewModel/UI state pattern** (followed by every screen): the VM exposes `val state: StateFlow<XState>` backed by an immutable `XState` data class. Live screens stay current by `combine(...repository flows...).collect { refresh() }`, where `refresh()` recomputes the whole state object. Screens are stateless renderers of `state`.

### Day scheduling is rotation-based, not calendar-based (important)

There is **no fixed weekly calendar**. The "next" day is derived from your last *completed* session: `nextWorkoutDay()` walks `(lastDayNumber % maxDayNumber) + 1` and **skips rest days** (rest days spawn no session, so plain modulo would stall on them). Rest days are **auto-logged** as `Session` rows with `notes == REST_SESSION_NOTE` (see `SessionRepository.completeRestDay`) so they appear on the calendar/heatmap; training counts filter these out. `maxConsecutiveRestDays()` tells streak/consistency how wide a gap it may bridge without breaking. Because "today is a rest day" is inferred from rotation + same-day logs rather than a date, that logic (in `HomeViewModel.refresh()`) is subtle — trace it before changing it.

During an active workout, **each set is written to the DB the instant it's logged** (`SessionRepository.logSet`), so a kill mid-workout is resumable — `ActiveWorkoutViewModel` reconstructs from the in-progress session. Distinguish in-progress (`completedAt == null`) from completed sessions.

**Cross-screen one-shot events** use a `@Singleton` signal holder polled by the consumer's VM (e.g. `WorkoutCelebrationSignal` fires the Today-page confetti after `WorkoutComplete`), since the two screens are independent nav entries with separate ViewModels.

**Theme**: light/dark + a selectable accent palette (`ui/theme/AccentPalette`), both prefs-driven and applied at the root via `AppViewModel`.

**OS-facing exception to "fully offline"**: `notifications/RestTimer.kt` (`RestTimerScheduler`) schedules an **exact alarm** (`SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`) that posts a `POST_NOTIFICATIONS` notification when a rest period ends, so the alert fires even when the app is backgrounded and its coroutines are frozen. The on-screen countdown is driven separately off wall-clock time; the scheduler only handles the background alert. Keep those two paths in sync when changing rest-timer behavior.

### Friends / social layer (`data/social/`, `ui/screens/social/`) — opt-in, the only network feature

Off by default and gated on `UserPrefs.socialEnabled`; **nothing touches the network until the user opts in.** `SocialRepository` owns the whole feature; `FirebaseModule` provides `FirebaseAuth`/`FirebaseFirestore` lazily, so a fully-local install never constructs them (and `FirebaseAuth.getInstance()` would throw pre-`google-services.json` — don't inject `SocialRepository` into always-on paths).

- **Identity = an anonymous Firebase Auth uid** (no email/password), stored in `UserPrefs` (`socialUserId/Handle/Color`). Losing the install loses the identity (no recovery yet). The QR payload is a `FriendCode`, encoded `ggd1:<json>` so the scanner rejects foreign QRs.
- **The friend graph lives on-device** (`Friend` Room table / `FriendDao`). The backend stores **only each user's own** `SocialStats` doc at Firestore `users/{uid}` — there is no server-side friend list. Friends' stats are cached on the `Friend` row so the leaderboard renders offline.
- **Privacy boundary:** `SocialRepository.buildMyStats` is the *only* thing that uploads, and it derives **only show-up metrics** (streaks, session counts, week target) — never set logs, weights, or body metrics. Preserve that invariant. Streak/week semantics mirror `HomeViewModel` exactly (rest-aware streak, trailing-7-day training count).
- **Sync model:** each user only ever writes their own doc → last-write-wins, no conflict logic. Synced on opening the Friends screen + manual refresh (no background worker yet). Streaks reuse `MetricsRepository.currentStreakDays`/`longestStreakDays` with `maxConsecutiveRestDays`.
- **Firestore security rules** live in the Firebase console, not the repo: `get` if authed, `list` denied (can't enumerate users), `write` only your own doc. QR generation uses `zxing core` (`ui/components/QrCode`); scanning uses `zxing-android-embedded` (`ScanContract`). Added `INTERNET` + `CAMERA` permissions.

## Gotchas when changing data

- **Schema change** = add the entity field + a `Migration` in `AppDatabase` + bump `version` + register the migration in `AppModule.provideDatabase`. `exportSchema = true` writes JSON to `app/schemas/`; commit it. Migration objects are named `MIGRATION_x_y` (note they're not declared in numeric order).
- **Seed data** lives in `app/src/main/res/raw/seed_data.json`, loaded by `SeedLoader` from Room's `onCreate`/`onOpen` callbacks. To push catalog additions to existing installs, add a split id to `SeedLoader.SEED_MARKERS` — a missing marker triggers a one-time reseed, then settles. `SeedLoader` only ever **INSERTs new rows or UPDATEs in place, never REPLACEs** an existing `Split`/`WorkoutDay`/`Exercise`: a REPLACE-upsert would CASCADE-wipe `day_exercise` rows or break `set_log`/`session` foreign keys. Preserve that invariant.
