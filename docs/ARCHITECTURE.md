# Architecture and interaction contract

Atlas has four layers: snap point resolution, state, interaction coordination, and rendering.

`SheetGeometry` is Android-independent. It resolves heights into downward-growing offsets,
clamps them to the available viewport and sticky chrome, and collapses equivalent physical
anchors into deterministic aliases. The first declared snap point wins a collision. Anchor
positions are recalculated on content, chrome, window and IME changes; IDs are stable.

`SheetState` wraps Foundation's `AnchoredDraggableState`, preserving its mutation arbitration
and animation cancellation. `currentSnapPoint` means settled, `targetSnapPoint` means in flight,
and `progress` is continuous visible height. A hidden anchor is excluded from user dragging
when drag dismissal is disabled. Explicit `hide()` temporarily adds it. State saving records
a snap point ID, not stale pixels. A removed ID falls back to the smallest surviving snap point.

The handle and header use `anchoredDraggable`. The body participates in nested scrolling.
Content that scrolls at the start owns the entire gesture, including its fling. Reaching an
edge mid-gesture does not transfer ownership. A fresh gesture at the physical top can
collapse the sheet when `bubbleAtTop` is enabled; one at the bottom can expand it when
`bubbleAtBottom` is enabled. Both default to true. Vertical input
alone is consumed; horizontal gestures are left to children. Between snap points the sheet tracks
the finger. Past the top, and past the lowest snap point when drag dismissal is off, leftover
drag uses the iOS scroll-view rubber band (coefficient 0.55). Upward, the sheet grows from
the top: the bottom edge and footer stay on the screen, and the header and body move up with
the extra height. Downward, when
drag dismissal is off, the whole sheet translates. The anchors stay put, so the body does not
reflow. Releasing springs that offset back with
the state's animation spec. An inward flick past the velocity threshold drops the rubber band
and settles. Header release otherwise uses Foundation's fling behavior; nested release
chooses the nearest anchor below the velocity threshold, or the next directional anchor
above it.

The layout gives the header and footer their own space and allocates the remaining bounded
viewport to the body. `Scroll` mode measures finite body content inside a library-owned
vertical scroll container. Its natural height remains observable when the sheet clips its
viewport, enabling live content snap points without duplicate composition. `Fill` mode delegates
scroll ownership to the caller and is appropriate for lazy/reverse chat lists. Atlas never
asks a lazy list for intrinsic measurements, nor pretends to know the height of uncomposed
items. Use numeric or custom snap points for `Fill`.

Available height excludes top safe areas, the configured top margin, and navigation bars.
IME policies use the maximum of navigation-bar and keyboard insets, not their sum. `Resize`
retains the snap point while recalculating its geometry. `Expand` temporarily selects the largest
snap point and restores the prior ID on keyboard close if it still exists. `Ignore` deliberately
does not protect text entry from the keyboard. Compose/Android bring-into-view handles focused
fields inside scroll containers; custom content must implement its own focus scrolling.

`AtlasModalSheet` uses a full-size Compose Dialog to isolate focus and input at window level.
The OS dim flag is cleared, the dialog's system bars are transparent, and contrast overlays
are disabled. The scrim is drawn once in Compose across the full host, including system bars.
By default its alpha tracks the visible height until the lowest snap point and then holds. `scrimFollowsDrag = false` keeps that alpha at full strength whenever the sheet is visible.
The sheet surface continues its container color behind the navigation bar. Dismissal callback runs once
the sheet is visually gone, so a following tap is not swallowed by the spring's settling tail. `AtlasSheetHost` is an inline overlay with no modal focus
isolation. `scrimEnabled = false` removes scrim drawing and touch handling, permitting outside
touches to reach the background of an inline sheet. Color controls drawing only. An enabled
scrim blocks outside touches even when `dismissOnScrim = false`. Modal windows always block
background interaction; disabling their scrim also disables outside-tap dismissal. Place the host last in a
full-size Box. It does not own your navigation or determine when it is removed.

## Before a broad rollout

This is a 0.2.0 implementation, not a claim of proven reliability at millions-of-users scale.
Run your own device/OEM matrix and benchmark release builds on representative low-end hardware.
Pay particular attention to rapid animation/drag interruption, orientation and split-screen
resizing, oversized accessibility text, gesture-navigation insets, keyboard variants, RTL,
TalkBack navigation and custom AndroidView nested-scroll interop. The shipped test suite and
CI are a starting point. Predictive Back progress, linked motion or a shared scrim across
stacked modals, edge-to-edge visual tuning on every OEM, and a measured frame-time benchmark
suite are not implemented. Separate `AtlasModalSheet` calls do stack as independent dialogs.
