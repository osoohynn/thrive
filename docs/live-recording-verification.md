# 목적지·위치 기록 검증

확인일: 2026-11-14. Pleos API 34 에뮬레이터, 차량 사용자 10.

## 구현

- NaviHelper에서 조회한 유효한 이름·좌표와 경로 상태가 있어야 기록 시작을 제안한다. 조회 후 30초가 지나거나 목적지가 바뀌면 재조회가 필요하다.
- 사용자가 목적지와 로컬 위치 저장에 동의하면 SDK 출처 세션을 시작한다. DEMO·SDK 및 에뮬레이터·기기 환경을 구분한다.
- FusedLocation 1.1.3의 지원 확인·구독·해제를 연결했다. 최신 콜백을 최대 초당 1개 저장하며 원본 시각, 수신 시각, 좌표, 원본 confidence, 품질 판정을 보존한다. 모든 SDK 콜백을 저장하는 방식은 아니다.
- 위치 기반 추정 거리, 유효·제외 샘플 수, 누락 구간 수를 저장하고 리포트에 표시한다. 미측정과 실제 0을 구분한다. 운전 점수·회전·급제동은 만들어내지 않는다.
- 중단 후 같은 세션을 복구하되 중단 전후 좌표를 이어 거리를 계산하지 않는다. 기록 삭제는 해당 위치 샘플도 삭제한다.

## 확인한 결과

- `assembleDebug`, `lintDebug`, `testDebugUnitTest` 성공. 단위 테스트 17개 통과. Lint 오류 0개, 기존 targetSdk·백업 경고 2개.
- 별도 검증 앱에서 Room v1 → v2 실제 마이그레이션 성공. 기존 기록 1개의 모든 원래 필드 유지 확인.
- 생성된 실제 Room v2 스키마로 SQLite 외래키 삭제 연쇄 동작 확인.
- 실제 NaviHelper 콜백에서 `SAFE_GUIDING`과 사용할 수 없는 목적지를 수신했다. 이 상태에서는 SDK 기록 시작 버튼이 나타나지 않았다.
- 좌표 범위, 타임스탬프 누락·역전·중복, 수신 지연, 점프, 정지, 재개 시 거리 단절과 목적지 유효성·안내 모드·조회 유효기간을 단위 테스트했다.

## 계산 기준과 한계

수신 후 5초를 초과한 위치는 오래된 값으로 처리한다. 원본 또는 수신 간격이 15초를 초과하면 새 구간으로 시작한다. 초당 60m를 초과하는 점프를 제외하고 3m 미만 변화를 누적하지 않는다. 이는 시연 단계의 임시 추정 정책이며 차량 주행계나 검증된 안전 판단 기준이 아니다. confidence는 척도가 확인되지 않아 원본만 저장한다.

후속 실행에서 실제 목적지·위치 콜백·저장·종료·재조회와 SDK 재연습을 확인했다. 런타임 권한 누락을 수정했고, 에뮬레이터 이동 입력으로 97건·약 55.6m 저장을 검증했다. 세부 결과는 [전체 흐름 실행 검증](sdk-recording-run.md)을 참고한다. 실차 거리 비교와 모든 오류·재연결 조건의 검증은 남아 있다. 현재 기록은 ViewModel·프로세스 생명주기에 종속되며 프로세스 종료 시 수신이 멈출 수 있다. Foreground Service와 상시 백그라운드 기록, 자동 도착 종료, ADAS·차량 사건 분석은 구현하지 않았다.

## 공식 자료

- [FusedLocation 소개](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro)
- [의존성·권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/project-setup)
- [위치 데이터](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/location-data-access)
- [초기화·해제](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/initialization-and-release)
