# Get Gym Done

Android workout logger. Local-only, no cloud, no accounts. Built from a 5-day illustrated split.

This branch contains the **Week 1 + Week 2 scaffold** from the handover: project structure, Compose theme matching the design system, Room schema, seeded data, JSON export/import, navigation graph, and a navigable onboarding flow.

## Stack

- **Kotlin** 2.0, **AGP** 8.5, **JDK** 17
- **Jetpack Compose** with Material 3 — Anton + Inter via Downloadable Google Fonts
- **Navigation Compose** 2.8 with type-safe Kotlin Serialization routes
- **Hilt** DI (KSP)
- **Room** 2.6 with kotlinx.serialization TypeConverters
- **kotlinx.serialization** for JSON seed + export
- **Vico** wired for upcoming progression charts
- `minSdk` 26 · `targetSdk` 35

## Open in Android Studio

1. Open this directory as a project.
2. Let Gradle sync — Android Studio will generate `gradle/wrapper/gradle-wrapper.jar` from the properties file.
3. Run the `app` configuration on a device or emulator (API 26+).

## What's wired up

- Splash → Pick split → Routine method → Home (with bottom-tab destinations stubbed but navigable)
- Room seeds **26 exercises** + the 5-day split on first launch
- **Settings** has live theme + units toggles and **JSON export/import** via Storage Access Framework
- **Debug data** screen shows the seeded catalog so you can verify the DB end-to-end
- Day rotation logic (`(lastDayNumber % dayCount) + 1`) drives the Up-Next card

## What's still placeholder

- Active workout (Stacked layout) — Week 4
- Workouts list, Profile charts, Day overview details — Week 5+
- Exercise illustrations use a striped Compose placeholder; PNGs swap in later via `Exercise.illustrationFilename`

## Layout

```
app/
├── build.gradle.kts
├── proguard-rules.pro
└── src/main/
    ├── AndroidManifest.xml
    ├── res/raw/seed_data.json        — 5-day split + 26 exercises
    └── java/com/getgymdone/app/
        ├── GymDoneApp.kt              — @HiltAndroidApp
        ├── MainActivity.kt
        ├── AppViewModel.kt
        ├── data/
        │   ├── db/                    — Room: entities, DAOs, AppDatabase, Converters
        │   ├── repository/            — Exercise / Split / Session / Export / UserPrefs
        │   └── seed/                  — SeedLoader + SeedModels
        ├── di/AppModule.kt
        ├── domain/                    — DayRotation, Units
        └── ui/
            ├── theme/                 — Color, Type, Shape, Theme (light + dark)
            ├── components/            — StripedPlaceholder, PillChip, BigCta
            ├── navigation/            — Routes (type-safe) + AppNavigation
            └── screens/               — onboarding / home / day / workout / profile / settings / debug
```

## Design tokens

Pulled from the Handover Document HTML:

| Token | Light | Dark |
|---|---|---|
| Background | `#F5F3EE` | `#0A0A09` |
| Surface | `#FFFFFF` | `#16140F` |
| Foreground | `#14130F` | `#F7F5F0` |
| Accent (Lime) | `#C1F038` | `#C1F038` |
| Accent 2 (Coral) | `#F76E5C` | `#F76E5C` |

Radius scale 4 / 10 / 14 / 22 / 44 maps to `Shapes.extraSmall` → `extraLarge`.
