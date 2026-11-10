#!/bin/bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_dir"

# Gradle 8.11.1 cannot run on the JDK 25 bundled with the installed IntelliJ.
if [[ -z "${JAVA_HOME:-}" ]]; then
    for candidate in /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home /usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home; do
        if [[ -x "$candidate/bin/java" ]]; then
            export JAVA_HOME="$candidate"
            break
        fi
    done
fi
if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]]; then
    echo 'JDK 17이 필요합니다. 터미널에서 brew install openjdk@17 실행 후 다시 시도하세요.' >&2
    exit 1
fi
java_version="$("$JAVA_HOME/bin/java" -version 2>&1)"
if [[ ! "$java_version" =~ version\ \"17\. ]]; then
    echo '이 프로젝트는 JDK 17을 사용합니다. JAVA_HOME을 JDK 17 경로로 지정하세요.' >&2
    exit 1
fi

export ANDROID_HOME="${ANDROID_HOME:-$project_dir/.tools/android-sdk}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$project_dir/.tools/gradle}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

case "${1:-assembleDebug}" in
    run)
        adb="$ANDROID_HOME/platform-tools/adb"
        if [[ ! -x "$adb" ]]; then
            echo 'ADB가 없습니다. ./scripts/android.sh setup을 먼저 실행하세요.' >&2
            exit 1
        fi
        devices="$("$adb" devices)"
        if [[ -n "${ANDROID_SERIAL:-}" ]]; then
            serial="$ANDROID_SERIAL"
            if [[ "$("$adb" -s "$serial" get-state)" != device ]]; then
                echo "기기에 연결할 수 없습니다: $serial" >&2
                exit 1
            fi
        else
            serials="$(printf '%s\n' "$devices" | awk '$2 == "device" { print $1 }')"
            device_count="$(printf '%s\n' "$serials" | awk 'NF { count++ } END { print count+0 }')"
            if [[ "$device_count" == 0 ]]; then
                echo '실행 중인 에뮬레이터가 없습니다. 실물 차량은 필요하지 않습니다.' >&2
                echo '먼저 Android Studio에서 Pleos 시스템 이미지를 설치하고, Device Manager → Create device → Automotive로 가상 차량을 생성·실행하세요.' >&2
                echo "설치 순서와 이미지 저장소 URL: $project_dir/README.md (Android Studio에서 열기와 실행)" >&2
                exit 1
            elif [[ "$device_count" != 1 ]]; then
                echo '여러 실행 대상이 있습니다. ANDROID_SERIAL로 실행할 에뮬레이터를 지정하세요.' >&2
                exit 1
            fi
            serial="$serials"
        fi
        if [[ "$("$adb" -s "$serial" shell getprop sys.boot_completed | tr -d '\r')" != 1 ]]; then
            echo '기기가 아직 부팅 중입니다. 부팅 완료 후 다시 실행하세요.' >&2
            exit 1
        fi
        "$0" assembleDebug
        "$adb" -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
        launch_result="$("$adb" -s "$serial" shell am start -W -n com.thrive.pleos/.MainActivity)"
        printf '%s\n' "$launch_result"
        if ! printf '%s\n' "$launch_result" | tr -d '\r' | awk '$1 == "Status:" && $2 == "ok" { found=1 } END { exit !found }'; then
            echo '앱 실행을 확인하지 못했습니다. 위 Activity Manager 응답을 확인하세요.' >&2
            exit 1
        fi
        ;;
    setup)
        if [[ "$(uname -s)" != Darwin || "$(uname -m)" != arm64 ]]; then
            echo '자동 설치는 Apple Silicon Mac용입니다. 다른 환경은 Android Studio SDK Manager를 사용하세요.' >&2
            exit 1
        fi
        sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
        if [[ ! -x "$sdkmanager" ]]; then
            mkdir -p "$project_dir/.tools" "$ANDROID_HOME/cmdline-tools"
            staging_dir="$(mktemp -d "$project_dir/.tools/android-download.XXXXXX")"
            trap 'rm -rf "$staging_dir"' EXIT
            curl --fail --location --retry 2 --connect-timeout 15 \
                https://dl.google.com/android/repository/commandlinetools-mac-13114758_latest.zip \
                -o "$staging_dir/tools.zip"
            echo "5673201e6f3869f418eeed3b5cb6c4be7401502bd0aae1b12a29d164d647a54e  $staging_dir/tools.zip" | shasum -a 256 -c -
            unzip -q "$staging_dir/tools.zip" -d "$staging_dir"
            mv "$staging_dir/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
        fi
        # Show licenses interactively; the developer chooses whether to accept.
        "$sdkmanager" --sdk_root="$ANDROID_HOME" --licenses
        "$sdkmanager" --sdk_root="$ANDROID_HOME" 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
        if [[ ! -e local.properties ]]; then
            printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
        fi
        echo 'SDK 설치 완료. ./scripts/android.sh assembleDebug 실행으로 APK를 빌드하세요.'
        ;;
    *)
        if [[ ! -f "$ANDROID_HOME/platforms/android-35/android.jar" ]]; then
            echo 'Android SDK API 35가 필요합니다. ./scripts/android.sh setup 실행 또는 ANDROID_HOME을 지정하세요.' >&2
            exit 1
        fi
        if [[ $# -eq 0 ]]; then set -- assembleDebug; fi
        exec ./gradlew "$@"
        ;;
esac
