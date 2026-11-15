#!/usr/bin/env python3
"""Run isolated SDK checks; never targets the main app or deletes records."""
import argparse
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--adb', default='adb')
parser.add_argument('--serial', required=True)
parser.add_argument('--user', required=True, type=int)
parser.add_argument('--scenario', choices=['mvp_storage', 'mvp_events', 'mvp_roundtrip', 'mvp_stability'], action='append')
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
adb = [args.adb, '-s', args.serial]
package = 'com.thrive.pleos.verification'
for path in ['debug/app-debug.apk', 'androidTest/debug/app-debug-androidTest.apk']:
    subprocess.run(adb + ['install', '-r', str(root / 'app/build/outputs/apk' / path)], check=True)
for scenario in args.scenario or ['mvp_storage', 'mvp_events', 'mvp_roundtrip']:
    result = subprocess.run(adb + ['shell', 'am', 'instrument', '--user', str(args.user), '-w',
        '-e', 'scenario', scenario, package + '.test/com.thrive.pleos.driving.RecordingLifecycleChecks'],
        text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=1100 if scenario == 'mvp_stability' else 180)
    print(result.stdout, flush=True)
    if result.returncode != 0 or 'PASS:' not in result.stdout or 'FAIL:' in result.stdout:
        raise SystemExit('Check failed: ' + scenario)
