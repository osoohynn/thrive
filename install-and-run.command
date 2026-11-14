#!/bin/bash
# Apple Silicon Mac: install the tools, create a Pleos AVD, and launch Thrive.
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
cd "$project_dir"
trap 'echo "설치·실행 중 중단됐습니다 (줄 $LINENO). 위 오류를 확인한 뒤 이 파일을 다시 실행하세요." >&2' ERR

if [[ "$(uname -s)" != Darwin || "$(uname -m)" != arm64 ]]; then
    echo '이 파일은 Apple Silicon(M1/M2/M3/M4 등) Mac용입니다.' >&2
    exit 1
fi

mkdir -p "$project_dir/.tools"
echo '[1/7] Homebrew 확인'
if ! command -v brew >/dev/null 2>&1; then
    if [[ -x /opt/homebrew/bin/brew ]]; then
        eval "$(/opt/homebrew/bin/brew shellenv)"
    else
        curl --fail --location --retry 2 --connect-timeout 15 \
            https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh \
            -o "$project_dir/.tools/install-homebrew.sh"
        /bin/bash "$project_dir/.tools/install-homebrew.sh"
        eval "$(/opt/homebrew/bin/brew shellenv)"
    fi
fi

echo '[2/7] JDK 17 · Android Studio 설치'
if [[ ! -x "$(brew --prefix)/opt/openjdk@17/bin/java" ]]; then
    brew install openjdk@17
fi
export JAVA_HOME="$(brew --prefix)/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
if [[ ! -d '/Applications/Android Studio.app' && ! -d "$HOME/Applications/Android Studio.app" ]]; then
    brew install --cask android-studio
fi

# Keep this install's Android configuration separate from other Android projects.
export ANDROID_HOME="$project_dir/.tools/android-sdk"
export ANDROID_USER_HOME="$project_dir/.tools/android-user"
export ANDROID_AVD_HOME="$project_dir/.tools/avd"
export GRADLE_USER_HOME="$project_dir/.tools/gradle"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
mkdir -p "$ANDROID_USER_HOME" "$ANDROID_AVD_HOME"
feed_url='https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml'
printf 'count=1\nsrc00=%s\ndisp00=Pleos Connect System Image\nenabled00=true\n' "$feed_url" > "$ANDROID_USER_HOME/repositories.cfg"

echo '[3/7] Pleos 공식 이미지 확인 · Android SDK 설치'
curl --fail --location --retry 2 --connect-timeout 15 --max-time 120 \
    "$feed_url" -o "$project_dir/.tools/pleos-system-images.xml"
package_id="$(python3 - "$project_dir/.tools/pleos-system-images.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET

root = ET.parse(sys.argv[1]).getroot()
packages = []
for node in root.iter():
    if node.tag.rsplit('}', 1)[-1] != 'remotePackage':
        continue
    path = node.get('path', '')
    parts = path.split(';')
    if len(parts) != 4 or parts[0] != 'system-images' or parts[1] != 'android-34' or parts[3] != 'arm64-v8a':
        continue
    revision = next((x for x in node if x.tag.rsplit('}', 1)[-1] == 'revision'), None)
    numbers = {x.tag.rsplit('}', 1)[-1]: int(x.text or '0') for x in revision} if revision is not None else {}
    if numbers.get('preview', 0):
        continue
    packages.append((tuple(numbers.get(k, 0) for k in ('major', 'minor', 'micro')), path))
if not packages:
    sys.exit('Pleos 저장소에서 API 34 / arm64-v8a 이미지를 찾지 못했습니다. 저장소 제공 상태를 확인하세요.')
print(max(packages)[1])
PY
)"
printf '설치할 Pleos 이미지: %s\n' "$package_id"
echo '다음에 표시되는 SDK 라이선스를 읽고 동의 여부를 선택하세요.'
"$project_dir/scripts/android.sh" setup
sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
avdmanager="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
"$sdkmanager" --sdk_root="$ANDROID_HOME" 'emulator' 'platforms;android-34' "$package_id"
image_dir="$ANDROID_HOME/$(printf '%s' "$package_id" | tr ';' '/')"
if [[ ! -f "$image_dir/package.xml" || ! -f "$image_dir/system.img" ]]; then
    echo 'Pleos 이미지 설치가 완료되지 않았습니다. 라이선스 동의와 다운로드 오류를 확인하세요.' >&2
    exit 1
