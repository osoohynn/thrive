# Thrive · Pleos Connect

현대자동차그룹 차량용 앱 개발 공모전을 위한 Android Automotive 프로젝트.
폴더 이름은 `pleos`, 앱 이름은 임시로 `Thrive`입니다. 핵심 서비스 기능은 아직 미정입니다.

## 구성

| 항목 | 설정 |
| --- | --- |
| 언어 / 화면 | Kotlin 2.1.20 / Jetpack Compose · Material 3 |
| JDK / Gradle / AGP | 17 / 8.11.1 / 8.9.2 |
| Android | min 28 / target 34 / compile 35 |
| Pleos Vehicle SDK | 2.3.2 |
| 앱 패키지 | `com.thrive.pleos` (출품 전 최종 이름 확인) |

가로 화면과 멀티 윈도우용 Compose Preview가 있는 휴식 대시보드 목업을 제공합니다.
Vehicle SDK는 Maven 저장소와 의존성까지 구성했으며, 차량 연결·API 호출·권한은 구현하지 않았습니다.
필요한 기능이 결정되면 해당 API 권한과 생명주기 처리를 추가합니다.

## GitHub에 올리기

```sh
./publish-to-github.command
```

필요하면 브라우저에서 GitHub에 로그인하고, 로그인한 계정 아래 `pleos` 비공개 저장소를
생성한 뒤 `main` 브랜치의 커밋을 올립니다. 기존 동명 저장소가 있으면 생성 단계에서 중단합니다.
SDK·캐시·로컬 설정·서명 키·환경 파일·빌드 결과물은 제외합니다.
이후 수정 사항은 Git으로 커밋한 다음 이 파일을 다시 실행하면 됩니다.

## 임시 목업

`mockup/index.html`을 브라우저에서 열면 Android 설치 없이 화면을 볼 수 있습니다.

```sh
open mockup/index.html
```

Android 앱 첫 화면에도 같은 디자인을 구현했습니다. 짙은 녹색 바탕과 연두색 휴식 카드,
예시 차량 상태, 세 가지 휴식 선택으로 구성했습니다. 좁은 화면에서는 세로로 배치합니다.
휴식 선택과 시작·종료 상태를 눌러볼 수 있습니다. 타이머·호흡 가이드·차량 연동은 아직 없습니다.
화면의 배터리·주행 가능 거리·온도·정차 상태는 고정 예시입니다.
HTML은 디자인 검토용 미리보기이며 Android 앱에 WebView로 삽입하지 않습니다.

## 이 Mac에서 빌드 준비

전체 설치와 실행은 아래 파일 하나로 진행할 수 있습니다. 일반 터미널에서 실행하거나
Finder에서 `install-and-run.command`를 더블클릭하세요.

```sh
cd /Users/suhyun/IdeaProjects/pleos
./install-and-run.command
```

Homebrew(없으면 설치) → JDK 17 → Android Studio → Android SDK → 공식 Pleos API 34 ARM 이미지
→ 가상 차량 생성 → 앱 빌드 → 에뮬레이터 부팅 → APK 설치·실행을 순서대로 진행합니다.
Homebrew 설치에 필요하면 Mac 로그인 암호를 묻고, SDK 라이선스는 직접 읽고 동의 여부를 선택합니다.
SDK·AVD·Android 설정·Gradle 캐시는 `.tools` 안에 저장하며 기존 가상 차량 데이터는 지우지 않습니다.
다시 실행하면 설치된 도구와 이 스크립트로 생성한 가상 차량을 재사용합니다.
공식 이미지 저장소에 연결할 수 없거나 해당 이미지가 없으면 오류를 표시하고 중단합니다.
이 세션에서는 다운로드와 가상화가 차단되어 전체 실제 설치·부팅은 검증하지 못했습니다.

아래는 개별 도구를 설치하거나 Android Studio에서 프로젝트를 열 때의 설명입니다.

현재 확인한 환경: Apple Silicon, Homebrew·JDK 17·프로젝트 내 Android SDK 설치됨.
IntelliJ 내장 JDK는 25여서 이 프로젝트의 Gradle 실행에 사용하지 않습니다.

네트워크가 가능한 일반 터미널에서:

```sh
cd /Users/suhyun/IdeaProjects/pleos
brew install openjdk@17
./scripts/android.sh setup
./scripts/android.sh assembleDebug lintDebug
```

`setup`은 프로젝트의 `.tools/android-sdk`에 Android 도구를 설치하고 라이선스를 표시합니다.
Command-line tools는 JDK 17용 13114758 버전으로 고정하고 ZIP의 SHA-256을 검증합니다.
SDK 라이선스는 직접 확인하고 동의 여부를 선택하세요. Gradle 캐시도 `.tools/gradle`에 보관합니다.
이미 설치한 SDK를 쓰려면 `ANDROID_HOME`, JDK 17을 따로 설치했다면 `JAVA_HOME`을 지정하세요.

