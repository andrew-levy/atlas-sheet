#!/usr/bin/env bash
set -euo pipefail

mkdir -p device-diagnostics
# Stream logs before testing so a system_server restart does not erase the cause.
adb logcat -b all -v threadtime > device-diagnostics/logcat.txt 2>&1 &
logcat_pid=$!
finish() {
    result=$?
    trap - EXIT
    kill "$logcat_pid" 2>/dev/null || true
    if [ "$result" -ne 0 ]; then
        adb shell dumpsys meminfo > device-diagnostics/meminfo.txt 2>&1 || true
        adb shell dumpsys activity > device-diagnostics/activity.txt 2>&1 || true
        adb shell dumpsys window > device-diagnostics/window.txt 2>&1 || true
        adb shell getprop > device-diagnostics/properties.txt 2>&1 || true
    fi
    exit "$result"
}
trap finish EXIT

# A responding adb connection alone does not mean Android services are ready.
ready=false
for attempt in $(seq 1 60); do
    if [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ] &&
        adb shell pm path android | grep '^package:' > /dev/null &&
        adb shell dumpsys window | grep 'mCurrentFocus=Window{' > /dev/null; then
        ready=true
        break
    fi
    sleep 2
done
if [ "$ready" != true ]; then
    echo 'Android did not finish booting with working package and window services.' >&2
    exit 1
fi
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell settings put global hide_error_dialogs 1
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS

# APKs were compiled before starting the emulator. Keep the test driver's heap
# small and release it between suites, leaving memory for Android and its GPU.
gradle_args=(--no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1g)
./gradlew "${gradle_args[@]}" :sample:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=dev.better.sheet.sample.SheetUiTest,dev.better.sheet.sample.SheetMotionTest,dev.better.sheet.sample.FragmentSheetTest,dev.better.sheet.sample.HeavyContentMotionTest,dev.better.sheet.sample.TrayRenderingTest,dev.better.sheet.sample.AnimationCustomizationTest,dev.better.sheet.sample.EntranceRenderingTest,dev.better.sheet.sample.GlassRenderingTest
./gradlew "${gradle_args[@]}" :findmy:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=dev.better.sheet.findmy.FindMyNavigationTest
