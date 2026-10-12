#!/usr/bin/env python3
"""Record the real Compose sample using adb + instrumentation; no mock UI or generated frames."""
import argparse
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SCENARIOS = ['detents', 'chat', 'contentFit', 'stickyActions', 'persistent', 'protectedFlow']

def run(*args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)

def record(scenario):
    out = ROOT / 'docs' / 'videos'
    out.mkdir(parents=True, exist_ok=True)
    remote = f'/sdcard/atlas-{scenario}.mp4'
    run('adb', 'shell', 'am', 'force-stop', 'dev.atlas.sheet.sample')
    recorder = subprocess.Popen(['adb', 'shell', 'screenrecord', '--bit-rate', '3000000', '--time-limit', '180', remote], stdout=subprocess.DEVNULL)
    try:
        result = run('adb', 'shell', 'am', 'instrument', '-w', '-e', 'class', f'dev.atlas.sheet.sample.DemoRecordingTest#{scenario}', 'dev.atlas.sheet.sample.test/androidx.test.runner.AndroidJUnitRunner', capture_output=True, text=True)
        if 'OK (1 test)' not in result.stdout:
            raise RuntimeError(result.stdout)
    finally:
        subprocess.run(['adb', 'shell', 'pkill', '-2', 'screenrecord'], check=False)
        recorder.wait(timeout=30)
    run('adb', 'pull', remote, str(out / f'{scenario}.mp4'))
    run('ffmpeg', '-y', '-i', str(out / f'{scenario}.mp4'), '-vf', 'fps=10,scale=360:-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse', str(out / f'{scenario}.gif'), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    run('adb', 'shell', 'rm', remote)
    print(f'Recorded {scenario}', flush=True)

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
    for scenario in args.scenarios or SCENARIOS:
        record(scenario)
