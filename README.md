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

Content owns a gesture if it scrolls at the start. Reaching an edge during that drag or its fling stops the list without moving the sheet. Start a new gesture at the edge to move the sheet. Each edge is independently configurable:

```kotlin
SheetBehavior(bubbleAtTop = true, bubbleAtBottom = false)
```

`bubbleAtTop` allows a new downward drag at the physical top to collapse or dismiss the sheet. `bubbleAtBottom` allows a new upward drag at the physical bottom to expand it. Both default to `true`; either or both can be disabled. Physical edges are independent of message order or reverse layout. Dragging the handle still moves the sheet directly.

`expandBeforeScroll` is deprecated and no longer changes behavior: content always gets the first movement so the sheet cannot steal a gesture that starts inside the list.

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
        bubbleAtTop = true,
        bubbleAtBottom = true,
    ),
    footer = { ChatComposer() },
) {
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        items(messages, key = { it.id }) { MessageBubble(it) }
    }
}
```

## Motion

The sheet settles with the same spring iOS uses for system motion: half a second, with no bounce past the snap point. Dragging between snap points tracks your finger. Dragging past the top, or past the bottom when the sheet is not allowed to close, resists and springs back. A sheet that can close still follows your finger off the screen.

The dimming fades with that drag until the lowest snap point, then stays fully dim. To keep it at full strength the whole time the sheet is visible:

```kotlin
SheetBehavior(scrimFollowsDrag = false)
```

`scrimColor = Color.Unspecified` still removes the dimming.

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

### Scroll handoff tuning

Header drags resize the sheet with the body attached below the header: a normal
`LazyColumn` preserves its first visible item and reveals more content below.
Use `SheetBodyMode.Fill` for caller-owned lists. A `reverseLayout = true` list anchors
its items to the bottom instead; use chronological items in a normal list for
this top-attached expansion behavior.

```kotlin
SheetBehavior(
    bubbleAtTop = true,             // New downward gesture at the top collapses
    bubbleAtBottom = false,         // Upward gestures at the bottom do not expand
    transferScrollFling = true,     // Preserve release speed after dragging the sheet
    scrollFlingThreshold = 500f,    // Minimum release speed in dp/s
    scrollFlingVelocityScale = .5f, // Scale sheet gesture release velocity (0..1)
)
```

List fling momentum never initiates a sheet drag, regardless of these settings.
`transferScrollFling` only controls release momentum for a gesture that already
moved the sheet. A partially dragged sheet always settles to a detent. Header
flicks are independent of these settings. Nested scrolling stops at the sheet
bounds without engaging the header's rubber-band effect.

### Custom stack effects

Use a modal stack when a flow opens one sheet above another. Atlas manages ordering,
independent sheet state, focus isolation and animated dismissal. Your modifier defines
how the covered surface looks; there is no mandatory scaling or padding preset.

```kotlin
val stack = rememberAtlasSheetStackState()
val scope = rememberCoroutineScope()
val recede = Modifier.atlasStackEffect { entry ->
    val p = entry.coveredProgress
    scaleX = 1f - 0.06f * p
    scaleY = 1f - 0.04f * p
    translationY = (-12).dp * p
    cornerRadius = (28 + 8 * p).dp
}

Button(onClick = { stack.push("friends") }) { Text("Friends") }
AtlasSheetStack(state = stack) {
    sheet("friends", modifier = recede) {
        Button(onClick = { stack.push("profile") }) { Text("Open profile") }
    }
    sheet("profile", modifier = recede) {
        Button(onClick = { scope.launch { stack.pop() } }) { Text("Close") }
    }
}
```

- `coveredProgress` follows the **immediately covering sheet's real visible height**:
  zero while hidden, one at its smallest detent, clamped above that detent. Opening,
  dragging down, reversing a drag, and closing all use that same value.
- `depth` counts entries above this one; `isTopmost` identifies the active entry.
- `LocalAtlasSheetStackEntry.current` exposes these values and `sheetState` inside
  content/header/footer or a custom composed modifier. Use it with normal Compose
  layout modifiers for dynamic side padding. Scaling preserves layout and shrinks
  content; padding changes available width and can reflow text.
- The effect supports X/Y scale, X/Y translation in `Dp`, alpha, transform origin
  (bottom-center by default), and optional rounded top-corner clipping. Corner
  clipping is additional to the sheet's base shape; it cannot undo that base clip.
- The stack's `sheet(modifier = …)` applies to the **surface**, not its full-screen
  scrim. Existing `AtlasSheet`, `AtlasModalSheet` and `AtlasSheetHost` also expose
  `surfaceModifier`; their existing `modifier` continues to affect the whole host.
- `sheet` accepts detents, behavior, body mode, title, sticky header and footer.
  When using custom detent IDs, pass `stack.push("profile", initialDetent = "medium")`.
  The default initial detent is `expanded`; missing IDs use the smallest detent.
- Keep `AtlasSheetStack` mounted while empty. `pop()` suspends for dismissal;
  Back, scrim taps and drag dismissal remove only the dismissed entry. Duplicate
  route keys create independent entries. Register every route you can push.
- Remembered stack navigation and last requested detents survive Activity recreation;
  transient drag/animation positions restart at presentation. This API coordinates
  modal sheets in one stack, not separate Fragment hosts or external navigation.

The **Stacked sheets** sample demonstrates three levels with an app-defined effect.
