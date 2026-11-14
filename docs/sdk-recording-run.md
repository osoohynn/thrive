# SDK 기록 전체 흐름 실행 검증

확인일: 2026-11-14. Pleos API 34 에뮬레이터, 사용자 10, 별도 앱 `com.thrive.pleos.verification`.

## 발견한 문제와 수정

FusedLocation 권한이 매니페스트에만 선언되어 있어, 실제 구독 시 SecurityException이 발생했다. 위험 권한의 실행 중 요청을 추가했다.

- 실제 목적지 동의 후 기록 시작, 중단된 SDK 기록 복구, 위치 재연결 전에 권한을 확인한다.
- 거부 시 새 기록이나 복구를 진행하지 않고 기존 세션을 보존한다. 위치 권한 안내와 앱 권한 설정 진입을 제공한다.
- ViewModel에서도 권한을 재확인해 UI 외 호출이 미허용 상태로 기록을 시작하지 못하게 한다.
- SDK 어댑터에서 미허용 상태를 구분하고, 실제 등록한 리스너만 해제한다. SecurityException을 일반 연결 실패와 구분해 안내한다.
- 권한 요청 중 Activity가 재생성되면 이전 기록 시작을 자동 실행하지 않는다. 사용자가 다시 확인해야 한다. 목적지의 기존 30초 유효기간 검사도 유지한다.

## 실행 결과

1. 기본 내비에서 공개 장소를 검색하고 경로 안내를 시작했다.
2. NaviHelper에서 실제 목적지와 ROUTE_GUIDING을 수신했다. 목적지 저장 동의 후 SDK 세션을 생성했다.
3. 위치 권한 거부 시 같은 세션이 Interrupted 상태로 보존되는 것을 DB에서 확인했다. 재요청·허용 후 같은 ID를 복구했다.
4. 실제 FusedLocation 콜백을 받아 정지 상태의 위치·거리 0을 저장했다.
5. Android Emulator의 `geo fix`로 위도 방향의 작은 이동을 5회 입력하고 원래 좌표로 복원했다. 이 입력이 SDK 콜백을 거쳐 반영됐다.
6. 첫 SDK 세션에 위치 97건, 유효 좌표 6개, 추정 거리 55.597m, 복구 공백 2회가 저장됐다. 구간 사이의 공백을 거리로 채우지 않았다.
7. 기록 종료·리포트 표시, 부담 응답 저장, 기본 내비 목적지 재확인, 두 번째 SDK 세션 시작·종료를 확인했다.
8. 두 세션 모두 SDK/EMULATOR/Completed이며 활성 슬롯이 비어 있는 것을 확인했다. 이전 기록의 모든 필드는 유지됐다.
9. 프로세스 재실행 후 SDK 리포트 재조회를 확인했다.

빌드·Lint·단위 테스트 36개 통과. 이번 권한 수정은 실제 시스템 권한 창의 거부·허용과 DB 상태로 추가 검증했다.

## 확인 범위

실제 내비·SDK 서비스와 저장 흐름을 검증했지만, 이동 입력은 에뮬레이터 GPS 모의 값이다. 실차 센서 정확도·주행계 비교·장시간 백그라운드 기록은 검증하지 않았다. 원시 좌표를 앱 진단 로그에 추가하지 않았고 권한을 ADB로 강제 부여하지 않았다. SDK 위험 권한의 시스템 창에는 ‘알 수 없는 작업’이라는 제공자 측 문구가 표시됐다. 앱은 별도 위치 저장 동의와 권한 필요 안내를 제공한다.

## 재현 순서

기본 내비에서 경로 안내 시작 → 지도 연동 화면에서 연결 → 실제 목적지 확인 → 동의 → 시스템 위치 권한 허용 → 위치 수신 확인 → 수동 종료 → 리포트·주행 기록 재조회. 권한 거부 상태에서는 기록 생성·복구가 진행되지 않아야 한다. SDK 재연습은 저장한 부담 응답을 선택한 뒤 ‘기본 내비 목적지 확인’으로 진행한다.

## 근거 문서

- [FusedLocation 권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions)
- [위치 콜백](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/location-data-access)
- [내비 목적지](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations)
- [에뮬레이터 GPS 입력](https://developer.android.com/studio/run/emulator-console#geo)
