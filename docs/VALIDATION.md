# Validation

## Device suite — October 13, 2026

`SheetUiTest`, `SheetMotionTest` and `FragmentSheetTest` (10 tests) passed on both API 24 and
API 35 x86_64 Pixel 4 emulators with system animations enabled:
[run 38390263772](https://github.com/andrew-levy/atlas-sheet/actions/runs/38390263772).
They cover intermediate entry/exit/resize frames, interrupted entry, mounted-body retention across
hide/show, keyboard expansion and restoration, and a real Fragment ComposeView being disposed
after an animated exit.

Earlier API 24 jobs never booted: the emulator aborted with "Not enough space to create userdata
partition", so the workflow now frees hosted-runner disk first. The chat keyboard test was flaky on
API 35 because Compose test clicks could focus the composer before the dialog window had input
focus; it now taps the composer through the device. A separate API 24 run hit an emulator
`DeadSystemException`, an infrastructure failure rather than a test assertion.

## Demo recordings — October 13, 2026

All six `DemoRecordingTest` scenarios were recorded in the same run on the API 35 emulator,
1080 × 2280 at density 440, and published in `docs/videos/`:

- detents: peek, expanded, medium, list scroll handoff and close
- chat: keyboard expansion, typing, sending and keyboard dismissal
- contentFit: options added and removed with live animated sizing, then close
- stickyActions: typing with the keyboard open, scrolling the form, pinned actions
- persistent: inline host detent changes and close, without a scrim
- protectedFlow: Back ignored, followed by explicit completion

The software-rendered CI emulator composites only about 2.5 frames per second, so `screenrecord`
missed most sheet motion. Clips are therefore captured frame by frame: the test advances the paused
Compose clock by 32 ms, waits for that frame to reach the display, and screenshots the full screen,
including dialog and IME windows. Frames are assembled into constant 30 fps H.264 with `faststart`,
and GIFs derive from those MP4s. Compose motion keeps its real timing; the IME animates on the
device clock and can appear abruptly. These are real sample screenshots, not frame-time benchmarks
or open-latency measurements.

Representative frames were reviewed for opening, detent, resize, keyboard and closing states.

## Local checks

The library, sample APK and instrumentation APK build. All 27 library unit tests pass.
Release lint passes (dependency-version notices only).

## Remaining verification

Accessibility, configuration changes, performance, OEM keyboards and production rollout
still need broader device coverage. No production traffic evidence or representative
frame-time benchmarks are provided by these demonstration videos.