APK 출력: `app/build/outputs/apk/debug/app-debug.apk`

`setup`은 빌드 도구만 설치합니다. 앱을 띄우는 가상 차량(Pleos 에뮬레이터)은 아래
Android Studio 절차로 별도 생성해야 합니다. 실물 차량이나 스마트폰은 필요하지 않습니다.
에뮬레이터를 처음 생성·부팅한 다음 아래 명령으로 최신 소스를 빌드하고, APK를 설치한 뒤 앱을 실행합니다.

```sh
./scripts/android.sh run
```

연결된 기기가 여러 대라면 `ANDROID_SERIAL=emulator-5554 ./scripts/android.sh run`처럼 지정하세요.

## Android Studio에서 열기와 실행

1. [Android Studio](https://developer.android.com/studio)를 설치하고 이 폴더를 엽니다.
2. Gradle JDK를 JDK 17로 지정합니다. Homebrew 설치 경로는 `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`입니다.
3. `setup`을 실행했다면 SDK 위치는 이 프로젝트의 `.tools/android-sdk`로 설정합니다. Studio에서 SDK를 설치한다면 API 35와 Build Tools 35.0.0을 설치하세요.
4. SDK Manager → SDK Update Sites에서 아래 저장소를 추가합니다.

   ```text
   Name: Pleos Connect System Image
   URL: https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml
   ```

5. SDK Platforms에서 API 34의 Pleos Connect system image를 설치합니다.
6. Device Manager → Create device → Automotive → Pleos Connect v2.1.0을 선택합니다. RAM 4 GB, Cold boot, API 34 이미지로 생성합니다.
7. 에뮬레이터를 실행하고 `app`을 Run 합니다. CLI 설치는 `adb install -r app/build/outputs/apk/debug/app-debug.apk`입니다.

compileSdk 35는 컴파일용이고 Pleos 실행 대상은 API 34입니다.
Automotive 기기를 요구하므로 차량용 에뮬레이터에서 실행하세요.

## Pleos 계정과 연동

[Pleos Playground](https://developers.pleos.ai)에서 계정과 Workspace 프로젝트를 생성해야 합니다.
공식 문서 기준 Vehicle State와 NaviHelper는 신청 없이 사용 가능하며,
FusedLocation·TTS·STT·Vehicle Control은 제공 상태를 확인한 뒤 개발 범위에 포함하세요.
아직 프로젝트 ID나 인증 정보를 넣지 않았습니다. 비밀 키와 서명 키는 Git에 올리지 않습니다.

## 공모전 준비

- 접수 마감: 2026-11-15. 제출 전에 [공식 공고](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000001)에서 변경 여부 확인.
- 필수: 접수양식(기획서).
- 선택 가산점: 시연 영상, 소스 코드와 APK가 함께 있는 GitHub 저장소.
- 기획 결정 사항: 핵심 사용자 / 차량 안에서 해결할 문제 / 핵심 기능 하나 / 필요한 차량 데이터.

## 확인 범위

프로젝트 파일·XML·셸 구문을 로컬에서 점검했습니다.
목업의 Kotlin 소스는 캐시된 Kotlin 2.1.20·Compose 컴파일러로 직접 컴파일했습니다.
이 검사는 문자열 XML에서 만든 임시 R 심볼을 사용하며 APK 패키징 검사를 포함하지 않습니다.
HTML은 jsdom으로 세 가지 선택·시작·종료·선택 시 상태 초기화를 확인했습니다.
현재 세션에서는 Gradle 데몬 소켓과 Chromium 프로세스 시작이 차단되어,
변경 후 APK 빌드·Lint·에뮬레이터 실행·브라우저 렌더링은 검증하지 못했습니다.

Gradle Wrapper 실행 파일과 JAR는 이 작업 공간의 기존 프로젝트에서 복사했습니다.
다운로드할 Gradle 배포판은 위의 8.11.1로 고정하고 공식 배포판 SHA-256을 설정했습니다.

## 공식 문서

- [Pleos 빠른 시작](https://document.pleos.ai/docs/pleos-only/getting-started)
- [Vehicle SDK 의존성](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup) — 빠른 시작의 2.3.1보다 SDK 전용 문서의 2.3.2를 우선 적용.
- [에뮬레이터 구성](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [프로젝트 생성·SDK 제공 상태](https://document.pleos.ai/docs/pleos-only/create-my-project)
- [AGP 8.9 호환성](https://developer.android.com/build/releases/agp-8-9-0-release-notes)
- [Gradle 배포판 체크섬](https://gradle.org/release-checksums/)
- [Command-line tools ZIP 체크섬 대조 출처](https://github.com/JetBrains/kotlin/blob/master/gradle/verification-metadata.xml)
