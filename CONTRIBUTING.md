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

Demo recordings live in `docs/videos/` and are not shown in the README. To recreate them, start
an emulator with `adb` and `ffmpeg` on `PATH`, then run:

```bash
python3 scripts/record-demos.py
python3 scripts/record-demos.py chat
```

The script builds and installs the sample and its test APK, runs one recording test per scenario,
and assembles per-frame screenshots into a 30 fps MP4 and a GIF. Recording tests are excluded
from normal CI. Run the Android workflow manually with `record_demos`, or push a commit whose
message contains `[record-demos]`.

## Releasing

Project convention: use plain semantic versions without a `v` prefix everywhere: Git tags,
GitHub release titles, `VERSION_NAME`, and dependency examples (for example, `0.2.1`).
Keep existing published tags intact; do not move or delete them to fix publication failures.

1. Update `VERSION_NAME` in `gradle.properties` and add a matching `## [x.y.z]` section to
   `CHANGELOG.md`.
2. Merge to `main`, then tag and push: `git tag X.Y.Z && git push origin X.Y.Z`.
3. The Release workflow checks that the tag matches `VERSION_NAME`, runs unit tests and lint, and
   creates a GitHub release with the changelog section and artifacts. JitPack builds the tag on
   first request. The workflow requests and verifies the JitPack POM and AAR; a failed or
   unavailable publication fails the workflow instead of reporting a successful release.
4. Update the README dependency version to the new tag. Confirm the Release workflow succeeds
   before announcing that the dependency is available. If JitPack fails, inspect its build log;
   do not claim a GitHub release alone means JitPack published successfully.
5. When the Maven Central secrets are set, the workflow also uploads a signed deployment to the
   Central Portal. Open <https://central.sonatype.com/publishing/deployments>, review it, and
   click **Publish**. It usually appears on Maven Central within about 30 minutes.

One-time Maven Central setup:

- Sign in to <https://central.sonatype.com> with the `andrew-levy` GitHub account; the
  `io.github.andrew-levy` namespace is verified automatically. Generate a user token.
- Create a signing key (`gpg --quick-gen-key "Andrew Levy <email>" rsa4096 sign 0`), publish it
  (`gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>`), and export it with
  `gpg --armor --export-secret-keys <KEY_ID>`.
- Add repository secrets: `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD` (the token),
  `SIGNING_KEY` (the exported key) and `SIGNING_KEY_PASSWORD`.

Maven Central releases are permanent; a published version cannot be replaced or deleted.
