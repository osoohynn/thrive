# thrive · Pleos Connect

현대자동차그룹 차량용 앱 개발 공모전을 위한 Android Automotive 프로젝트.
프로젝트와 폴더 이름은 `thrive`, 앱 이름은 `Thrive`입니다. 초보·장롱면허 운전자의 연습과 실제 목적지 주행을 연결하는 운전 적응 서비스를 개발합니다.

GitHub 저장소: [osoohynn/thrive](https://github.com/osoohynn/thrive)

## 구성

| 항목 | 설정 |
| --- | --- |
| 언어 / 화면 | Kotlin 2.1.20 / Jetpack Compose · Material 3 |
| JDK / Gradle / AGP | 17 / 8.11.1 / 8.9.2 |
| Android | min 28 / target 34 / compile 35 |
| Pleos Vehicle SDK | 2.3.2 |
| 앱 패키지 | `com.thrive.pleos` (출품 전 최종 이름 확인) |

가로 화면과 멀티 윈도우용 Compose Preview가 있는 운전 적응 앱 시연을 제공합니다.
Vehicle SDK는 Maven 저장소와 의존성까지 구성했으며, 차량 연결·API 호출·권한은 구현하지 않았습니다.
필요한 기능이 결정되면 해당 API 권한과 생명주기 처리를 추가합니다.

## GitHub에 올리기

```sh
./publish-to-github.command
```

필요하면 브라우저에서 GitHub에 로그인하고, 로그인한 계정 아래 `thrive` 비공개 저장소를
생성한 뒤 `main` 브랜치의 커밋을 올립니다. 기존 동명 저장소가 있으면 생성 단계에서 중단합니다.
SDK·캐시·로컬 설정·서명 키·환경 파일·빌드 결과물은 제외합니다.
이후 수정 사항은 Git으로 커밋한 다음 이 파일을 다시 실행하면 됩니다.

## 운전 적응 앱 시연

기본 진입 화면을 운전 적응 홈으로 연결했습니다. 기존 네이비·시안 테마, Pretendard, 공통 패널을 재사용합니다. HTML의 밝은 테마와 차량 시스템 바는 앱 안에 복제하지 않습니다.

- 홈 → 연습 목표·시간 → 코스 선택 → 출발 준비 → 주행 시연 → 리포트 → 다음 연습.
- 목적지 검색 → 코스 선택 → 같은 주행·리포트 흐름.
- 지도 연동 체험 → 앱 내부 참여 제안 → 출발 준비. 실제 외부 앱 감지·시스템 알림은 아닙니다.
- 이전 예시 기록과 앱에서 완료한 시연 기록을 구분하고, 프로필·안내 선택을 화면 재생성 중 유지합니다. 앱 데이터 영구 저장은 아직 없습니다.

구현 위치: `ui/driving/DrivingApp.kt`(상태·화면 전환), `PlanningScreens.kt`(출발 전 화면), `ReviewScreens.kt`(주행·복습·프로필), `DrivingComponents.kt`(공통 컨트롤), `CourseMap.kt`(모의 지도), `DrivingModels.kt`(예시 데이터).

모든 지도·경로·주행 기록은 모의 데이터입니다. 실제 차량·내비게이션·음성 연결과 운전 평가 기능은 구현하지 않았습니다. 기존 휴식 구현과 테마 파일은 보존했습니다.

## 이전 휴식 목업

`mockup/index.html`을 브라우저에서 열면 Android 설치 없이 화면을 볼 수 있습니다.

```sh
open mockup/index.html
```

이전 휴식 화면에도 같은 디자인을 구현했습니다. 현재 앱의 기본 화면은 아래 운전 적응 시연입니다. Figma 3안의 네이비·블루/시안 파형 배경,
반투명 패널과 둥근 흰색 버튼을 바탕으로 예시 차량 상태와 세 가지 휴식 선택을 구성했습니다. 좁은 화면에서는 세로로 배치합니다.
휴식 선택과 시작·종료 상태를 눌러볼 수 있습니다. 타이머·호흡 가이드·차량 연동은 아직 없습니다.
화면의 배터리·주행 가능 거리·온도·정차 상태는 고정 예시입니다.
HTML은 디자인 검토용 미리보기이며 Android 앱에 WebView로 삽입하지 않습니다.

## 디자인 시스템

[Figma 3안 기반 디자인 시스템](docs/design-system.md)에 색상·타이포·간격·라운드·컴포넌트 상태와 원본/확장 토큰을 정리했습니다.
Android는 `ui/theme/ThriveTheme.kt`의 토큰과 Material 3 테마, `ThriveSurfaces.kt`의 공통 표면을 사용합니다.
HTML 미리보기는 `mockup/tokens.css`에 같은 값을 반영합니다. 배경과 Pretendard 글꼴을 앱에 포함해 오프라인에서도 표시합니다.
Figma의 검색·채팅 기능은 가져오지 않았으며 기존 휴식 선택과 시작·종료 동작을 유지합니다.
차량 화면의 주변 UI에 맞춰 본문 28sp·보조 정보 24sp·주요 제목 40~48sp를 기본으로 사용하며, 시스템 글자 배율은 변경하지 않습니다. [가독성 수정 및 검증 기록](docs/readability-review.md)을 함께 제공합니다.

## 이 Mac에서 빌드 준비

전체 설치와 실행은 아래 파일 하나로 진행할 수 있습니다. 일반 터미널에서 실행하거나
Finder에서 `install-and-run.command`를 더블클릭하세요.

```sh
cd /Users/suhyun/IdeaProjects/thrive
./install-and-run.command
```

Homebrew(없으면 설치) → JDK 17 → Android Studio → Android SDK → 공식 Pleos API 34 ARM 이미지
→ 가상 차량 생성 → 앱 빌드 → 에뮬레이터 부팅 → APK 설치·실행을 순서대로 진행합니다.
Homebrew 설치에 필요하면 Mac 로그인 암호를 묻고, SDK 라이선스는 직접 읽고 동의 여부를 선택합니다.
SDK·AVD·Android 설정·Gradle 캐시는 `.tools` 안에 저장하며 기존 가상 차량 데이터는 지우지 않습니다.
다시 실행하면 설치된 도구와 이 스크립트로 생성한 가상 차량을 재사용합니다.
새 가상 차량은 공식 Pleos Connect 기기 프로필의 2560×1440·160dpi로 생성합니다.
공식 이미지 저장소에 연결할 수 없거나 해당 이미지가 없으면 오류를 표시하고 중단합니다.
이 세션에서는 다운로드와 가상화가 차단되어 전체 실제 설치·부팅은 검증하지 못했습니다.

아래는 개별 도구를 설치하거나 Android Studio에서 프로젝트를 열 때의 설명입니다.

현재 확인한 환경: Apple Silicon, Homebrew·JDK 17·프로젝트 내 Android SDK 설치됨.
IntelliJ 내장 JDK는 25여서 이 프로젝트의 Gradle 실행에 사용하지 않습니다.

네트워크가 가능한 일반 터미널에서:

```sh
cd /Users/suhyun/IdeaProjects/thrive
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
[공모전 개발 범위 안내](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000008)에 따라
공개 문서의 Vehicle·NaviHelper·TextToSpeech·SpeechToText·FusedLocation SDK는 Workspace에
‘준비 중’으로 표시되어도 문서에 안내된 방법으로 에뮬레이터에서 개발·시험할 수 있습니다.
Gleo Interface(App Schema)는 공모전 참가팀에 한해 별도 검토·승인 없이 사용할 수 있습니다.
문서에 없는 기능과 Pleos LLM SDK는 공모전에서 제공되지 않으며, 차량 마이크 음성을 외부
음성 인식 서비스로 보내는 방식은 지원하지 않으므로 SpeechToText SDK를 사용합니다.
아직 프로젝트 ID나 인증 정보를 넣지 않았습니다. 비밀 키와 서명 키는 Git에 올리지 않습니다.

## 공모전 준비

- 접수 마감: 2026-11-15. 제출 전에 [공식 공고](https://appdevchallenge.auto-hmg.io/notice/c0a1a000-2026-4923-8000-000000000001)에서 변경 여부 확인.
- 필수: 접수양식(기획서).
- 선택 가산점: 시연 영상, 소스 코드와 APK가 함께 있는 GitHub 저장소.
- 기획 결정 사항: 핵심 사용자 / 차량 안에서 해결할 문제 / 핵심 기능 하나 / 필요한 차량 데이터.

## 확인 범위

2026-11-14 디자인 시스템 적용 후 APK 빌드와 `lintDebug`를 통과했습니다.
Lint 오류는 없고 기존 targetSdk·백업 설정 경고 2개가 남아 있습니다.
Pleos API 34 에뮬레이터에서 메인 화면 렌더와 세 가지 휴식 선택·시작·종료·선택 변경 시 초기화,
단일 선택 접근성 상태를 확인했습니다. 480px 앱 창에서 세로 배치와 창 크기 변경 후 선택·휴식 상태 유지도 확인했습니다.
검증 중 내장 주행 화면의 GPU 오류가 있어 호스트 GPU 모드로 전환했습니다.
공식 기기 프로필에 맞춰 기존 AVD를 2560×1440·160dpi로 변경하고 재시작한 뒤,
ADB의 실제 해상도·밀도와 Thrive의 넓은 화면 렌더링을 확인했습니다.
Chromium에서 HTML 미리보기의 1200·480·320px 배치, 가로 넘침 없음, 로컬 폰트 로드,
세 가지 선택·시작·종료·선택 시 상태 초기화와 키보드 포커스를 확인했습니다.
배경 이미지·폰트·라이선스가 APK 안에 포함된 것도 확인했습니다.

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
