# Figma 화면 적용 및 검증

검증일: 2026-11-15. 기준은 [Thrive · 데모 및 디자인](https://www.figma.com/design/V1lYJYRGTstcbi3U6VqEAP/Thrive?node-id=48-152)의 공통 컴포넌트와 01~07 흐름이다.

## 구현

모든 운전 화면의 배경·색상·Pretendard 타이포·패널·버튼·선택 카드·칩·토글·내비게이션·다이얼로그를 같은 밝은 디자인으로 통일했다. 목표/프로필/기록의 카드 배열, 지도와 코스/브리핑/리포트 패널, 주행 중의 큰 안내와 기록 상태를 적용했다. 지도·아이콘·토글은 Figma 원본 SVG와 그 PNG를 포함한다. 기존 배경 두 장은 Figma 원본과 SHA-256이 일치한다.

기존 실제 지도 연동·주행 기록·저장소·권한·음성 동작을 연결했고, 시연과 실제 SDK 기록의 구분을 유지한다. 실제 연습 경로 준비와 참여 제안 알림 설정도 접근할 수 있다. 디자인 변경 위치와 화면별 원본은 [디자인 시스템](design-system.md)에 정리했다.

## 빌드와 정적 검사

```sh
./scripts/android.sh assembleDebug testDebugUnitTest lintDebug --offline
```

통과. 단위 테스트 51개, 실패/오류/건너뜀 0개. Lint 오류 0개, 경고 16개: target API, 의존성 버전, 데이터 추출 규칙, 이전 테마의 미사용/중복 이미지, KTX 제안. 경고를 없애기 위한 SDK·의존성 변경은 하지 않았다.

일반 설치용 APK: `app/build/outputs/apk/debug/app-debug.apk` (`com.thrive.pleos`).

## 에뮬레이터 검사

Pleos API 34, 2560×1440, density 160. 시스템 Driving view·상태바·GNB와 앱 콘텐츠 영역을 구분한다. 검사는 `com.thrive.pleos.verification.figma` 별도 패키지에서 수행한다. 기존 주 앱과 SDK 검증 앱의 데이터를 지우거나 변경하지 않는다.

최종 결과: UI 동작 43개 통과, 스크린샷 27장.

`FigmaUiChecks`는 Android 접근성 동작으로 아래 흐름을 확인하고 스크린샷을 저장한다. Pleos의 다중 창에서 셸 좌표 입력이 다른 창으로 전달될 수 있어 좌표 클릭을 사용하지 않는다. 접근성 트리가 렌더보다 먼저 갱신될 수 있으므로 캡처 전 렌더 대기를 둔다.

- 목표 선택·15분 조건의 코스 없음·30분으로 변경·두 경로 선택·출발 준비.
- 시연 시작·종료·리포트의 느낌 저장·다음 연습 조건으로 연결.
- 장소 검색의 빈 결과·한글 검색·목적지 선택·경로에 반영.
- 지도 연동 상태·함께 운전 제안의 건너뛰기와 수락·출발 준비 연결.
- 프로필 경험/관심 상황 변경·저장 후 홈·실제 DataStore 저장값 확인.
- 주행 기록·리포트 재열기·개별/전체 삭제 다이얼로그와 취소 후 기록 유지.
- 콘텐츠 너비 840dp와 600dp에서 홈·프로필·연습 화면 렌더. 600dp에서는 가로 내비게이션, 좁은 콘텐츠에서는 카드/패널의 세로 배치를 확인한다. 이 검사는 앱 콘텐츠의 실제 측정 너비를 바꾼 것이며 시스템의 앱 분할 전환을 자동화한 것은 아니다.

재실행:

```sh
./scripts/android.sh assembleDebug assembleDebugAndroidTest -PthriveVerification=figma --offline
.tools/android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
.tools/android-sdk/platform-tools/adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
.tools/android-sdk/platform-tools/adb shell am instrument --user 10 -w -e scenario figma_ui com.thrive.pleos.verification.figma.test/com.thrive.pleos.driving.RecordingLifecycleChecks
```

`--user 10`은 이 Pleos 에뮬레이터의 운전자 사용자다. 다른 기기는 현재 사용자를 확인한다. 검증 APK 빌드 후 일반 APK가 필요하면 `./scripts/android.sh assembleDebug --offline`을 실행한다.

스크린샷은 검증 앱의 `files/figma-ui`에 저장하며, 이번 검토용 복사본은 `/private/tmp/thrive-figma-render/`에 보관했다. 카메라·시스템 바·다른 내비 앱이 보이는 부분은 Thrive 구현 범위에 포함하지 않는다.

## 검증 한계

실차에서의 SDK·위치·음성 품질과 주행 중 안전성은 이번 디자인 변경의 검사 범위에 포함하지 않는다. 로딩·저장 오류·위치 권한 거부·중단 기록 복구·음성 오류·기록 없음은 기존 실제 상태에 연결한 공통 UI이며, 이번 UI 시나리오에서 모든 오류를 강제로 발생시키지는 않았다. 기존 기능별 검증 문서는 그대로 유지한다.
