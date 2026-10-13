# Motion and Fragment-root update

The smooth-fragment-root branch builds the library, sample APK and test APK locally.
All 27 library unit tests pass. Release lint passes (dependency-version notices only).

Added device checks cover intermediate entry/exit/resize frames, mounted-body retention
across hide/show, and a real Fragment ComposeView being disposed after an animated exit.
The API 24/35 workflow now includes these tests with system animation scales enabled.
Their device results and revised recordings are still being collected.

The original gallery used an auto-advancing Compose test clock, so it cannot establish
real-world transition timing or an opening latency. Recording tests now advance individual
frames with a wall-clock pause instead of draining animations between screen recordings.
No representative production frame-time benchmark has been performed.

## Baseline validation

## Demo recordings — October 12, 2026

All six `DemoRecordingTest` scenarios completed successfully on an Android API 35,
x86_64 Pixel 4 emulator, 1080 × 2280 at density 440.

- detents: peek, medium, expanded, scrolling and explicit close
- chat: keyboard expansion, typing, sending and keyboard dismissal
- contentFit: add/remove options, live sizing and explicit close
- stickyActions: long form scrolling with keyboard and pinned actions
- persistent: inline host detent changes and close, without a scrim
- protectedFlow: Back ignored, followed by explicit completion

[Successful recording run](https://github.com/andrew-levy/atlas-sheet/actions/runs/38324920419)
used commit `7939eaa3ba53fbb93f01e7c498047b22c738208c`. The sample and test APKs
built and installed in that run. Recording tests require `OK (1 test)` for each scenario.
The original MP4s and GIFs are retained in its `demo-videos` Actions artifact.

Published clips trim startup screens and use constant-frame-rate H.264 with `faststart`;
GIF previews derive from the same screen recordings. All final MP4s are decoded with ffmpeg
and representative frames are reviewed for visible UI, keyboard, sheet and footer behavior.

## Local checks

The updated sample and instrumentation APKs build successfully. All 25 library unit tests pass.
Release lint passed in the earlier local validation.

## Remaining verification

This recording result does not establish that the full API 24/35 behavioral matrix passes.
Earlier remote runs found a content-fit measurement crash and a provisional-detent test failure;
those published-source mismatches were corrected before this recording run. The earlier
API 24 emulator also timed out during boot. The full device suite needs a fresh completed run,
including the latest sample gallery semantics, before claiming complete CI validation.

Accessibility, configuration changes, performance, OEM keyboards and production rollout
still need broader device coverage. No production traffic evidence or representative
frame-time benchmarks are provided by these demonstration videos.
