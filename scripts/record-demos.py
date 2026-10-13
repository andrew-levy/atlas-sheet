#!/usr/bin/env python3
"""Record the real Compose sample frame by frame using adb + instrumentation; no mock UI.

DemoRecordingTest advances the paused Compose clock one output frame at a time and screenshots the
device after each frame reaches the display. This script pulls those screenshots and assembles them
at the same frame duration, so recorded motion has its real timing even on a slow emulator.
"""
import argparse
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SCENARIOS = ['detents', 'chat', 'contentFit', 'stickyActions', 'persistent', 'protectedFlow', 'heavyContent']
PACKAGE = 'dev.atlas.sheet.sample'
FRAMES = f'/sdcard/Android/data/{PACKAGE}/files/demo-frames'
FRAME_MILLIS = 32  # DemoRecordingTest.FRAME_MILLIS
SCENARIO_TIMEOUT = 900  # seconds

def run(*args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)

def encode(frames, mp4, gif):
    # Constant 30 fps H.264 with faststart; the GIF preview derives from the published MP4.
    run('ffmpeg', '-v', 'error', '-y', '-framerate', f'{1000 / FRAME_MILLIS}', '-i', str(frames / '%05d.jpg'),
        '-vf', 'fps=30', '-c:v', 'libx264', '-profile:v', 'high', '-pix_fmt', 'yuv420p', '-crf', '28',
        '-preset', 'slow', '-movflags', '+faststart', str(mp4))
    run('ffmpeg', '-v', 'error', '-y', '-i', str(mp4), '-vf',
        'fps=10,scale=360:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse', str(gif))

def record(scenario, work):
    out = ROOT / 'docs' / 'videos'
    out.mkdir(parents=True, exist_ok=True)
    run('adb', 'shell', 'am', 'force-stop', PACKAGE)
    try:
        result = run('adb', 'shell', 'am', 'instrument', '-w', '-e', 'class', f'{PACKAGE}.DemoRecordingTest#{scenario}', f'{PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner', capture_output=True, text=True, timeout=SCENARIO_TIMEOUT)
    except subprocess.TimeoutExpired:
        subprocess.run(['adb', 'shell', 'am', 'force-stop', PACKAGE], check=False)
        raise RuntimeError(f'{scenario} did not finish within {SCENARIO_TIMEOUT}s')
    if 'OK (1 test)' not in result.stdout:
        raise RuntimeError(result.stdout)
    run('adb', 'pull', f'{FRAMES}/{scenario}', str(work), stdout=subprocess.DEVNULL)
    run('adb', 'shell', 'rm', '-rf', f'{FRAMES}/{scenario}')
    frames = work / scenario
    count = len(list(frames.glob('*.jpg')))
    encode(frames, out / f'{scenario}.mp4', out / f'{scenario}.gif')
    print(f'Recorded {scenario} ({count} frames, {count * FRAME_MILLIS / 1000:.1f}s)', flush=True)

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
