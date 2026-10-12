# Contributing

Use Android Studio with JDK 17 and SDK 35. Preserve named-detent semantics and caller-owned lazy
scrolling. Add tests around user-observable behavior, particularly resizing and gesture handoff,
when changing those paths. Do not add a Material dependency to the core library.

Before a pull request:

```bash
./gradlew :atlas-sheet:testDebugUnitTest :atlas-sheet:lintRelease :sample:assembleDebug
./gradlew :sample:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.atlas.sheet.sample.SheetUiTest
```

Include the device/API/IME used and explain the reproduction scenario. When changing public API,
update README examples and architecture notes. Keep release artifacts and videos reproducible;
do not check build caches or credentials into the repository.
