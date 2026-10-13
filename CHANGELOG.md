# Changelog

All notable changes to Atlas Sheet are documented here. The project follows
[Semantic Versioning](https://semver.org); while the version is 0.x, minor releases may change
public API.

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

[0.1.0]: https://github.com/andrew-levy/atlas-sheet/releases/tag/v0.1.0
