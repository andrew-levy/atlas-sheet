# Changelog

All notable changes to Atlas Sheet are documented here. The project follows
[Semantic Versioning](https://semver.org); while the version is 0.x, minor releases may change
public API.

## [Unreleased]

## [0.3.0] - 2026-10-17

### Breaking API changes

- Rename `SheetDetent` / `SheetDetentScope` to `SheetSnapPoint` / `SheetSnapPointScope`,
  `detents` to `snapPoints`, and `content()` to `fitContent()` (the default ID remains `"content"`).
- Rename `initialDetent`, `currentDetent`, and `targetDetent` to `initialSnapPoint`,
  `currentSnapPoint`, and `targetSnapPoint`; method argument `detent` becomes `snapPoint`.
- Rename `SheetImePolicy` / `imePolicy` to `SheetKeyboardBehavior` / `keyboardBehavior`.
- Rename `AtlasModalSheet.onDismissRequest` to `onDismissed`; it runs after dismissal completes.
- Stack APIs use `SheetStackEntry`, `SheetStackEffectScope`, `LocalSheetStackEntry`,
  `Modifier.sheetStackEffect`, and the `stackState` parameter.
- Remove the obsolete `expandBeforeScroll` parameter. Content owns gestures that begin
  by scrolling; fresh edge gestures use `bubbleAtTop` and `bubbleAtBottom` independently.
- Scrim color controls drawing only. Use `SheetBehavior(scrimEnabled = false)` to remove
  both scrim drawing and touch handling. Enabled transparent scrims still intercept touches.
  Modal windows continue to block background interaction when their scrim is disabled.

### Changed

- Use a short, critically damped settle spring that preserves flick velocity. Open, close,
  snap-point changes, content-fit resize, and rubber-band return share the state animation spec.
- Dragging past the top, or past the lowest detent when drag dismissal is off, rubber-bands and springs back. A dismissible pull downward still tracks the finger off screen.

### Added

- Coordinated modal stacks through `rememberSheetStackState()`, with live cumulative depth
  and customizable surface effects. Each sheet retains its own visibility state.
- Independent content-edge handoff switches and nested release velocity tuning.
- `SheetBehavior.scrimEnabled` (default `true`) and explicit blocking of outside touches
  when scrim dismissal is disabled.
- Usage documentation and GIFs for modal and inline sheets, stacks, keyboard handling,
  and common parameters; Fragment integration examples.
- `SheetBehavior.scrimFollowsDrag` (default `true`). The scrim fades with the drag until the lowest detent, then stays fully opaque. Set it to `false` to hold the scrim at full strength whenever the sheet is visible.

### Fixed

- Reaching a list edge during a drag or fling no longer starts moving the sheet.
- Release background touch interception promptly after the sheet is visually dismissed.
- Upward overscroll stretches from the top while keeping the bottom edge and footer seated.

## [0.2.0] - 2026-10-13

### Changed

- The scrim covers the whole host, including the status bar, navigation bar, and display
  cutout, so the dimming has no seam at the system bars.
- Modal windows use transparent system bars and turn off contrast overlays. The sheet
  continues its container color behind the navigation bar.
- The sample scrolls body content before moving the sheet (`expandBeforeScroll = false`).

### Fixed

- Chat lists in the sample are top-aligned. Dragging the sheet carries the messages with
  it, and further dragging scrolls them until they run out.

## [0.1.0] - 2026-10-13

Initial public release.

### Added

- `AtlasModalSheet`: a modal sheet in its own Dialog window with focus and input isolation, a
  Compose-drawn scrim, Back, scrim and drag dismissal policies, and accessibility actions.
- `AtlasSheetHost`: an inline overlay host for persistent panels over interactive content.
- `AtlasSheet`: an always-mounted, in-place root sheet for Activities and ComposeView Fragments,
  with `show()`/`hide()` and an `onDismissed` callback after the exit animation.
- Named detents: fixed, fraction, content (natural size), expanded and custom.
- `SheetState` with `animateTo`, `snapTo`, `show`, `hide`, `expand` and `collapse`, saveable
  across recreation, plus `currentDetent`, `targetDetent`, `isVisible` and continuous `progress`.
- Animated entry from hidden after the first chrome and body measurement, animated exit, and live
  animated content-fit resizing, all using the state's `animationSpec`.
- Sticky header and footer chrome that keeps its space in small windows, and `SheetBodyMode.Scroll`
  or `Fill` (for caller-owned lazy lists) with nested-scroll handoff between body and sheet.
- Keyboard policies: `SheetImePolicy.Resize`, `Expand` (with restoration) and `Ignore`.

### Known limitations

- Under heavy CPU load on low-core devices, the start of the opening animation can be delayed;
  see `docs/VALIDATION.md` for measurements.
- Accessibility, configuration changes, OEM keyboards and real-device performance need broader
  coverage before production rollout at scale.

[0.3.0]: https://github.com/andrew-levy/atlas-sheet/releases/tag/0.3.0
[0.2.0]: https://github.com/andrew-levy/atlas-sheet/releases/tag/v0.2.0
[0.1.0]: https://github.com/andrew-levy/atlas-sheet/releases/tag/v0.1.0