fi

echo '[4/7] 가상 차량 생성 (RAM 4 GB · 가로 화면 · Cold boot)'
avd_name='Thrive_Pleos'
avd_dir="$ANDROID_AVD_HOME/$avd_name.avd"
if [[ ! -f "$avd_dir/config.ini" ]]; then
    printf 'no\n' | "$avdmanager" create avd --name "$avd_name" --package "$package_id" --device 'Pleos_Connect_v2_0' --path "$avd_dir"
    python3 - "$avd_dir/config.ini" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
settings = {
    'hw.ramSize': '4096',
    'hw.lcd.width': '2560',
    'hw.lcd.height': '1440',
    'hw.lcd.density': '160',
    'hw.gpu.enabled': 'yes',
    'hw.gpu.mode': 'auto',
    'skin.name': '2560x1440',
    'skin.path': '2560x1440',
    'showDeviceFrame': 'no',
    'fastboot.forceColdBoot': 'yes',
}
lines = [line for line in path.read_text().splitlines() if line.split('=', 1)[0].strip() not in settings]
path.write_text('\n'.join(lines + [f'{k}={v}' for k, v in settings.items()]) + '\n')
PY
fi

echo '[5/7] 최신 앱 빌드'
"$project_dir/scripts/android.sh" assembleDebug

echo '[6/7] Pleos 에뮬레이터 실행'
adb="$ANDROID_HOME/platform-tools/adb"
"$adb" start-server
serial=''
devices="$("$adb" devices)"
while read -r candidate state remainder; do
    if [[ "$candidate" == emulator-* && "$state" == device ]]; then
        running_name="$("$adb" -s "$candidate" emu avd name 2>/dev/null | tr -d '\r' | head -n 1)"
        if [[ "$running_name" == "$avd_name" ]]; then
            serial="$candidate"
            break
        fi
    fi
done <<< "$devices"
if [[ -z "$serial" ]]; then
    port="$(python3 - <<'PY'
import socket

for port in range(5554, 5585, 2):
    try:
        with socket.socket() as console, socket.socket() as adb:
            console.bind(('127.0.0.1', port))
            adb.bind(('127.0.0.1', port + 1))
            print(port)
            break
    except OSError:
        continue
else:
    raise SystemExit('에뮬레이터 실행에 사용할 포트가 없습니다.')
PY
)"
    serial="emulator-$port"
    nohup "$ANDROID_HOME/emulator/emulator" -avd "$avd_name" -port "$port" \
        -memory 4096 -no-snapshot-load > "$project_dir/.tools/pleos-emulator.log" 2>&1 < /dev/null &
    emulator_pid=$!
fi
deadline=$((SECONDS + 300))
while [[ "$("$adb" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" != 1 ]]; do
    if [[ -n "${emulator_pid:-}" ]] && ! kill -0 "$emulator_pid" 2>/dev/null; then
        echo '에뮬레이터가 종료됐습니다. .tools/pleos-emulator.log를 확인하세요.' >&2
        tail -n 30 "$project_dir/.tools/pleos-emulator.log"
        exit 1
    fi
    if (( SECONDS >= deadline )); then
        echo '5분 안에 부팅되지 않았습니다. .tools/pleos-emulator.log를 확인하세요.' >&2
        exit 1
    fi
    sleep 3
done

echo '[7/7] APK 설치 · Thrive 실행'
"$adb" -s "$serial" install -r "$project_dir/app/build/outputs/apk/debug/app-debug.apk"
launch_result="$("$adb" -s "$serial" shell am start -W -n com.thrive.pleos/.MainActivity)"
printf '%s\n' "$launch_result"
if ! printf '%s\n' "$launch_result" | tr -d '\r' | awk '$1 == "Status:" && $2 == "ok" { found=1 } END { exit !found }'; then
    echo '앱 실행을 확인하지 못했습니다. 위 Activity Manager 응답을 확인하세요.' >&2
    exit 1
fi
printf '\nThrive를 실행했습니다. 가상 차량 창에서 목업 화면을 확인하세요.\n'
