# Usage guide

[Back to the README](../README.md) · [Sample gallery](../samples/sample/src/main/java/dev/better/sheet/sample)

Focused examples and behavior details for less common configurations. Code uses the
API on this branch; app-specific content functions are placeholders.

[Custom surfaces](SURFACES.md) · [Background interaction](#background-interaction) · [Inline sheets](#inline-sheets) · [Fragments](#fragments) · [Stacking](#stacking) · [Parameters](#parameters) · [Dismissal](#dismissal) · [Scroll handoff](#scroll-handoff) · [Edge to edge](#edge-to-edge) · [Floating tray](#floating-tray) · [Animation behavior and migration](#animation-behavior-and-migration)

## Background interaction

`scrimEnabled` enables or removes the whole scrim: its drawing and outside-touch handling.
`scrimColor` controls appearance only; a transparent enabled scrim still catches touches.

```kotlin
BetterSheet(
    state = state,
    snapPoints = snapPoints,
    scrimColor = Color.Black.copy(alpha = 0.42f), // Used only when enabled.
    behavior = SheetBehavior(scrimEnabled = false),
) {
    Content()
}
```

| Configuration | Outside touches |
| --- | --- |
| `scrimEnabled = true` (default), `dismissOnScrim = true` | Dismiss the sheet; do not reach the background |
| `scrimEnabled = true`, `dismissOnScrim = false` | Blocked without dismissing the sheet |
| `scrimEnabled = false` | No scrim or dimming; touches reach the background of an inline sheet |

`Color.Transparent` and `Color.Unspecified` both draw no dimming and never change touch
behavior. `BetterModalSheet` always blocks background interaction through its window.
With its scrim disabled, outside taps do not dismiss it. Back and `state.hide()` still
work according to their own settings. Use `BetterSheet` for touch-through.

## Inline sheets

Use `BetterSheet` in a full-size `Box` alongside your Compose screen. It fills its parent,
opens on mount by default, and animates closed when you call `hide()`. Set
`behavior = SheetBehavior(scrimEnabled = false)` to keep the background interactive outside the sheet.
An inline sheet does not provide the separate-window focus isolation of `BetterModalSheet`.

*This recording shows the shared sheet surface in the modal demo; the inline variant uses
that same resizing and scrolling implementation inside your layout.*

```kotlin
val state = rememberSheetState(initialSnapPoint = "peek")
val scope = rememberCoroutineScope()

Box(Modifier.fillMaxSize()) {
    MapScreen()
    BetterSheet(
        state = state,
        snapPoints = listOf(
            SheetSnapPoint.fixed("peek", 220.dp),
            SheetSnapPoint.expanded(),
        ),
        scrimColor = Color.Unspecified,
        behavior = SheetBehavior(scrimEnabled = false),
        header = { Text("Nearby places") },
        footer = {
            Button(onClick = { scope.launch { state.hide() } }) { Text("Close") }
        },
    ) {
        PlaceFilters()
    }
}
```

To open it later, use `rememberSheetState(initiallyVisible = false)` and call `state.show()`.
Keep the sheet mounted to reopen it. If your navigation owns removal, perform that in
`onDismissed` after closing. Removing the composable immediately skips its exit animation.

## Fragments

This example uses `BetterSheet` because it renders within the Fragment's own view. A
Fragment can also host `BetterModalSheet` when modal window behavior is desired; its
`onDismissed` callback can remove or pop the Fragment in the same way.

Use `BetterSheet` inside a `ComposeView`. Tie the composition to the Fragment's
view lifecycle, and have the close button call `hide()` before removing the Fragment:

```kotlin
class OrderSheetFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        // Define a stable ID in res/values/ids.xml for saved Compose state.
        id = R.id.order_sheet_content
        setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        setContent {
            val state = rememberSheetState(initialSnapPoint = "medium")
            val scope = rememberCoroutineScope()
            BetterSheet(
                state = state,
                snapPoints = listOf(
                    SheetSnapPoint.fraction("medium", 0.6f),
                    SheetSnapPoint.expanded(),
                ),
                onDismissed = {
                    parentFragmentManager.beginTransaction()
                        .remove(this@OrderSheetFragment)
                        .commit()
                },
                footer = {
                    Button(onClick = { scope.launch { state.hide() } }) { Text("Done") }
                },
            ) {
                OrderDetails()
            }
        }
    }
}
```

Mount the Fragment in a full-window container so the scrim can cover the background. For
back-stack navigation, replace the removal callback with your navigation's pop operation.
The sheet handles Back while visible. Avoid a second Fragment slide transition on the same view.
See the runnable [Fragment sample](../samples/sample/src/main/java/dev/better/sheet/sample/FragmentDemoActivity.kt).

`BetterSheetHost` remains a lower-level public rendering API. Prefer `BetterSheet` for inline
usage with managed entry/exit and `onDismissed`.

## Stacking

Share one `rememberSheetStackState()` between ordinary modal sheets. There is no stack
composable, route registration, push/pop navigation, or second visibility boolean.
Keep the modals mounted and use their own `SheetState.show()` / `hide()` methods.

```kotlin
val stackState = rememberSheetStackState()
val friends = rememberSheetState()
val profile = rememberSheetState(initiallyVisible = false)
val scope = rememberCoroutineScope()
val recede = Modifier.sheetStackEffect { entry ->
    val depth = entry.animatedDepth
    scaleX = (1f - 0.06f * depth).coerceAtLeast(0.7f)
    scaleY = (1f - 0.04f * depth).coerceAtLeast(0.8f)
    translationY = (-12).dp * depth
    cornerRadius = (28 + 8 * depth).dp
}

BetterModalSheet(
    state = friends, stackState = stackState,
    snapPoints = listOf(SheetSnapPoint.expanded()),
    sheetTransform = recede,
) {
    Button(onClick = { scope.launch { profile.show() } }) { Text("Open profile") }
}
BetterModalSheet(
    state = profile, stackState = stackState,
    snapPoints = listOf(SheetSnapPoint.fraction("expanded", 0.6f)),
    sheetTransform = recede,
) {
    Button(onClick = { scope.launch { profile.hide() } }) { Text("Close") }
}
```

- `animatedDepth` sums the visible progress of **every** sheet above this one. Three
  settled layers have depths 2, 1, 0; the example gives widths 88%, 94%, 100%.
  Opening a third sheet moves the back layer smoothly from 1 to 2. Dismissing or
  reversing the gesture updates every underlying layer from the actual positions.
- Each sheet contributes zero while hidden, one at its smallest snap point, clamped above
  that snap point. `coveredProgress` exposes only the immediately covering sheet's progress.
  `depth` is the integer count above; `isTopmost` identifies the active layer.
- Hidden mounted modals create no dialog and do not count toward depth. A shown modal
  joins at the top, remains through its completed exit, then unregisters. Showing an
  already-presented modal does not reorder it. Simultaneous first presentations use
  composition order; open sequentially when a specific ordering is needed.
- `onDismissed` is optional and runs after the exit completes. Existing callers
  may still remove the composable there; mounted callers can leave it in place and
  later call `show()` again. Modal content leaves composition while hidden: hoist any
  content state that must survive reopening outside the modal.
- `LocalSheetStackEntry.current` is nullable outside coordinated modal content.
  Use it in a custom composed modifier for dynamic side padding. Scaling preserves
  layout and shrinks text; padding changes width and can reflow content.
- The effect supports scale, translations in `Dp`, alpha, transform origin and optional
  top-corner clipping. Clipping adds to the base shape; it cannot undo that base clip.
  Effects apply only to `sheetTransform`, leaving the full-screen scrim unchanged.
- Sheets without `stackState` remain independent; `sheetStackEffect` is a no-op there.
  Share each stack only among modals in one host window. Separate stack instances do
  not coordinate. Inline/Fragment sheets are not enrolled in this modal coordinator.
- Each remembered `SheetState` restores its visibility/snap point after Activity recreation.
  Stack ordering itself is transient and rebuilt in composition order on restoration;
  this coordinator does not own or persist app navigation.

The **Stacked sheets** sample uses three always-mounted modals with cumulative recession.

## Parameters

These parameters are shared by `BetterModalSheet` and `BetterSheet`, except `stackState`.

| Parameter | Default | When to change it |
| --- | --- | --- |
| `state` | Required for modal; remembered for inline | Hoist it to open, close, or change the snap point |
| `snapPoints` | Required | Choose fixed, fractional, content-fit, or custom heights |
| `onDismissed` | No-op | Update navigation or notify the parent after dismissal |
| `header` / `footer` | `null` | Keep a title, actions, or chat composer outside the scrolling body |
| `bodyMode` | `SheetBodyMode.Scroll` | Use `Fill` for caller-owned scrolling such as a `LazyColumn` |
| `dragHandle` | `SheetDragHandle()` | Supply a custom composable or `null` to hide it |
| `behavior` | `SheetBehavior()` | Configure dragging, dismissal, keyboard, or scroll handoff |
| `maxWidth` / `topMargin` | `640.dp` / `24.dp` | Tune tablet width and space above the expanded sheet |
| `containerColor` / `contentColor` | White / dark blue-gray | Apply your theme; custom content can read `LocalSheetContentColor` |
| `scrimColor` | Black at 42% alpha | Tune dimming; `Color.Unspecified` or `Color.Transparent` draws no dimming |
| `shape` | Rounded top corners, `28.dp` | Customize the base surface shape |
| `modifier` | `Modifier` | Customize the full host container |
| `sheetTransform` | `Modifier` | Transform/decorate the whole sheet, including stack effects |
| `sheetMaterial` | `(@Composable (Shape) -> Unit)?` | Decorative material slot behind content; null keeps solid defaults |
| `title` | `"Bottom sheet"` | Set the accessibility pane title; this does not render a visible header |
| `presentation` | `SheetPresentation.Standard` | Use `SheetPresentation.Tray()` for an inset surface |
| `enterTransition` / `exitTransition` | Vertical spring slide | Use standard Compose transitions |
| `snapAnimationSpec` | Critically damped spring | Change snap-point and drag-release timing |
| `stackState` | `null` (modal only) | Coordinate multiple modal sheets |

Removing a modal's scrim does not make its separate window behave like an inline sheet.
Choose `BetterSheet` when you need interaction with the screen behind it.

## Dismissal

```kotlin
SheetBehavior(
    dismissOnDrag = false,
    dismissOnScrim = false,
    dismissOnBack = false,
)
```

The user cannot drag it away, tap the dimming, or press Back to close it. `state.hide()` still closes it when your own button says so.

## Scroll handoff

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
moved the sheet. A partially dragged sheet always settles to a snap point. Header
flicks are independent of these settings. Nested scrolling stops at the sheet
bounds without engaging the header's rubber-band effect.

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

## Floating tray

Use the same sheet with an iOS-inspired floating surface that joins the edges as it expands:

```kotlin
BetterSheet(
    state = state,
    snapPoints = listOf(
        SheetSnapPoint.fraction("medium", 0.6f),
        SheetSnapPoint.expanded(),
    ),
    presentation = SheetPresentation.Tray(),
) {
    // Your content
}
```

Available on `BetterSheet`, `BetterSheetHost`, and `BetterModalSheet`. Defaults are 12.dp side
and bottom gaps, 32.dp fallback corners, 28.dp expanded top corners, and morphing over the final
40% of available height. Customize with `horizontalInset`, `bottomInset`, `cornerRadius`,
`expandedCornerRadius`, `morphStartFraction`, and `matchDeviceCorners`. Android 12+ reported
window corners inform the collapsed radius; use `matchDeviceCorners = false` for an explicit
radius. Matching applies to collapsed corners; expanded top corners use `expandedCornerRadius`.
Unavailable metadata, embedded hosts, and width-capped surfaces use the configured fallback.
This matches a reported circular radius, not every device's physical corner contour. Tray owns its shape; `shape` remains the Standard presentation's setting.

Dragging, reversing, and programmatic snap changes all drive the same morph. Short sheets stay
floating; navigation/keyboard safe areas remain protected. The existing `maxWidth` still applies
on tablets. This adds the geometry, not Liquid Glass blur/refraction. Try **Floating tray** in
the sample gallery or the people sheet in the Find My sample. Reproduce the recording with
`python3 scripts/record-demos.py tray`.

## Animation behavior and migration

Configure animations on `BetterSheet`, `BetterModalSheet`, or `BetterSheetHost`:

```kotlin
val state = rememberSheetState(initialSnapPoint = "medium")
BetterModalSheet(
    state = state,
    snapPoints = snapPoints,
    enterTransition = fadeIn(animationSpec = tween(220)),
    exitTransition = fadeOut(animationSpec = tween(180)),
    snapAnimationSpec = spring(dampingRatio = 0.8f, stiffness = 350f),
) {
    SheetContent()
}
```

Use standard `androidx.compose.animation` transitions: `fadeIn`/`fadeOut`,
`slideInVertically`/`slideOutVertically`, `scaleIn`/`scaleOut`, or combinations with `+`.
Defaults slide vertically with the existing critically damped spring. `EnterTransition.None`
and `ExitTransition.None` skip the surface transition. The scrim has a short independent fade.

- `state.snapTo("expanded")` animates using `snapAnimationSpec`. Calling it while hidden enters
  at the requested snap point using `enterTransition`.
- `state.jumpTo("expanded")` immediately selects the snap point and skips the entrance.
- `show()`, `hide()`, `expand()`, and `collapse()` use the composable configuration.
- Back and scrim dismissal use `exitTransition`; `hide()` and `onDismissed` wait for completion.
- Dragging stays attached to the finger. Drag release uses `snapAnimationSpec`, including a drag
  to dismiss; it does not replay a decorative exit after reaching the bottom.
- Mounted inline/fragment content retains its form and scroll state across hide/show. A modal's
  dialog is disposed after dismissal. A new show/hide request interrupts the previous operation.
- Rubber-band recovery and content resizing retain internal spring defaults.


**Migration:** `animateTo(id)` is now `snapTo(id)`. The previous immediate `snapTo(id)` is now
`jumpTo(id)`. Move `rememberSheetState(animationSpec = …)` to the composable's
`snapAnimationSpec`; enter/exit timing is configured directly on Compose transitions.



## Coordinating content with sheet progress

`state.progress` updates during drags and animations: 0 is hidden and 1 is the full
available height. Use it directly to drive opacity, blur, color, or scale, without a
second animation lagging behind the finger:

```kotlin
val closing = (1f - state.progress / 0.25f).coerceIn(0f, 1f)
Column(Modifier.blur((16f * closing).dp)) { SheetContent() }
```

Compose content blur is supported on Android 12+; it is ignored on older devices.
Find My also fades its content, so collapse stays smooth on API 24–30. Its minimum snap
point is a permanent People/Chat bar, so it subtracts that collapsed height before
mapping the last 25% of expansion to blur. Contextual actions sit above the tabs and
shrink with the same progress; the tabs stay sharp and interactive.


Tray shadows use Compose 1.9's `dropShadow`: the blur, opacity, and offset stay constant
while the tray changes height and shape. They do not fade based on expansion progress.
