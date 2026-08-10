# Get Gym Done — Web → Android porting backlog

**Status: analysis only — none of this is implemented.** This document is the plan for
bringing the Android app up to the standard of the web port. It is written to be picked up
by someone (or several parallel agents) with a working build; section 2 assigns exclusive
file ownership per work package so packages can be run concurrently without collisions.

Source of truth for this analysis:
- Android: `get-gym-done` (Kotlin/Compose/Room/Hilt, 82 source files)
- Web: `get-gym-done-web` (React/TS/Zustand, 37 source files, 5 392 LoC)
- QA list: `get-gym-done-web/docs/qa/UI-TEARDOWN-REPORT.md` (42 tasks, committed *after* the port — `git log` shows no fix commits, so every item is still open on web too)

### A note on verification

This analysis was produced by reading both codebases; **no claim here was confirmed by
compiling or running the Android app**, because the Android SDK and Google's Maven repo
(`dl.google.com`) are unreachable from the environment this was written in. The three
headline findings were re-checked by hand against the source and are solid:

- **C1** — `sessionsThisWeek` (`HomeScreen.kt:266`) and `weekDoneDayNumbers`
  (`HomeScreen.kt:204`) are computed by two different rules and are rendered next to each
  other (`:382` vs `:475`). Confirmed.
- **C2** — `SessionDao.getLastCompletedWorkout()` has no split scoping. Confirmed.
- **X1** — `MuscleMap.kt` fills cover only `shoulders/front delts/side delts`, `chest/upper
  chest`, `biceps`, `forearms`, `abs/core`, `quads`, `adductors`, `calves` — 10 of the 25
  distinct `primaryMuscle` values in `seed_data.json`. Confirmed, and if anything
  understated below: `Obliques`, `Neck`, `Abductors`, `Lower Chest` and `Full Body` are
  unmapped too.

Line numbers elsewhere in this document should be treated as accurate-at-time-of-writing
rather than guaranteed.

---

## Summary

The two apps are genuinely 1:1 on features — same seed catalog (the two `seed_data.json` files are **byte-identical**), same rotation/streak/progression algorithms, same 10 accent palettes, same `BackupFile v2` shape. The delta is not "web has features Android lacks"; it is **(a) a handful of places where the web port quietly computed things more correctly than the Kotlin original, (b) two structural regressions on Android (custom routines cannot contain rest days; added/replaced exercises are lost when a workout is resumed), and (c) roughly two-thirds of the 42 QA items being latent on Android as well** because the port carried the Android UI over faithfully, bugs included.

Android is *ahead* of web in several places worth protecting: exercise media (photo/GIF upload), an exact-alarm background rest notification, per-set weight/rep memory pulled from the last completed session, heatmap month labels, unit suffixes on all stat tiles, a theme-aware splash, a working custom-exercise form with required-field validation, and confetti that is consumed on mount. Those are listed under "Already correct on Android" so no one wastes effort re-porting them.

The single highest-value cluster is **behavioural divergences** (section 5): the Today-tab week counter, split-switch rotation, and metric windows are computed differently in the two apps, and in each case the web version is the correct one.

---

## 1. Backlog

