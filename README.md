# Atlas Sheet

Bottom sheets for Jetpack Compose, with named snap points, a sticky header and footer, and scrolling that stays under your control.

Bring your own theme and content. Atlas is built on Compose Foundation, not Material. A modal sheet isolates focus in its own window. An inline sheet can sit over a screen the user can still touch.

Requires Android 24+ and Jetpack Compose.

## Install

Add JitPack, then depend on the release tag:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories { google(); mavenCentral(); maven("https://jitpack.io") }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.andrew-levy:atlas-sheet:0.2.0")
}
```

To try a local build instead, run `./gradlew :atlas-sheet:publishToMavenLocal` and depend on `io.github.andrew-levy:atlas-sheet:0.2.0` with `mavenLocal()`.

## Show a sheet

```kotlin
val state = rememberSheetState(initialDetent = "medium")
val scope = rememberCoroutineScope()

AtlasModalSheet(
    state = state,
    detents = listOf(
        SheetDetent.fixed("peek", 260.dp),
        SheetDetent.fraction("medium", 0.6f),
        SheetDetent.expanded(),
    ),
    onDismissRequest = { open = false },
    header = { Text("Order") },
    footer = {
        Button(onClick = { scope.launch { state.hide() } }) { Text("Done") }
    },
) {
    OrderDetails()
}
```

Remove `AtlasModalSheet` from composition when `onDismissRequest` runs. The sheet animates closed before that callback.

```kotlin
scope.launch { state.animateTo("peek") }
scope.launch { state.expand() }
scope.launch { state.collapse() }
scope.launch { state.hide() }
```

`currentDetent` is the snap point the sheet has settled on, or `null` while it is hidden. `progress` is the visible height from `0` to `1`.

## Snap points

| Factory | Height |
| --- | --- |
| `fixed("peek", 260.dp)` | An exact height, including the handle, header, and footer |
| `fraction("medium", 0.6f)` | A fraction of the space above the navigation bar and keyboard |
| `content()` | As tall as the body needs, plus the header and footer |
| `expanded()` | The full height under the status bar, minus `topMargin` |
| `custom("reading") { … }` | Your own height from `availableHeight`, `contentHeight`, and `chromeHeight` |

Ids must be unique. Two snap points that land on the same height share one position; the first id you declared is the one dragging settles on.

`content()` measures the body, so use it with ordinary content, not a lazy list.

## Let the content scroll first

By default, dragging up on the body grows the sheet before the content moves. For a list or a chat, let the content scroll until it runs out, and only then move the sheet:

```kotlin
SheetBehavior(expandBeforeScroll = false)
```

Dragging the handle still moves the sheet directly. Dragging down at the top of the content collapses it, and can dismiss it when drag dismissal is enabled.

Keep a chat list top-aligned. `reverseLayout = true` pins messages to the bottom, so growing the sheet moves the top edge and leaves the bubbles in place. Put the composer in `footer`, not in the list. Atlas does not scroll to new messages for you.

Lazy content cannot use `content()`, because items that are not composed have no height. Use `SheetBodyMode.Fill` and fixed, fractional, or custom snap points:

```kotlin
AtlasModalSheet(
    state = state,
    detents = detents,
    onDismissRequest = { open = false },
    bodyMode = SheetBodyMode.Fill,
    behavior = SheetBehavior(
        imePolicy = SheetImePolicy.Expand,
        expandBeforeScroll = false,
    ),
    footer = { ChatComposer() },
) {
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        items(messages, key = { it.id }) { MessageBubble(it) }
    }
}
```

## Keyboard

Do not add `imePadding()` around the sheet. Atlas already keeps the sheet above the keyboard.

| `imePolicy` | What happens |
| --- | --- |
| `Resize` | The sheet keeps its snap point and shortens to fit above the keyboard. This is the default. |
| `Expand` | The sheet also jumps to its largest snap point, then returns to the previous one when the keyboard closes. |
| `Ignore` | The keyboard is ignored. Fine for a sheet with nothing to type. |

## Edge to edge

Call `enableEdgeToEdge()` with transparent system-bar styles. The default navigation-bar style paints its own scrim, which shows up as a darker strip:

```kotlin
enableEdgeToEdge(
    statusBarStyle = SystemBarStyle.light(
        android.graphics.Color.TRANSPARENT,
        android.graphics.Color.TRANSPARENT,
    ),
    navigationBarStyle = SystemBarStyle.light(
        android.graphics.Color.TRANSPARENT,
        android.graphics.Color.TRANSPARENT,
    ),
)
```

The dimming covers the status bar and navigation bar, so the sheet and the screen read as one surface. The sheet's own color continues behind the navigation buttons. Content stays in the safe area.

## A sheet inside a Fragment

`AtlasSheet` stays in your composition. It opens when it appears, animates shut on `hide()`, and tells you when that animation has finished.

```kotlin
AtlasSheet(
    state = rememberSheetState(initialDetent = "medium"),
    detents = listOf(SheetDetent.fraction("medium", .6f), SheetDetent.expanded()),
    onDismissed = { /* pop this screen after the sheet has closed */ },
    footer = { OrderActions() },
) {
    OrderDetails()
}
```

Close it with `state.hide()`. `onDismissed` runs after the exit. Removing the Fragment yourself skips that animation.

Mount it in a full-window container if the dimming should cover the screen behind it. The sheet handles Back while it is visible. Your navigation handles removing the Fragment. Don't also run a Fragment transition that slides the same view.

For a host that starts hidden:

```kotlin
val state = rememberSheetState(initiallyVisible = false)
// later
scope.launch { state.show() }
```

## A panel over a live screen

`AtlasSheetHost` does not open a new window, so the screen behind it still receives touches when you omit the dimming:

```kotlin
Box(Modifier.fillMaxSize()) {
    ScreenBehind()
    AtlasSheetHost(
        state = state,
        detents = detents,
        scrimColor = Color.Unspecified,
        header = { Text("Filters") },
    ) {
        FilterList()
    }
}
```

Watch `currentDetent == null` if you want to remove the host after it closes.

## Stack sheets

Compose another `AtlasModalSheet` while one is already open. Each sheet is its own window, with its own snap points and dismissal. Closing the top sheet leaves the ones beneath it.

```kotlin
var details by remember { mutableStateOf(false) }

AtlasModalSheet(state = plans, detents = planDetents, onDismissRequest = { open = false }) {
    Plans(onOpen = { details = true })
}
if (details) {
    AtlasModalSheet(
        state = stay,
        detents = listOf(SheetDetent.fraction("details", 0.6f)),
        onDismissRequest = { details = false },
    ) {
        StayDetails()
    }
}
```

There is no shared scrim or linked motion between them. Each sheet dims whatever is behind it, including another sheet.

## Keep a flow from being dismissed

```kotlin
SheetBehavior(
    dismissOnDrag = false,
    dismissOnScrim = false,
    dismissOnBack = false,
)
```

The user cannot drag it away, tap the dimming, or press Back to close it. `state.hide()` still closes it when your own button says so.

## Run the sample

Open the project in Android Studio and run `sample` on Android 24+, with JDK 17 and Android SDK 35.

```bash
./gradlew :sample:assembleDebug :atlas-sheet:testDebugUnitTest
```

## License

Apache 2.0. See [CHANGELOG](CHANGELOG.md), [architecture](docs/ARCHITECTURE.md), and [contributing](CONTRIBUTING.md).
