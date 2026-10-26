# Repository Guidelines

## Architecture Requirements

Read [architecture_requirements.md](architecture_requirements.md) before implementing features or changing application architecture. Follow its MVI guidance for screen contracts (State, Action, Event), ViewModels, unidirectional data flow, and helper/repository responsibilities.

## Project Structure & Module Organization

This is a single-module Android rehabilitation assistant written in Kotlin with Jetpack Compose. The `app/` module contains application code and Android configuration.

- `app/src/main/java/com/dre/lab1/`: `MainActivity.kt` hosts the Compose screen and lifecycle callbacks; `SensorViewModel.kt` manages accelerometer readings through `StateFlow`.
- `app/src/main/res/`: strings, themes, icons, and an XML layout. The active screen is rendered through Compose.
- `app/src/test/`: local JVM tests.
- `app/src/androidTest/`: tests requiring an Android device or emulator.
- `gradle/libs.versions.toml`: dependency and plugin versions; `app/build.gradle.kts`: module build settings.

## Build, Test, and Development Commands

Use the checked-in Gradle wrapper from the repository root. On Windows:

- `.\gradlew.bat :app:assembleDebug`: build the debug APK under `app/build/outputs/apk/debug/`.
- `.\gradlew.bat :app:installDebug`: install on a connected device or running emulator; launch the app from its launcher icon or Android Studio.
- `.\gradlew.bat :app:testDebugUnitTest`: run local unit tests.
- `.\gradlew.bat :app:connectedDebugAndroidTest`: run instrumented tests.
- `.\gradlew.bat :app:lintDebug`: run Android Lint.

Use `./gradlew` on Unix. Configure an Android SDK with API 36; supported devices require API 31 or newer.

## Coding Style & Naming Conventions

Follow the official Kotlin style configured in `gradle.properties`: four-space indentation, `UpperCamelCase` classes and composable functions, and `lowerCamelCase` properties and ordinary functions. Use lowercase underscore names for Android resources. Keep sensor acquisition in `SensorViewModel` and UI rendering in composables. Preserve lifecycle-based listener registration and cleanup. Use Android Studio formatting; no standalone formatter or additional lint plugin is configured.

## Testing Guidelines

Local tests use JUnit 4; instrumented tests use AndroidJUnit4 with Espresso available. Name test classes `*Test` and use descriptive test methods. Existing tests are template smoke tests, and no coverage threshold is configured. Add meaningful checks for changed behavior and manually verify accelerometer updates, balance rendering, and pause/resume behavior on a suitable device.

## Commit & Pull Request Guidelines

History currently contains only `initial commit`, so no established commit convention exists. Write short, imperative subjects describing the change. Pull requests should explain behavior changes, link relevant issues, and list validation performed. Include screenshots for UI changes and device/API details for sensor testing. Keep changes focused; exclude generated build files, machine-specific IDE changes, secrets, and the ignored `local.properties` SDK path.