| ID | Area | What the web app does | What Android does today | Android files to change | Impact | Effort | Risk / notes |
|---|---|---|---|---|---|---|---|
| **C1** | Home / stats | `sessionsThisWeek` = `weekDoneDayNumbers.size` — a **Set of distinct day numbers of the active split** completed within the trailing 7 *calendar* days (`Home.tsx:71-85`). The same Set drives the ✓ ticks in the week list, so tile and list can never disagree. | Two different definitions in one screen: the ✓ ticks use a calendar-day Set (`HomeScreen.kt:190`, `:204`) but the stat tile counts **raw sessions** in a `7×24h` millisecond window including standalone activity logs and repeats of the same day (`HomeScreen.kt:213`, `:266`). Renders "8/6" against 3 ticks. | `ui/screens/home/HomeScreen.kt` (`HomeViewModel.refresh`, lines 190-208, 213, 266; `StatPill` call at 382) | High | S | Pure VM change; `sessionsThisWeek` is also mirrored in `data/social/SocialRepository.buildMyStats` — keep the two in sync (CLAUDE.md invariant). |
| **C2** | Rotation | `lastCompletedDayNumber` scans for the latest completed session **whose `workoutDayId` belongs to the active split** (`selectors.ts:42-53`), so returning to a split you used before resumes its rotation. | `sessions.getLastCompletedWorkout()` returns the globally latest workout session from **any** split (`SessionDao.kt:35`); if it isn't in the active split, `lastDayNumber` is `null` and the rotation restarts at Day 1 (`HomeScreen.kt:167-171`). | `data/db/dao/SessionDao.kt`, `ui/screens/home/HomeScreen.kt:165-171` | Med | S | Add a `getLastCompletedForDays(dayIds)` query or filter in the VM. Only observable for users who switch splits back and forth. |
| **C3** | Profile | Progression + PR series are built from **completed sessions only** (`Profile.tsx:64-84`). | Built from `metrics.allSets()` (`ProfileScreen.kt:208`, `:218-235`, `:275-292`) — sets from an *in-progress* workout already appear on the charts and can set a fake PR before the workout is finished. | `data/repository/MetricsRepository.kt` (add a completed-only accessor), `ui/screens/profile/ProfileScreen.kt` | Med | S | `SetLogDao.getCompletedProgression` already exists as a per-exercise precedent; add a bulk equivalent. |
| **C4** | Workout complete | PR count compares against sessions **completed strictly earlier** than this one (`WorkoutComplete.tsx:30-38`). | Compares against `sessions.getProgression(exId)` = every set of that exercise from any session, filtered only by `sessionId != this` (`WorkoutCompleteScreen.kt:96-102`, `SetLogDao.kt:22`) — an in-progress session's sets count as "prior". | `ui/screens/workout/WorkoutCompleteScreen.kt:94-102` | Med | S | Reuse `getCompletedProgression` and add a `completedAt <` filter. |
| **C5** | Active workout | On resume, added exercises are reconstructed from the session's logged exercise ids that aren't in the day's prescription (`store.ts:378-382`), so an on-the-fly addition survives a reload. | The VM rebuilds the exercise list **only** from `splits.getDayExercises(workoutDayId)` (`ActiveWorkoutScreen.kt:195`, `:210-246`). Any exercise added or swapped in during the workout disappears on resume, and its already-written `set_log` rows become invisible-but-counted (volume, PRs, heatmap). | `ui/screens/workout/ActiveWorkoutScreen.kt` (`ActiveWorkoutViewModel.init`) | High | M | Needs `sessions.getSetsForSession` grouped by exercise, minus base ids, appended as `isTemporary = true`. A replaced exercise also can't be distinguished from an added one without extra state. |
| **C6** | Rest timer | `restEndAt` / `restDuration` live in the persisted store (`store.ts:519-551`, `types.ts:93-94`), so a full page reload mid-rest resumes the countdown (QA verified 1:58 continuing). | `restEndAt`/`restTotal` are plain `remember` state (`ActiveWorkoutScreen.kt:518-520`) and `DisposableEffect { onDispose { RestTimerScheduler.cancel(context) } }` (`:572-574`) kills the alarm the moment you leave the screen. Backgrounding survives (the alarm fires), but process death or navigating away loses the timer entirely. | `ui/screens/workout/ActiveWorkoutScreen.kt`, plus a persisted holder (new `@Singleton` signal or a `restEndAt` column on `UserPrefs`) | High | M | If persisted in Room, needs a `Migration` + schema bump (CLAUDE.md gotcha). Keep the on-screen countdown and `RestTimerScheduler` in sync (CLAUDE.md invariant). |
| **W1** | Active workout | *(QA #6, unfixed on web too)* — steppers allow 0×0. | Same: `adjustReps`/`setReps` clamp to `0..99` (`ActiveWorkoutScreen.kt:306-307`) and `completeActiveSet` has no guard (`:311-329`). A `0 kg × 0 reps` set pollutes volume, PR detection and progression advice. | `ui/screens/workout/ActiveWorkoutScreen.kt:306-329`, `BottomBar` at `:1411` | Med | S | Require `reps ≥ 1`; weight 0 stays legal (see W6). Disable the CTA rather than silently no-op. |
| **W2** | Active workout | *(QA #7, unfixed)* — "Finish workout" appears as soon as the last exercise is done. | Identical: `BottomBar` shows Finish on `state.isLastExercise && activeSetIndex < 0` (`ActiveWorkoutScreen.kt:1411-1415`), regardless of untouched earlier exercises. Completion screen just says "1 exercises · 3 sets". | `ui/screens/workout/ActiveWorkoutScreen.kt:1380-1417` | Med | S | Add a confirm dialog naming the unfinished exercises. |
| **W3** | Rest timer / Settings | *(QA #5, unfixed)* — ±30s writes `prefs.restSeconds`. | Identical and equally undiscoverable: `adjustRestDuration` persists the new value (`ActiveWorkoutScreen.kt:266-272`, called from `:777-784`), and `SettingsScreen.kt`'s "Workout" group contains only Units (`:125-127`) — no rest-duration control anywhere. | `ui/screens/workout/ActiveWorkoutScreen.kt:266-272, 776-786`, `ui/screens/settings/SettingsScreen.kt:125-127` | Med | S | Two files, two packages — coordinate (see collisions). |
| **W4** | Active workout | *(QA #22, unfixed)* — replaced exercise labelled "· ADDED". | Identical: `buildActiveExercise` always sets `isTemporary = true` (`ActiveWorkoutScreen.kt:451`) and the hero prints `"EXERCISE n · ADDED"` (`:1059`). | `ui/screens/workout/ActiveWorkoutScreen.kt:427-454, 1056-1064` | Low | S | Add a `Replaced` origin to `ActiveExercise` or drop the tag. |
| **W5** | Active workout | *(QA #12, unfixed)* — `Notification.requestPermission()` on first mount, no context. | Identical: `notifPermission.launch(POST_NOTIFICATIONS)` in a bare `LaunchedEffect(Unit)` on workout entry (`ActiveWorkoutScreen.kt:553-557`). Denied-forever is a real risk since the rest alert is the whole point. | `ui/screens/workout/ActiveWorkoutScreen.kt:549-557` | Med | S | Defer to the first `startRest(...)` with a one-line rationale, or move to Settings alongside W3. |
| **W6** | Active workout | *(QA #33, unfixed)* — Plank/Pull Up prefill 20 kg. | Identical: `DEFAULT_START_WEIGHT_KG = 20.0` applies to every exercise regardless of `Exercise.equipment` (`ActiveWorkoutScreen.kt:112`, `:239`, `:447`). | `ui/screens/workout/ActiveWorkoutScreen.kt`, `domain/Units.kt` (a `defaultStartWeightFor(equipment)` helper) | Low | M | Interacts with W1 — a bodyweight set must be allowed at 0 and rendered as "BW". |
| **D1** | Settings / data | Import shows a **confirm dialog** ("replaces ALL current data … cannot be undone", `Settings.tsx:101-110`), parses first, and toasts the result. | No confirm at all: `GhostCta("Import from JSON") → openDoc.launch(...) → vm.importBackup(uri)` fires straight into `ExportRepository.importFrom` (`SettingsScreen.kt:78-80, 149-152`; `ExportRepository.kt:94-108`), which calls `db.clearAllTables()`. No version check, no success/failure feedback; a malformed file throws an unhandled `SerializationException` inside `viewModelScope` with zero UI response. | `ui/screens/settings/SettingsScreen.kt:63-80, 143-155`, `data/repository/ExportRepository.kt:94-108` | High | S | kotlinx.serialization's strict decode does mean Android won't wipe on `{"not":"a backup"}` (decode precedes `clearAllTables`) — but the missing confirm + silent failure are worse than web's. Add `version == 2` check and a result callback. |
| **D2** | Settings / routine | *(QA #13, unfixed)* — Settings → Reset routine skips the confirm dialog that Home's ↺ shows. | Identical: `GhostCta("Reset routine", onClick = onResetRoutine)` (`SettingsScreen.kt:138`) jumps straight to PickSplit, while Home's `IconSquare(RestartAlt)` (`HomeScreen.kt:360`) opens `ResetRoutineDialog`. | `ui/screens/settings/SettingsScreen.kt:131-139` | Low | S | Extract `ResetRoutineDialog` from `HomeScreen.kt` to a shared component, or just duplicate the dialog. |
| **O1** | Onboarding | The custom-routine builder has a **rest-day checkbox** per day (`CustomizeRoutine.tsx:125-132`) and `commitCustomSplit` persists `isRestDay` (`store.ts:216`). | **No rest-day support at all.** `DayDraft` has only `name` + `exercises` (`CustomizeRoutineScreen.kt:79-82`), `CustomDayDraft` likewise (`SplitRepository.kt:145-149`), and `createCustom` builds `WorkoutDay(...)` without `isRestDay` (`SplitRepository.kt:116-124`) so it defaults to `false`. Consequence: a custom split can never contain a rest day → `maxConsecutiveRestDays()` returns 0 → **any missed day breaks the streak** and the heatmap never bridges. | `ui/screens/onboarding/CustomizeRoutineScreen.kt` (`DayDraft`, `DayEditor`, `save`), `data/repository/SplitRepository.kt:111-142, 145-149` | High | M | Web's implementation is itself buggy (QA #2: checking the box wipes the day's exercises). Port the *feature*, not the bug — keep the list in the draft and only hide it. |
| **O2** | Onboarding | *(QA #15, unfixed)* — "Curate from your PDF" references a nonexistent PDF. | Identical string, verbatim: `RoutineMethodScreen.kt:63`. | `ui/screens/onboarding/RoutineMethodScreen.kt:62-66` | Low | S | Trivial copy fix. |
| **O3** | Onboarding | *(QA #16)* — step-progress track on step 1 only, but web at least shows a "STEP n OF 3" eyebrow on all three (`PickSplit.tsx:48`, `RoutineMethod.tsx:13`, `CustomizeRoutine.tsx:87`). | Worse: `PickSplitScreen.kt:267` renders `ProgressBar(0.33f)` and `:337` "STEP 1 OF 3"; `RoutineMethodScreen` and `CustomizeRoutineScreen` have **neither** eyebrow nor track. | `ui/screens/onboarding/RoutineMethodScreen.kt`, `ui/screens/onboarding/CustomizeRoutineScreen.kt` (extract `ProgressBar` from `PickSplitScreen.kt:393`) | Low | S | Move `ProgressBar` into `ui/components/`. |
| **H1** | Home | *(QA #8, unfixed)* — no "workout in progress" affordance. | Identical: `HomeState` has no in-progress field; `UpNextCard` always reads "START WORKOUT →" (`HomeScreen.kt:681`). The session *does* resume (`sessions.getInProgressForDay`), the user just can't tell. `SessionDao.getInProgress()` already exists and is unused by Home. | `ui/screens/home/HomeScreen.kt` (`HomeState`, `HomeViewModel.refresh`, `UpNextCard`) | Med | S | Show "RESUME · n min" plus elapsed time from `session.startedAt`. |
| **H2** | Home / calendar | *(QA #26 + #36, unfixed)* — one tag per day, past days not tappable, no session-detail screen anywhere. | Identical: `completedByEpochDay` is a `LinkedHashMap<Long, Int>` where a later session overwrites (`HomeScreen.kt:203`), a workout tag always beats an activity tag (`:201-207`, `:1074-1081`), only today's empty cell is clickable (`:1024`), and the legend has just Completed/Today (`:1036-1039`). Logged history is effectively write-only. | `ui/screens/home/HomeScreen.kt:192-208, 958-1084`, `ui/navigation/Routes.kt` + `AppNavigation.kt` (new `Route.SessionDetail`), new `ui/screens/session/SessionDetailScreen.kt` | Med | L | Biggest net-new UI. `MetricsRepository.setsForSession` already supplies the data. |
| **H3** | Home | *(QA #14, unfixed)* — ⇄ on the up-next card actually opens Day Overview. | Identical: `Icons.Rounded.SwapHoriz` with `contentDescription = "Day overview"` (`HomeScreen.kt:691`). Android at least uses a *different* icon for real switch-day (`MoreHoriz`, `DayOverviewScreen.kt:201`), so the collision is milder — but ⇄ still means "preview", which is wrong. | `ui/screens/home/HomeScreen.kt:683-692` | Low | S | Swap to a list/eye icon. |
| **H4** | Home + Day overview | *(QA #38, unfixed)* — `exs.length * 11` duplicated in two screens, ignores set counts and rest duration. | Identical duplication: `HomeScreen.kt:666` and `DayOverviewScreen.kt:228`. | new `domain/Duration.kt`, `ui/screens/home/HomeScreen.kt:665-669`, `ui/screens/day/DayOverviewScreen.kt:226-229` | Low | S | `sets × (restSeconds + ~40s)`; pure function, unit-testable. |
| **P1** | Profile | *(QA #9, unfixed)* — body-fat 300 % logs fine. | Identical: `LogBodyMetricsSheet` accepts any `toDoubleOrNull()` and `MetricsRepository.logBodyMetric` has no range check (`MetricsRepository.kt:64-80`; sheet in `ProfileScreen.kt`). | `ui/screens/profile/ProfileScreen.kt` (`LogBodyMetricsSheet`, `NumericField`), `data/repository/MetricsRepository.kt:64-80` | Low | S | Clamp fat 2-70 %, weight 20-400 kg, with inline errors. |
| **P2** | Profile | *(QA #29, unfixed)* — 20-char name cap with no feedback. | Identical: `ProfileFields.kt:84` — `onValueChange = { if (it.length <= 20) onName(it) }`, silently dropping keystrokes. | `ui/components/ProfileFields.kt:82-90` | Low | S | Show an `n/20` counter. Shared with the Friends opt-in panel — one fix, two screens. |
| **A1** | Architecture | Streak, rotation, PR counting, volume, compact-number and weight formatting all live in pure, Android-free modules (`domain/streak.ts`, `domain/metrics.ts`, `domain/units.ts`) usable from any screen. | `currentStreakDays`/`longestStreakDays` are methods on `MetricsRepository` (`MetricsRepository.kt:103-141`) coupled to Room + `ZoneId`; `compactVolume` is private in `ProfileViewModel` (`ProfileScreen.kt:410-414`); PR counting is inline in `WorkoutCompleteViewModel` (`:96-102`). None is unit-testable, contradicting CLAUDE.md's "prefer putting logic in the pure `domain/` layer". | new `domain/Streak.kt`, new `domain/Metrics.kt`, `data/repository/MetricsRepository.kt`, `ui/screens/profile/ProfileScreen.kt`, `ui/screens/workout/WorkoutCompleteScreen.kt` | Med | M | Take `List<Session>` + `maxRestGap` + a `now` parameter, exactly like `streak.ts:17-37`. Enables the first real unit tests in the repo. |
| **A2** | Architecture | `domain/units.ts` owns `formatWeight`, `roundDisplay`, `incrementKgFor`, `unitLabel` — one formatter for the whole app. | `private fun formatWeight` is copy-pasted in `ActiveWorkoutScreen.kt:1673-1674` and `WorkoutCompleteScreen.kt:324-325`, while Home/Profile use ad-hoc `"%.1f %s".format(...)` (`HomeScreen.kt:250`; `ProfileScreen.kt:230, 247-249, 267-269, 289`). Three different rounding behaviours for the same number. | `domain/Units.kt`, then the four call-site files | Low | S | Merge with A1 into one domain package. |
| **X1** | Shared components | `MuscleMap.tsx` has regions for neck/traps, delts (incl. rear), upper + lower chest, biceps, forearms, abs/obliques, **lats/mid back/lower back**, quads, **glutes/hamstrings/posterior chain**, calves (`MuscleMap.tsx:14-99`). QA #34 only complains the glute region is a thin sliver. | Android's map has regions for shoulders, chest, biceps, forearms, abs/core, quads, adductors, calves only (`ui/components/MuscleMap.kt:79-165`). There is **no** region for Back/Lats/Mid Back, Triceps, Hamstrings, Glutes, Posterior Chain, Rear Delts or Traps — all of which are `primaryMuscle` values in the seed catalog. Deadlift, Pull Up, Close Grip Bench etc. light up nothing on the `TargetsCard` shown for every exercise in the active workout. | `ui/components/MuscleMap.kt` | Med | M | Port the web region set and alias table, then fix the glute/hamstring shape for both. Purely additive drawing code. |
| **X2** | Theme | *(QA #21, unfixed)* — accent-on-background contrast fails in light mode for lime/mono. | Identical by construction: `lightColors(a)` and `darkColors(a)` both use `a.primary` verbatim for `primary`/`secondary` (`ui/theme/Theme.kt:28-66`), and the 10 palettes are the same hexes as web (`AccentPalette.kt:17-26`). Lime `#B7EF09` as text on `GymBgLight` fails WCAG AA. | `ui/theme/AccentPalette.kt` (add an `onLightBackground` slot), `ui/theme/Theme.kt:48-66` | Med | M | Audit all 10 in light mode; `mono` (`#F7F5F0`) is essentially invisible. |
| **X3** | Copy | *(QA #18, unfixed)* — "1 sessions", "1 exercises … · logged.", "1 PRs", "NEW" under "VOL". | Identical strings: `"$sessionCount sessions"` (`HomeScreen.kt:982`), `"${state.exerciseCount} exercises · ${state.setCount} sets · logged."` (`WorkoutCompleteScreen.kt:201`), `StatPill("PRs", ...)` (`:208`), `computeVolumeDelta` returning `"NEW"` (`:138`, `:140`). | `ui/screens/home/HomeScreen.kt:982`, `ui/screens/workout/WorkoutCompleteScreen.kt:134-143, 199-210` | Low | S | Use Android plurals resources; show the actual session volume with a "first time" footnote instead of "NEW". |
| **X4** | Units | *(QA #19, partly)* — lbs leaks `44.09`; web rounds to 2 dp and steps by 5 lb converted through kg. | Slightly better but same class of bug: `formatWeight` rounds to 1 dp so 20 kg reads `44.1 lbs`, and `adjustWeight` adds `5.0.displayToKg(Lbs)` giving 49.1, 54.1 … — never a round plate number. `displayStep` is correct (`domain/Units.kt:26-30`); the *prefill* is what leaks. | `domain/Units.kt`, `ui/screens/workout/ActiveWorkoutScreen.kt:286-292, 1673` | Low | M | Snap display values to 0.5 lb / 2.5 lb increments at the display boundary only — never touch the stored kg. |
| **X5** | Day overview | *(QA #10, unfixed)* — empty training day still shows an enabled START WORKOUT that lands on "NOTHING TO DO". | Identical: `BigCta("Start workout →")` is unconditional (`DayOverviewScreen.kt:288-291`) and `ActiveWorkoutScreen.kt:591-609` renders the terminal screen. `SplitRepository.addDay` creates exactly such days ("New Day", zero exercises). | `ui/screens/day/DayOverviewScreen.kt:281-292` | Med | S | When `state.exercises.isEmpty()`, swap the CTA for "Add exercises" opening the existing `EditExercisesSheet`. |
| **X6** | Day overview | *(QA #30, unfixed)* — 4 hard-coded warmup steps; "Start warmup" actually starts the workout. | Same boilerplate (`DayOverviewScreen.kt:86-94`) and the same lying CTA (`:289`). Android is *better* on the interpolation half: `CustomizeRoutineViewModel.inferMuscleGroups` (`:165-168`) populates `muscleGroups` for custom splits, so the focus string is real, unlike web's `[]`. | `ui/screens/day/DayOverviewScreen.kt:86-94, 281-292` | Low | M | Derive focus from the day's exercises' `primaryMuscle`s; rename the CTA or build a real step-through. |
| **X7** | Seed data | *(QA #34, first half)* — `decline_crunch` has `equipment: "Bodyweight"` despite being named "Machine / Decline Crunch". | Identical — the two seed files are byte-identical; the row is at `app/src/main/res/raw/seed_data.json:654`. | `app/src/main/res/raw/seed_data.json`, `data/seed/SeedLoader.kt` (`SEED_MARKERS`) | Low | S | `SeedLoader` UPDATEs in place, so pushing a corrected row to existing installs needs a new split id in `SEED_MARKERS` — do **not** REPLACE (CLAUDE.md invariant). |
| **X8** | Splash / day overview | *(QA #11, unfixed)* — placeholder art shipped in the core flow. | Identical: `HeroPlaceholder` labelled "hero — athlete" (`SplashScreen.kt:295-334`) and `StripedPlaceholder(label = "gif")` on every day-overview row (`DayOverviewScreen.kt:747`). Android's *active-workout* hero is already better (real media upload, falling back to "add photos / GIFs", `ActiveWorkoutScreen.kt:965-969`). | `ui/screens/onboarding/SplashScreen.kt`, `ui/screens/day/DayOverviewScreen.kt:735-755` | Low | M | Reuse `ExerciseMediaRepository` for the day-overview thumbnails, or fall back to a `MuscleMap` silhouette (depends on X1). |

---

## 2. Grouped work packages

Ordered by value. File ownership is exclusive within a package unless flagged.

### WP-A — Active-workout durability and set validation
**Rows:** C5, C6, W1, W2, W4, W5, W6
**Files:** `ui/screens/workout/ActiveWorkoutScreen.kt` (sole owner — 1 812 lines, VM + all composables), plus a new persisted rest-timer holder (`ui/RestTimerState.kt` as a `@Singleton`, or a `restEndAt`/`restDurationSeconds` pair on `UserPrefs`).
The single biggest correctness package. Do C5 and C6 first — they are the two real regressions.

### WP-B — Settings: rest duration, import safety, reset confirm
**Rows:** W3 (Settings half), D1, D2
**Files:** `ui/screens/settings/SettingsScreen.kt` (sole owner), `data/repository/ExportRepository.kt` (sole owner).
Adds a "Default rest" stepper to the Workout group, a confirm dialog + `version == 2` validation + result feedback around import, and the missing reset confirm.

### WP-C — Today tab: rotation, week counter, resume state, calendar detail
**Rows:** C1, C2, H1, H2, H3, plus the copy fix in X3 for `HomeScreen.kt:982`
**Files:** `ui/screens/home/HomeScreen.kt` (sole owner — 1 299 lines), `data/db/dao/SessionDao.kt`, `ui/navigation/Routes.kt`, `ui/navigation/AppNavigation.kt`, new `ui/screens/session/SessionDetailScreen.kt`.
H2 (session-detail screen) is the only L-effort item here; it can be split off if the package is too large, but it must stay with C1/C2 because they all edit `HomeViewModel.refresh`.

### WP-D — Metrics correctness + pure domain extraction
**Rows:** C3, C4, A1, A2, P1, and X3's `WorkoutCompleteScreen` strings
**Files:** new `domain/Streak.kt`, new `domain/Metrics.kt`, `domain/Units.kt`, `data/repository/MetricsRepository.kt`, `ui/screens/profile/ProfileScreen.kt` (719+ lines), `ui/screens/workout/WorkoutCompleteScreen.kt`.
Mirrors `domain/streak.ts` + `domain/metrics.ts` exactly, then rewires the two consumers. Unlocks the repo's first unit tests.

### WP-E — Onboarding parity
**Rows:** O1, O2, O3
**Files:** `ui/screens/onboarding/CustomizeRoutineScreen.kt`, `ui/screens/onboarding/RoutineMethodScreen.kt`, `ui/screens/onboarding/PickSplitScreen.kt`, `data/repository/SplitRepository.kt` (`CustomDayDraft`, `createCustom`), new `ui/components/StepProgressBar.kt`.
O1 is the structural one: rest days in custom routines. No schema change needed — `WorkoutDay.isRestDay` already exists (`entities/WorkoutDay.kt:28`) and `SeedLoader` already honours it (`SeedLoader.kt:50, 130`); only the builder and `createCustom` drop it.

### WP-F — Day overview: empty-day guard, warmup, duration estimate
**Rows:** X5, X6, H4 (the `domain/Duration.kt` helper + the DayOverview call site)
**Files:** `ui/screens/day/DayOverviewScreen.kt` (sole owner — 870 lines), new `domain/Duration.kt`.

### WP-G — Visual system: muscle map and light-theme accents
**Rows:** X1, X2, X4
**Files:** `ui/components/MuscleMap.kt` (sole owner), `ui/theme/AccentPalette.kt`, `ui/theme/Theme.kt`, `ui/theme/Color.kt`, `domain/Units.kt` (display rounding).

### WP-H — Seed catalog and shared-component polish
**Rows:** X7, P2, X8
**Files:** `app/src/main/res/raw/seed_data.json`, `data/seed/SeedLoader.kt`, `ui/components/ProfileFields.kt`, `ui/screens/onboarding/SplashScreen.kt`.

### File collisions between packages — read before parallelising

1. **WP-A ↔ WP-B — `UserPrefs.restSeconds` semantics.** Different files (`ActiveWorkoutScreen.kt` vs `SettingsScreen.kt`), but W3 is a single behaviour split across both: A must stop writing the pref on ±30s at the same time B exposes it as a setting. Land A first, or agree the contract up front.
2. **WP-A ↔ WP-D — `domain/Units.kt`.** A's W6 wants `defaultStartWeightFor(equipment)`, D's A2 rewrites the file's formatting helpers, G's X4 changes display rounding. Three packages, one small file — assign `domain/Units.kt` to **WP-D only** and have A and G file requests against it.
3. **WP-C ↔ WP-D — `data/repository/MetricsRepository.kt`.** C reads `currentStreakDays`; D moves its body into `domain/Streak.kt`. Only D edits the file; C must be written against the post-D signature or land first.
4. **WP-D ↔ WP-A — `ui/screens/workout/` package.** D owns `WorkoutCompleteScreen.kt`, A owns `ActiveWorkoutScreen.kt`. Same directory, no shared file — safe.
5. **WP-C ↔ WP-F — navigation.** Only C touches `Routes.kt`/`AppNavigation.kt`.
6. **WP-G ↔ WP-E — theme constants.** `SplashScreen.kt`/`PickSplitScreen.kt` import `AccentLime`/`AccentLimeFg` from `ui/theme/`. G adds slots without removing any, so E is safe; G must not rename the existing constants.
7. **WP-G ↔ WP-H — `SplashScreen.kt`.** H's X8 replaces the hero placeholder; G touches only theme files. Safe, but if X1's silhouette fallback is used for X8, H depends on G landing first.

---

## 3. Do-not-port list

| Web thing | Why not |
|---|---|
| **GitHub Gist cloud sync** (`src/sync/gist.ts`, Settings → Cloud Sync) | Android already has SAF `Export/Import to JSON` plus the opt-in Firebase Friends layer; storing a GitHub PAT in prefs is a strictly worse security posture than an anonymous Firebase uid. |
| **`HashRouter` + `/get-gym-done-web/` base path** (`App.tsx:88`, `vite.config.ts`) | GitHub-Pages deep-linking workaround. Android's type-safe `Route` sealed interface is the better model. |
| **PWA install / manifest / service worker** | No meaning in a packaged APK. |
| **`localStorage` persistence + `ensureSeeded` merge** (`store.ts:158-193`) | Room + `SeedLoader.SEED_MARKERS` already does versioned, non-destructive reseeding with FK safety — a superset of the web behaviour. |
| **QA #4 — `min-height: 0` on `.screen-scroll`** (`global.css:128-129`) | A flexbox artifact with no Compose analogue; Android's `Crossfade` + per-screen `verticalScroll`/`LazyColumn` already own their scroll and reset per tab. |
| **QA #37 — React Router v7 future-flag warnings** | Framework-specific. |
| **QA #23 — clear the toast queue on navigation** | Android's toast is a single `mutableStateOf<String?>` scoped to `ActiveWorkoutScreen` and auto-cleared after 1 800 ms (`ActiveWorkoutScreen.kt:576-581`); it cannot leak onto the completion screen. |
| **`resizeImage` canvas downscaler** (`Profile.tsx:404-427`) | `util/AvatarCodec.fromUri` already does this natively off the main thread. |
| **Web `Heatmap.tsx`** | Android's `buildHeatmap` (`ProfileScreen.kt:361-408`) is richer — month labels, explicit `-1` future cells, the same rest-gap bridging. |
| **`Session.activityType`/`durationMin` "port"** | Already present on Android (`entities/Session.kt:29-31`) with a fuller Log-Activity dialog. |

---

## 4. Already correct on Android — do not "fix"

These QA items are web-only regressions; the Android original is right and porting the web behaviour would be a downgrade.

- **QA #17 (splash theme toggle looks broken)** — Android's splash reads the live scheme (`SplashScreen.kt:188`, `luminance()`), so toggling visibly repaints. Web hard-codes dark.
- **QA #24 (confetti replays)** — `HomeScreen.kt:325-330` consumes the celebration flag on mount, exactly the fix the report asks for. Web consumes on animation end.
- **QA #28 (custom-exercise alphabetical defaults)** — `CustomExerciseForm.kt:67-75`: muscle starts `null` and is *required* (`valid = name.isNotBlank() && muscle != null`); equipment defaults to "Dumbbell", not "Band".
- **QA #32 (rest-day icon not centred)** — `RestDayBody` is a `Column` with `horizontalAlignment = CenterHorizontally` (`DayOverviewScreen.kt:663-701`).
- **QA #20 (missing unit labels)** — `HomeScreen.kt:250` renders `"%.1f %s"` with the unit; `ProfileScreen.kt:447` renders `"$totalVolumeLabel $unitLabel"`.
- **QA #27 (clipped exercise chips)** — `DayCard`'s preview row is `horizontalScroll` (`WorkoutsListScreen.kt:384-392`).
- **QA #30, interpolation half** — `inferMuscleGroups` (`CustomizeRoutineScreen.kt:165-168`) fills `muscleGroups` for custom splits, so the warmup focus string is a real muscle, not the day name.
- **QA #41 (unexplained "count as today's workout")** — `MarkAsWorkoutToggle` already shows the helper line "Day 2 · Pull A" (`HomeScreen.kt:874-905`, fed from `:496`).
- **QA #42 (icon-only top bars)** — mild: Android uses two *different* icons for switch-day and day-overview, unlike web's single ⇄.

Android-only capabilities worth protecting through all of the above: exercise media upload/pager/viewer (`ActiveWorkoutScreen.kt:949-1080`), the exact-alarm background rest notification (`notifications/RestTimer.kt`), per-set weight/rep memory via `SetLogDao.getLastCompletedSessionSets`, media bytes embedded in the backup (`ExportRepository.kt:65-75`), and the Friends layer.

---

## 5. Behavioural divergences — where the two apps genuinely disagree

Ranked by how wrong the losing side is.

### 5.1 "This week" — Android is wrong, and internally inconsistent
- **Web:** `Home.tsx:71-85` builds `weekDoneDayNumbers`, a `Set<number>` of **day numbers of the active split** whose sessions completed within `today - epochDay <= 6` (calendar days), excluding rest logs and day-less activities. The tile prints `set.size / trainableDayCount`; the ✓ marks in the week list read the same set.
- **Android:** the ✓ marks use an equivalent calendar-day set (`HomeScreen.kt:190, 204`) — but the tile uses a *different* quantity: `trainingCompleted.count { completedAt >= System.currentTimeMillis() - 7×24h }` (`:213, :266`). That counts raw sessions, so it (a) uses a rolling millisecond window rather than calendar days, (b) includes standalone activity logs that have no `workoutDayId`, and (c) double-counts a day number trained twice. The denominator is `days.count { !isRestDay }`, so the tile can legitimately read **"8/6"** while only three ticks are shown below it.
- **Correct: web.** It is the only definition consistent with the label and with the list underneath. This is also QA #25's "This week and Streak disagree" complaint, and on Android it additionally makes the tile disagree with Android's own tick marks.

### 5.2 Rotation after switching splits — web is right
- **Web:** `selectors.ts:42-53` finds the latest completed session **restricted to days of the active split**, so re-activating a split you trained before resumes its rotation where you left off.
- **Android:** `SessionDao.getLastCompletedWorkout()` (`SessionDao.kt:35`) takes the globally latest workout session, then `days.firstOrNull { it.id == s.workoutDayId }?.dayNumber` (`HomeScreen.kt:168`) yields `null` if that session belonged to a different split — so `nextWorkoutDay(days, null)` restarts at the lowest trainable day.
- **Correct: web.** Android's behaviour silently resets progress through a routine every time the user bounces between two saved splits. Note the calendar is deliberately cross-split on Android (`dayNumberById = splits.getAllDays()`, `:188`) — that part is fine and should be preserved; only the *rotation* input needs scoping.

### 5.3 Which sets feed the analytics — web is right
- **Web:** `Profile.tsx:64-84` iterates `sessions.filter(s => s.completedAt != null)`; `WorkoutComplete.tsx:31-37` additionally requires `other.completedAt < completedAt`.
- **Android:** `ProfileScreen.kt:208` uses `metrics.allSets()` (every row in `set_log`) for both progression and PR series, and `WorkoutCompleteScreen.kt:98-100` uses `sessions.getProgression(exId)` filtered only on `sessionId != sessionId`.
- **Correct: web.** Because Android writes every set to the DB the instant it's logged (a deliberate, good design — CLAUDE.md), an unfinished workout's sets leak into the Profile charts and can suppress a legitimate PR on the completion screen. Android already has the right query for the per-exercise case (`SetLogDao.getCompletedProgression`, `SetLogDao.kt:34`) and uses it for progression *advice* — the analytics paths just never adopted it.

### 5.4 Resumed-session reconstruction — web is right
- **Web:** `store.ts:378-382` — `added = loggedIds.filter(id => !baseIds.includes(id))`, so exercises added mid-workout come back on resume.
- **Android:** `ActiveWorkoutViewModel.init` maps only `splits.getDayExercises(workoutDayId)` (`ActiveWorkoutScreen.kt:195, 210`). An exercise added via the ⋮ menu, or a replacement, vanishes after a process kill — while its `set_log` rows survive and keep counting toward volume, PRs and the heatmap. The user sees a workout that "lost" work it actually kept.
- **Correct: web.** This is a data-visibility bug, not just UX.

### 5.5 Rest-timer lifetime — web is right
- **Web:** `restEndAt`/`restDuration` are part of the persisted `ActiveSessionState` (`types.ts:93-94`, `store.ts:519-551`); a full reload resumes the countdown.
- **Android:** `remember`ed in the composable (`ActiveWorkoutScreen.kt:518-520`), and `onDispose` cancels the alarm (`:572-574`). Backgrounding is handled (the exact alarm still fires), but leaving the screen or a process kill loses the timer silently.
- **Correct: web** on durability. Android's *alarm* mechanism is the better one and should be kept — the fix is persisting the anchor, not replacing the scheduler.

### 5.6 Rest days in custom routines — web is right (feature), Android is right (implementation)
- **Web:** the builder has an `isRestDay` checkbox and `commitCustomSplit` persists it (`CustomizeRoutine.tsx:125-132`, `store.ts:216`) — but checking it **deletes the day's exercises** and unchecking cannot restore them (QA #2), and the day-count stepper destroys trimmed days (QA #3).
- **Android:** cannot express a rest day in a custom routine at all (`CustomizeRoutineScreen.kt:79-82`, `SplitRepository.kt:116-124`). Its own day-count stepper has the same destructive `days.removeAt(days.size - 1)` behaviour (`CustomizeRoutineScreen.kt:316-318`) as QA #3.
- **Correct: neither.** Port the capability from web and the non-destructive draft handling from the QA report's recommended fix. The downstream cost of Android's omission is real: `maxConsecutiveRestDays()` returns 0 for every custom split, so `currentStreakDays(0)` breaks the streak on the first missed calendar day and the heatmap never bridges.

### 5.7 Progression-advice bucket qualification — cosmetic difference
- **Web:** `progression.ts:53-55` — a set position qualifies only if **every** recent session contributed at least one log for it.
- **Android:** `ProgressionAdvice.kt:48` — `if (logs.size < sessions) return@forEach`, i.e. total logs across sessions must reach the session count.
- Equivalent today (set numbers are unique per session per exercise), and Android additionally guards `sessions < 2` (`:29`) which web omits. Worth aligning during the WP-D domain extraction, but not a live bug. Android's `weightIncreaseSuggestion` is otherwise a faithful match, including the `1e-3` weight tolerance and `min(reps)` reporting.

---

## 6. Appendix — the 42 QA items mapped onto Android

| # | Severity | Also on Android? | Backlog row |
|---|---|---|---|
| 1 | P0 | **Partly.** kotlinx.serialization's strict decode rejects a non-backup JSON *before* `clearAllTables()`, so no wipe — but there is no confirm, no `version` check and no error surfaced at all. | D1 |
| 2 | P0 | **N/A → worse.** Android has no rest-day checkbox to break; custom rest days are impossible. | O1 |
| 3 | P0 | **Yes.** `CustomizeRoutineScreen.kt:316-318` pops days on shrink and pushes empty ones on grow. | O1 (same package) |
| 4 | P1 | No — CSS/flexbox only. | do-not-port |
| 5 | P1 | **Yes**, identically, and Android also has no rest setting. | W3 |
| 6 | P1 | **Yes.** `adjustReps` clamps to 0. | W1 |
| 7 | P1 | **Yes.** | W2 |
| 8 | P1 | **Yes.** | H1 |
| 9 | P1 | **Yes.** | P1 |
| 10 | P1 | **Yes.** | X5 |
| 11 | P1 | **Yes** on splash + day-overview rows; **no** on the active-workout hero (real media). | X8 |
| 12 | P1 | **Yes.** `LaunchedEffect(Unit) { notifPermission.launch(...) }`. | W5 |
| 13 | P2 | **Half.** Home has the confirm; Settings skips it. | D2 |
| 14 | P2 | **Yes** (⇄ = day overview), milder — different icon used for real switch-day. | H3 |
| 15 | P2 | **Yes**, verbatim string. | O2 |
| 16 | P2 | **Yes, worse** — steps 2 and 3 have no eyebrow either. | O3 |
| 17 | P2 | **No** — Android's splash is theme-aware. | — |
| 18 | P2 | **Yes**, all four sub-items. | X3 |
| 19 | P2 | **Yes**, milder (1 dp rounding → "44.1 lbs"). | X4 |
| 20 | P2 | **No** — Android labels both tiles. | — |
| 21 | P2 | **Yes** — one palette for both schemes. | X2 |
| 22 | P2 | **Yes.** | W4 |
| 23 | P2 | **No** — single screen-scoped toast. | — |
| 24 | P2 | **No** — consumed on mount. | — |
| 25 | P2 | **Yes, differently** — on Android activities *do* feed the week tile, which is why it can exceed the denominator. | C1 |
| 26 | P2 | **Yes**, all three sub-items. | H2 |
| 27 | P2 | **No** — Android's preview chips scroll horizontally. | — |
| 28 | P2 | **No** — muscle is required, equipment defaults to Dumbbell. | — |
| 29 | P2 | **Yes.** | P2 |
| 30 | P2 | **Half** — boilerplate steps and the lying CTA, yes; the focus interpolation is correct on Android. | X6 |
| 31 | P2 | **Yes**, milder — Android's overlay is a top-anchored card, not full-screen, but nothing behind it is interactive. | (fold into WP-A) |
| 32 | P2 | **No** — properly centred. | — |
| 33 | P2 | **Yes.** | W6 |
| 34 | P2 | **Yes** (identical seed file) and **worse** on the muscle map — Android has no back/lats/triceps/glutes/hamstrings/traps/rear-delt regions at all. | X7, X1 |
| 35 | P2 | **N/A** — no cloud sync on Android. | do-not-port |
| 36 | P2 | **Yes** — no way back to a past session summary. | H2 |
| 37 | P3 | No — React-specific. | do-not-port |
| 38 | P3 | **Yes**, same duplication across two files. | H4 |
| 39 | P3 | **N/A** — no rest-day flag in the Android builder yet; fold into O1's design. | O1 |
| 40 | P3 | **Yes** — `name.ifBlank { "My Routine" }` (`CustomizeRoutineScreen.kt:142`) with no inline hint. | (fold into WP-E) |
| 41 | P3 | **No** — the toggle already carries "Day 2 · Pull A". | — |
| 42 | P3 | **Yes**, milder — icons carry `contentDescription` but no visible labels. | (fold into WP-F) |

**Tally:** 27 of 42 apply to Android in some form, 8 are already fixed/never present on Android, 4 are web-platform-only, and 3 (#2, #35, #39) are not applicable because Android lacks the feature — two of which become the O1 backlog row.
