#!/usr/bin/env python3
"""Record the real Compose sample using adb + instrumentation; no mock UI or generated frames."""
import argparse
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SCENARIOS = ['detents', 'chat', 'contentFit', 'stickyActions', 'persistent', 'protectedFlow']
MARKER_TAG = 'AtlasDemo'  # DemoRecordingTest.MARKER_TAG
SCENARIO_TIMEOUT = 300  # seconds; a scenario normally finishes in well under a minute

def run(*args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)

def marker_times():
    """Device epoch seconds for each AtlasDemo logcat message, keyed by message."""
    log = run('adb', 'logcat', '-d', '-v', 'epoch', '-s', f'{MARKER_TAG}:I', capture_output=True, text=True).stdout
    times = {}
    for line in log.splitlines():
        fields = line.split()
        if len(fields) >= 2 and line.rstrip().endswith(f'{MARKER_TAG}: {fields[-1]}'):
            try:
                times[fields[-1]] = float(fields[0])
            except ValueError:
                pass
    return times

def encode(raw, mp4, gif, start):
    # Constant 30 fps H.264 with faststart; the GIF preview derives from the published MP4.
    run('ffmpeg', '-v', 'error', '-y', '-ss', f'{start:.3f}', '-i', str(raw), '-an', '-vf', 'fps=30',
        '-c:v', 'libx264', '-profile:v', 'high', '-pix_fmt', 'yuv420p', '-crf', '28', '-preset', 'slow',
        '-movflags', '+faststart', str(mp4))
    run('ffmpeg', '-v', 'error', '-y', '-i', str(mp4), '-vf',
        'fps=10,scale=360:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse', str(gif))

def record(scenario, work):
    out = ROOT / 'docs' / 'videos'
    out.mkdir(parents=True, exist_ok=True)
    remote = f'/sdcard/atlas-{scenario}.mp4'
    raw = work / f'{scenario}.mp4'
    run('adb', 'shell', 'am', 'force-stop', 'dev.atlas.sheet.sample')
    run('adb', 'logcat', '-c')
    run('adb', 'shell', 'log', '-p', 'i', '-t', MARKER_TAG, 'recording')
    recorder = subprocess.Popen(['adb', 'shell', 'screenrecord', '--bit-rate', '3000000', '--time-limit', '180', remote], stdout=subprocess.DEVNULL)
    try:
        result = run('adb', 'shell', 'am', 'instrument', '-w', '-e', 'class', f'dev.atlas.sheet.sample.DemoRecordingTest#{scenario}', 'dev.atlas.sheet.sample.test/androidx.test.runner.AndroidJUnitRunner', capture_output=True, text=True, timeout=SCENARIO_TIMEOUT)
        if 'OK (1 test)' not in result.stdout:
            raise RuntimeError(result.stdout)
    except subprocess.TimeoutExpired:
        subprocess.run(['adb', 'shell', 'am', 'force-stop', 'dev.atlas.sheet.sample'], check=False)
        raise RuntimeError(f'{scenario} did not finish within {SCENARIO_TIMEOUT}s')
    finally:
        subprocess.run(['adb', 'shell', 'pkill', '-2', 'screenrecord'], check=False)
        recorder.wait(timeout=30)
    run('adb', 'pull', remote, str(raw))
    run('adb', 'shell', 'rm', remote)
    times = marker_times()
    # screenrecord starts slightly after the 'recording' marker, so this trims at or just before 'ready'.
    start = max(0.0, times['ready'] - times['recording']) if 'ready' in times and 'recording' in times else 0.0
    encode(raw, out / f'{scenario}.mp4', out / f'{scenario}.gif', start)
    print(f'Recorded {scenario} (trimmed {start:.2f}s)', flush=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('scenarios', nargs='*', metavar='SCENARIO')
    parser.add_argument('--skip-build', action='store_true')
    args = parser.parse_args()
    if any(name not in SCENARIOS for name in args.scenarios):
        parser.error('Scenarios: ' + ', '.join(SCENARIOS))
    if not args.skip_build:
        run(str(ROOT / 'gradlew'), ':sample:assembleDebug', ':sample:assembleDebugAndroidTest', cwd=ROOT)
    run('adb', 'wait-for-device')
    run('adb', 'install', '-r', str(ROOT / 'sample/build/outputs/apk/debug/sample-debug.apk'))
    run('adb', 'install', '-r', str(ROOT / 'sample/build/outputs/apk/androidTest/debug/sample-debug-androidTest.apk'))
    run('adb', 'shell', 'settings', 'put', 'secure', 'show_ime_with_hard_keyboard', '1')
    failures = []
    with tempfile.TemporaryDirectory() as work:
        for scenario in args.scenarios or SCENARIOS:
            try:
                record(scenario, Path(work))
            except Exception as error:
                print(f'FAILED {scenario}: {error}', flush=True)
                failures.append(scenario)
    if failures:
        raise SystemExit('Failed scenarios: ' + ', '.join(failures))
