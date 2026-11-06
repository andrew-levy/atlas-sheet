#!/usr/bin/env python3
"""Record the real light-mode Find My app frame by frame using adb + instrumentation; no mock UI.

DemoRecordingTest advances the paused Compose clock one output frame at a time and screenshots the
device after each frame reaches the display. This script pulls those screenshots and assembles them
at the same frame duration, so recorded motion has its real timing even on a slow emulator.
"""
import argparse
from pathlib import Path
import subprocess
import shutil
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SCENARIOS = ['snapPoints', 'tabs', 'chat', 'contentFit', 'stacked', 'scrollHandoff', 'tray', 'topSheet']
PACKAGE = 'dev.better.sheet.findmy'

FRAME_MILLIS = 32  # DemoRecordingTest.FRAME_MILLIS
SCENARIO_TIMEOUT = 900  # seconds

def run(*args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)

def encode(frames, mp4, gif):
    # Preserve native resolution for MP4; encode GIFs directly from the captured frames.
    # 540 px previews remain sharp on high-density displays at README card sizes.
    run('ffmpeg', '-v', 'error', '-y', '-framerate', f'{1000 / FRAME_MILLIS}', '-i', str(frames / '%05d.jpg'),
        '-vf', 'fps=30', '-c:v', 'libx264', '-profile:v', 'high', '-pix_fmt', 'yuv420p', '-crf', '18',
        '-preset', 'slow', '-movflags', '+faststart', str(mp4))
    encode_preview(frames / '%05d.jpg', gif, framerate=1000 / FRAME_MILLIS)

def encode_preview(source, gif, framerate=None):
    input_args = ['-framerate', str(framerate)] if framerate else []
    run('ffmpeg', '-v', 'error', '-y', *input_args, '-i', str(source), '-filter_complex',
        'fps=25,scale=540:-1:flags=lanczos,split[s0][s1];'
        '[s0]palettegen=stats_mode=diff[p];'
        '[s1][p]paletteuse=dither=sierra2_4a:diff_mode=rectangle',
        '-loop', '0', str(gif))

def record(scenario, work):
    package = 'dev.better.sheet.sample' if scenario in ('topSheet', 'glass') else PACKAGE
    device_frames = f'/sdcard/Android/data/{package}/files/demo-frames'
    out = ROOT / 'docs' / 'videos'
    out.mkdir(parents=True, exist_ok=True)
    run('adb', 'shell', 'am', 'force-stop', package)
    test_class = 'GlassRecordingTest' if scenario == 'glass' else 'DemoRecordingTest'
    try:
        result = run('adb', 'shell', 'am', 'instrument', '-w', '-e', 'class', f'{package}.{test_class}#{scenario}', f'{package}.test/androidx.test.runner.AndroidJUnitRunner', capture_output=True, text=True, timeout=SCENARIO_TIMEOUT)
    except subprocess.TimeoutExpired:
        subprocess.run(['adb', 'shell', 'am', 'force-stop', package], check=False)
        raise RuntimeError(f'{scenario} did not finish within {SCENARIO_TIMEOUT}s')
    if 'OK (1 test)' not in result.stdout:
        raise RuntimeError(result.stdout)
    run('adb', 'pull', f'{device_frames}/{scenario}', str(work), stdout=subprocess.DEVNULL)
    run('adb', 'shell', 'rm', '-rf', f'{device_frames}/{scenario}')
    frames = work / scenario
    count = len(list(frames.glob('*.jpg')))
    encode(frames, out / f'{scenario}.mp4', out / f'{scenario}.gif')
    if scenario == 'glass':
        # Include a 1:1 detail frame alongside the phone-sized animation.
        run('ffmpeg', '-v', 'error', '-y', '-i', str(frames / '00015.jpg'), '-vf', 'crop=iw:ih/3:0:ih/3', str(out / 'glass-detail.png'))
    shutil.rmtree(frames)
    print(f'Recorded {scenario} ({count} frames, {count * FRAME_MILLIS / 1000:.1f}s)', flush=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('scenarios', nargs='*', metavar='SCENARIO')
    parser.add_argument('--skip-build', action='store_true')
    args = parser.parse_args()
    if any(name not in SCENARIOS + ['glass'] for name in args.scenarios):
        parser.error('Scenarios: ' + ', '.join(SCENARIOS))
    scenarios = args.scenarios or SCENARIOS
    modules = []
    if any(name not in ('topSheet', 'glass') for name in scenarios):
        modules.append('findmy')
    if any(name in ('topSheet', 'glass') for name in scenarios):
        modules.append('sample')
    if not args.skip_build:
        tasks = [f':{module}:{task}' for module in modules for task in ('assembleDebug', 'assembleDebugAndroidTest')]
        run(str(ROOT / 'gradlew'), *tasks, cwd=ROOT)
        run(str(ROOT / 'gradlew'), '--stop', cwd=ROOT)
    run('adb', 'wait-for-device')
    for module in modules:
        apk_dir = ROOT / 'samples' / module / 'build' / 'outputs' / 'apk'
        run('adb', 'install', '-r', str(apk_dir / 'debug' / f'{module}-debug.apk'))
        run('adb', 'install', '-r', str(apk_dir / 'androidTest' / 'debug' / f'{module}-debug-androidTest.apk'))
    run('adb', 'shell', 'settings', 'put', 'secure', 'show_ime_with_hard_keyboard', '1')
    failures = []
    with tempfile.TemporaryDirectory() as work:
        for scenario in scenarios:
            try:
                record(scenario, Path(work))
            except Exception as error:
                print(f'FAILED {scenario}: {error}', flush=True)
                failures.append(scenario)
    if failures:
        raise SystemExit('Failed scenarios: ' + ', '.join(failures))

