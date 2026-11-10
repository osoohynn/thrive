"""Offline installer checks with fake external tools; no downloads or installations."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

PROJECT = Path(__file__).resolve().parents[2]
FEED = '''<repository><remotePackage path="system-images;android-34;pleos;arm64-v8a"><revision><major>2</major><minor>1</minor></revision></remotePackage><remotePackage path="system-images;android-34;pleos;arm64-v8a"><revision><major>3</major><preview>1</preview></revision></remotePackage><remotePackage path="system-images;android-34;pleos;x86_64"><revision><major>4</major></revision></remotePackage></repository>'''

with tempfile.TemporaryDirectory(prefix='pleos-installer-') as temporary:
    root = Path(temporary)
    shutil.copy(PROJECT / 'install-and-run.command', root)
    bin_dir = root / 'bin'
    bin_dir.mkdir()
    def executable(path, text):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text('#!/bin/bash\nset -e\n' + text)
        path.chmod(0o755)
    executable(bin_dir / 'brew', 'if [[ "$1" == --prefix ]]; then echo "$FIXTURE/brew"; else echo "brew $*" >> "$FIXTURE/calls"; fi\n')
    executable(bin_dir / 'curl', 'while [[ $# -gt 0 ]]; do if [[ "$1" == -o ]]; then cp "$FIXTURE/feed.xml" "$2"; break; fi; shift; done\n')
    java = root / 'brew/opt/openjdk@17/bin/java'
    executable(java, 'exit 0\n')
    executable(root / 'scripts/android.sh', 'echo "android $*" >> "$FIXTURE/calls"\n')
    sdk = root / '.tools/android-sdk'
    executable(sdk / 'cmdline-tools/latest/bin/sdkmanager', '''echo "sdkmanager $*" >> "$FIXTURE/calls"
if [[ "${FIXTURE_MISSING_IMAGE:-}" != 1 ]]; then
mkdir -p "$ANDROID_HOME/system-images/android-34/pleos/arm64-v8a"
touch "$ANDROID_HOME/system-images/android-34/pleos/arm64-v8a/"{package.xml,system.img}
fi
''')
    executable(sdk / 'cmdline-tools/latest/bin/avdmanager', '''echo 'create avd' >> "$FIXTURE/calls"
while [[ $# -gt 0 ]]; do if [[ "$1" == --path ]]; then mkdir -p "$2"; printf 'hw.ramSize=512\\nskin.name=old\\n' > "$2/config.ini"; break; fi; shift; done
''')
    executable(sdk / 'platform-tools/adb', '''echo "adb $*" >> "$FIXTURE/calls"
if [[ "$1" == devices ]]; then printf 'List of devices attached\\nemulator-5554\\tdevice\\n'
elif [[ "$1" == start-server || "$3" == install ]]; then exit 0
elif [[ "$3" == emu ]]; then printf 'Thrive_Pleos\\nOK\\n'
elif [[ "$4" == getprop ]]; then echo 1
elif [[ "${FIXTURE_LAUNCH_FAIL:-}" == 1 ]]; then echo 'Error: Activity not found'
else echo 'Status: ok'
fi
''')
    (root / 'feed.xml').write_text(FEED)
    env = {**os.environ, 'PATH': f'{bin_dir}:{os.environ["PATH"]}', 'FIXTURE': str(root)}
    def run(**overrides):
        return subprocess.run(['bash', str(root / 'install-and-run.command')], env={**env, **overrides}, capture_output=True, text=True, timeout=20)
    first = run()
    assert first.returncode == 0, (first.stdout, first.stderr)
    config = root / '.tools/avd/Thrive_Pleos.avd/config.ini'
    assert 'hw.ramSize=4096\n' in config.read_text()
    assert 'hw.ramSize=512' not in config.read_text()
    assert 'system-images;android-34;pleos;arm64-v8a' in (root / 'calls').read_text()
    assert 'src00=https://' in (root / '.tools/android-user/repositories.cfg').read_text()
    config.write_text(config.read_text() + 'keep.existing.data=yes\n')
    assert run().returncode == 0
    assert 'keep.existing.data=yes' in config.read_text()
    assert (root / 'calls').read_text().count('create avd') == 1
    assert run(FIXTURE_LAUNCH_FAIL='1').returncode != 0
    image = sdk / 'system-images/android-34/pleos/arm64-v8a/system.img'
    image.unlink()
    assert run(FIXTURE_MISSING_IMAGE='1').returncode != 0
    (root / 'feed.xml').write_text('<repository/>')
    assert run().returncode != 0
print('PASS: image selection, install sequence, AVD creation/reuse, missing image, empty feed, launch failure')
