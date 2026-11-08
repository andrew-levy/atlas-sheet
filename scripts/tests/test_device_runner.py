"""Check boot gating and failure propagation without an emulator."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "run-device-tests.sh"


class DeviceRunnerTest(unittest.TestCase):
    def run_script(self, *, ready=True, gradle_status=0):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            adb = root / "adb"
            adb.write_text('''#!/usr/bin/env bash
case "$*" in
  'shell getprop sys.boot_completed') echo "$BOOT_READY" ;;
  'shell pm path android') echo package:/system/framework/framework-res.apk ;;
  'shell dumpsys window') echo 'mCurrentFocus=Window{123 u0 test}' ;;
  *) echo "$*" ;;
esac
''')
            gradle = root / "gradlew"
            gradle.write_text('''#!/usr/bin/env bash
echo "$*" >> gradle-calls.txt
exit "$GRADLE_STATUS"
''')
            sleep = root / "sleep"
            sleep.write_text('#!/usr/bin/env bash\nexit 0\n')
            for executable in (adb, gradle, sleep):
                executable.chmod(0o755)
            env = dict(os.environ, PATH=f"{root}:{os.environ['PATH']}",
                       BOOT_READY="1" if ready else "0", GRADLE_STATUS=str(gradle_status))
            result = subprocess.run(["bash", str(SCRIPT)], cwd=root, env=env,
                                    capture_output=True, text=True, timeout=15)
            calls = root / "gradle-calls.txt"
            return result, calls.read_text().splitlines() if calls.exists() else [], {
                p.name for p in (root / "device-diagnostics").iterdir()
            }

    def test_ready_device_runs_both_suites(self):
        result, calls, diagnostics = self.run_script()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(len(calls), 3)
        self.assertIn(":sample:connectedDebugAndroidTest", calls[0])
        self.assertIn("GlassRenderingTest", calls[0])
        self.assertIn(":sample:connectedDebugAndroidTest", calls[1])
        self.assertIn(":findmy:connectedDebugAndroidTest", calls[2])
        self.assertIn("logcat.txt", diagnostics)

    def test_failed_suite_stays_failed_and_collects_diagnostics(self):
        result, calls, diagnostics = self.run_script(gradle_status=42)
        self.assertEqual(result.returncode, 42, result.stderr)
        self.assertEqual(len(calls), 1, "Do not mask an assertion failure with a retry")
        self.assertTrue({"logcat.txt", "meminfo.txt", "activity.txt", "window.txt", "properties.txt"} <= diagnostics)

    def test_unready_device_never_starts_tests(self):
        result, calls, diagnostics = self.run_script(ready=False)
        self.assertEqual(result.returncode, 1)
        self.assertIn("did not finish booting", result.stderr)
        self.assertEqual(calls, [])
        self.assertIn("properties.txt", diagnostics)


if __name__ == "__main__":
    unittest.main()
