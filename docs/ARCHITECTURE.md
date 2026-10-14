# Architecture and interaction contract

Atlas has four layers: detent resolution, state, interaction coordination, and rendering.

`SheetGeometry` is Android-independent. It resolves heights into downward-growing offsets,
clamps them to the available viewport and sticky chrome, and collapses equivalent physical
anchors into deterministic aliases. The first declared detent wins a collision. Anchor
positions are recalculated on content, chrome, window and IME changes; IDs are stable.

`SheetState` wraps Foundation's `AnchoredDraggableState`, preserving its mutation arbitration
and animation cancellation. `currentDetent` means settled, `targetDetent` means in flight,
and `progress` is continuous visible height. A hidden anchor is excluded from user dragging
when drag dismissal is disabled. Explicit `hide()` temporarily adds it. State saving records
a detent ID, not stale pixels. A removed ID falls back to the smallest surviving detent.

The handle and header use `anchoredDraggable`. The body participates in nested scrolling:
upward input expands the sheet before the child scrolls by default; downward input is given
to the child first, and only its unconsumed distance moves the sheet. Setting
`expandBeforeScroll = false` lets the child scroll first in both directions. Vertical input
alone is consumed; horizontal gestures are left to children. Header release uses
Foundation's default fling behavior; nested release chooses the nearest anchor below the
velocity threshold, or the next directional anchor above it.

The layout gives the header and footer their own space and allocates the remaining bounded
viewport to the body. `Scroll` mode measures finite body content inside a library-owned
vertical scroll container. Its natural height remains observable when the sheet clips its
viewport, enabling live content detents without duplicate composition. `Fill` mode delegates
scroll ownership to the caller and is appropriate for lazy/reverse chat lists. Atlas never
asks a lazy list for intrinsic measurements, nor pretends to know the height of uncomposed
items. Use numeric or custom detents for `Fill`.

Available height excludes top safe areas, the configured top margin, and navigation bars.
IME policies use the maximum of navigation-bar and keyboard insets, not their sum. `Resize`
retains the detent while recalculating its geometry. `Expand` temporarily selects the largest
detent and restores the prior ID on keyboard close if it still exists. `Ignore` deliberately
does not protect text entry from the keyboard. Compose/Android bring-into-view handles focused
fields inside scroll containers; custom content must implement its own focus scrolling.

`AtlasModalSheet` uses a full-size Compose Dialog to isolate focus and input at window level.
The OS dim flag is cleared, the dialog's system bars are transparent, and contrast overlays
are disabled. The scrim is drawn once in Compose across the full host, including system bars.
The sheet surface continues its container color behind the navigation bar. Dismissal callback runs after
the hidden animation settles. `AtlasSheetHost` is an inline overlay with no modal focus
isolation; a scrim-free host permits interaction with its background. Place it last in a
full-size Box. It does not own your navigation or determine when it is removed.

## Before a broad rollout

This is a 0.2.0 implementation, not a claim of proven reliability at millions-of-users scale.
Run your own device/OEM matrix and benchmark release builds on representative low-end hardware.
Pay particular attention to rapid animation/drag interruption, orientation and split-screen
resizing, oversized accessibility text, gesture-navigation insets, keyboard variants, RTL,
TalkBack navigation and custom AndroidView nested-scroll interop. The shipped test suite and
CI are a starting point. Predictive Back progress, stacked modal coordination, edge-to-edge
visual tuning on every OEM, and a measured frame-time benchmark suite are not implemented.
