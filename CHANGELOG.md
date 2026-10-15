# Changelog

All notable changes to Atlas Sheet are documented here. The project follows
[Semantic Versioning](https://semver.org); while the version is 0.x, minor releases may change
public API.

## [Unreleased]

### Changed

- The default settle spring matches the iOS system spring: perceptual duration 0.5s, critically damped, no bounce. Open, close, detent changes, flings, content-fit resize, and the rubber-band return all use it. Pass `animationSpec` to `rememberSheetState` to keep a different curve.
- Dragging past the top, or past the lowest detent when drag dismissal is off, rubber-bands and springs back. A dismissible pull downward still tracks the finger off screen.

### Added

- `SheetBehavior.scrimFollowsDrag` (default `true`). The scrim fades with the drag until the lowest detent, then stays fully opaque. Set it to `false` to hold the scrim at full strength whenever the sheet is visible.

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

[0.2.0]: https://github.com/andrew-levy/atlas-sheet/releases/tag/v0.2.0
[0.1.0]: https://github.com/andrew-levy/atlas-sheet/releases/tag/v0.1.0
