# Architecture Requirements: Lightweight MVI

## Purpose and Current Status

Use this guide when implementing or reviewing this educational Android rehabilitation app. Each screen follows Model-View-Intent (MVI): actions enter a ViewModel, which updates state for Compose to render. Keep the design simple; additional architecture layers or frameworks are unnecessary.

This is the target architecture, not a description of completed migration. Currently, `MainActivity.kt` contains the Compose screen and lifecycle callbacks, while `SensorViewModel.kt` directly handles sensor APIs. Screen contracts and extracted helpers remain to be introduced. Room, cloud storage, and additional screens are future extensions; do not add them unless the task requires them.

## Screen Contract and Data Flow

```text
Screen -> Action -> ViewModel -> State -> Screen
Helper / Repository -> Flow or suspend result -> ViewModel
ViewModel -> Event -> Screen side effect
```

Define each screen's contract in `XxxContract.kt`:

- **State:** everything the UI currently displays. Use an immutable data class, `val` properties, and default values.
- **Action:** user interactions and lifecycle commands, represented by a sealed interface.
- **Event:** transient effects such as a Toast, Snackbar, or navigation request, represented by a separate sealed interface.

```kotlin
data class MonitorState(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val isRunning: Boolean = false
)

sealed interface MonitorAction {
    data object Start : MonitorAction
    data object Stop : MonitorAction
    data object ResetClick : MonitorAction
}

sealed interface MonitorEvent {
    data class ShowMessage(val text: String) : MonitorEvent
}
```

Only the ViewModel changes state. Expose read-only state and accept commands through one public `onAction(action: XxxAction)` function with an exhaustive `when`; keep implementation methods private.

```kotlin
private val _state = MutableStateFlow(MonitorState())
val state: StateFlow<MonitorState> = _state.asStateFlow()

// Inside a ViewModel action handler:
// _state.update { it.copy(x = 0f, y = 0f, z = 0f) }
```

Keep durable display values and statuses in State. Deliver transient events through a private `Channel<XxxEvent>` exposed with `receiveAsFlow()`. A Channel does not guarantee exactly-once UI effects across lifecycle changes or process death; do not use it for durable delivery requirements.

## Responsibilities and File Organization

Use one folder per screen under `app/src/main/java/com/dre/lab1/`:

```text
com/dre/lab1/
|-- MainActivity.kt
|-- monitor/
|   |-- MonitorContract.kt
|   |-- MonitorViewModel.kt
|   `-- MonitorScreen.kt
|-- helpers/
|   `-- SensorReader.kt
`-- repository/                 # Only when persistence/networking is needed
    |-- SensorRepository.kt
    |-- SensorReading.kt
    |-- CloudApi.kt             # Network API and payload model
    `-- AppDatabase.kt          # Room database, DAO, and record model
```

Apply the same `XxxContract`, `XxxViewModel`, and `XxxScreen` naming to new screens. Keep new features localized rather than rewriting unrelated screens.

- **Screen:** collect State using `collectAsStateWithLifecycle()`, handle events in `LaunchedEffect`, and forward actions. Do not access helpers, repositories, or platform services directly from composables.
- **Content:** accept State and an action callback; render UI without owning business logic. This supports previews and isolated UI checks.
- **ViewModel:** handle actions, coordinate helpers/repositories, and convert results into State or Event. For example, sensor thresholds that trigger vibration belong here, with platform work delegated to a helper.
- **Helper:** wrap sensors, Bluetooth, camera, vibration, or ML APIs behind `Flow` or `suspend` functions.
- **Repository:** encapsulate database and remote data access. ViewModels must not call Room DAOs, Retrofit, or Firebase directly.

```kotlin
@Composable
fun MonitorContent(state: MonitorState, onAction: (MonitorAction) -> Unit) {
    Column {
        Text("X: ${state.x}")
        Button(onClick = { onAction(MonitorAction.ResetClick) }) {
            Text("Reset")
        }
    }
}
```

Snippets illustrate boundaries and omit imports. Check required dependencies before using APIs absent from the current build.

## Lifecycle, Resources, and Concurrency

For the current single-screen app, Activity lifecycle callbacks dispatch Start after `super.onResume()` and Stop before `super.onPause()`. Keep sensor logic out of the Activity. An Activity-scoped ViewModel survives configuration changes; its in-memory state does not automatically survive process death.

Make Start idempotent: retain one collection Job and avoid duplicate subscriptions. Stop cancels and clears the Job and updates running status. Use `viewModelScope` for ViewModel coroutines.

Wrap sensor callbacks in `callbackFlow`; copy sensor values before emitting because Android can reuse the event array. Unregister listeners in `awaitClose` so cancellation releases resources. Sensors must stop when the app is paused.

Do not block the main thread. Launching a coroutine alone does not move work off it: use main-safe suspend APIs or place blocking database/network operations on `Dispatchers.IO` inside the data-access implementation.

## Conditional Persistence and Cloud Extensions

When storage or upload is required, provide one application-scoped repository through an Application-owned instance or equivalent shared holder. Keep storage changes behind its interface:

- Start with `suspend fun save(reading: SensorReading): Result<Unit>` for upload; let the ViewModel translate outcomes into status and events.
- When adding Room, save locally first, upload second, and mark records synchronized only after successful upload. Retain failed uploads with `synced = false`.
- Expose history as a Flow and provide `syncPending()` for retrying stored records. Pending flags alone do not schedule retries; the feature must supply an explicit synchronization trigger.
- Separate `SensorReading` (ViewModel-facing data), `SensorPayload` (network JSON), and `SensorRecord` (Room entity). Keep `toPayload()` and `toRecord()` mappings near their models.

## Agent Implementation and Review Checklist

1. Read the existing implementation and identify which target boundaries the task affects.
2. Add defaulted State fields, Action variants, and transient Event variants as needed.
3. Add helper or repository operations for platform access or data storage.
4. Handle actions in `onAction`; update State through `_state.update { it.copy(...) }`.
5. Render State and dispatch actions from the screen; create a separate screen folder when the feature needs its own screen.
6. Verify immutable/read-only state, resource cleanup, lifecycle behavior, and background execution. Follow `AGENTS.md` for build and test commands.
