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

Keep `AtlasModalSheet` mounted and control visibility with `state.show()` / `state.hide()`. Its dialog is removed after dismissal. `onDismissRequest` is optional; callers may still remove the composable in that callback.

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

Pass the same `stack = rememberSheetStackState()` instance to each `AtlasModalSheet`.
Each modal uses its own state and detents. Calling `show()` presents it above the others;
calling `hide()` dismisses it without a separate visibility boolean. The custom stack
example below adds cumulative recession with `Modifier.atlasStackEffect`.

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

Share one `rememberSheetStackState()` between ordinary modal sheets. There is no stack
composable, route registration, push/pop navigation, or second visibility boolean.
Keep the modals mounted and use their own `SheetState.show()` / `hide()` methods.

```kotlin
val stack = rememberSheetStackState()
val friends = rememberSheetState()
val profile = rememberSheetState(initiallyVisible = false)
val scope = rememberCoroutineScope()
val recede = Modifier.atlasStackEffect { entry ->
    val depth = entry.animatedDepth
    scaleX = (1f - 0.06f * depth).coerceAtLeast(0.7f)
    scaleY = (1f - 0.04f * depth).coerceAtLeast(0.8f)
    translationY = (-12).dp * depth
    cornerRadius = (28 + 8 * depth).dp
}

AtlasModalSheet(
    state = friends, stack = stack,
    detents = listOf(SheetDetent.expanded()),
    surfaceModifier = recede,
) {
    Button(onClick = { scope.launch { profile.show() } }) { Text("Open profile") }
}
AtlasModalSheet(
    state = profile, stack = stack,
    detents = listOf(SheetDetent.fraction("expanded", 0.6f)),
    surfaceModifier = recede,
) {
    Button(onClick = { scope.launch { profile.hide() } }) { Text("Close") }
}
```

- `animatedDepth` sums the visible progress of **every** sheet above this one. Three
  settled layers have depths 2, 1, 0; the example gives widths 88%, 94%, 100%.
  Opening a third sheet moves the back layer smoothly from 1 to 2. Dismissing or
  reversing the gesture updates every underlying layer from the actual positions.
- Each sheet contributes zero while hidden, one at its smallest detent, clamped above
  that detent. `coveredProgress` exposes only the immediately covering sheet's progress.
  `depth` is the integer count above; `isTopmost` identifies the active layer.
- Hidden mounted modals create no dialog and do not count toward depth. A shown modal
  joins at the top, remains through its completed exit, then unregisters. Showing an
  already-presented modal does not reorder it. Simultaneous first presentations use
  composition order; open sequentially when a specific ordering is needed.
- `onDismissRequest` is optional and runs after the exit completes. Existing callers
  may still remove the composable there; mounted callers can leave it in place and
  later call `show()` again. Modal content leaves composition while hidden: hoist any
  content state that must survive reopening outside the modal.
- `LocalAtlasSheetStackEntry.current` is nullable outside coordinated modal content.
  Use it in a custom composed modifier for dynamic side padding. Scaling preserves
  layout and shrinks text; padding changes width and can reflow content.
- The effect supports scale, translations in `Dp`, alpha, transform origin and optional
  top-corner clipping. Clipping adds to the base shape; it cannot undo that base clip.
  Effects apply only to `surfaceModifier`, leaving the full-screen scrim unchanged.
- Sheets without `stack` remain independent; `atlasStackEffect` is a no-op there.
  Share each stack only among modals in one host window. Separate stack instances do
  not coordinate. Inline/Fragment sheets are not enrolled in this modal coordinator.
- Each remembered `SheetState` restores its visibility/detent after Activity recreation.
  Stack ordering itself is transient and rebuilt in composition order on restoration;
  this coordinator does not own or persist app navigation.

The **Stacked sheets** sample uses three always-mounted modals with cumulative recession.
