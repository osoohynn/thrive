# SDK 지원 확인

확인일: 2026-11-14. 환경: Pleos API 34 에뮬레이터, 현재 차량 사용자 10. 실행 검증은 기본 앱과 충돌하지 않는 `com.thrive.pleos.verification`에서 수행했다.

## 확인 결과

| 항목 | 상태 | 근거와 범위 |
|---|---|---|
| NaviHelper 3.0.5 의존성·타입 | 확인 | 공식 Maven 아티팩트 해석·컴파일 성공. 실제 AAR의 콜백·데이터 타입과 대조 |
| `NAVI_ROUTE` 권한 선언 | 적용 | 현재 사용하는 읽기 API 권한만 선언 |
| 서비스 초기화 | 실행 확인 | 실제 서비스 연결 후 목적지 조회 API를 호출 |
| 목적지 조회 | 유효 목적지 실행 확인 | 초기 빈 응답 이후 기본 내비에서 목적지 설정·안내 시작 시 유효한 이름·좌표와 ROUTE_GUIDING 수신 확인 |
| 목적지 변경 리스너 | 구현, 동작 미확인 | `onDestinationChanged` 구독과 재조회 연결. 이번 실험에서 변경 이벤트 수신 횟수는 0 |
| 경로 취소·SDK 오류 | 처리 구현 | 상태 표시·조회 정리. 실제 취소·오류 주입 검증은 후속 |
| 연결·조회 타임아웃 | 구현 | 연결 10초·조회 8초 후 실패 또는 미응답 표시. 예시 목적지로 대체하지 않음 |
| 백그라운드·미실행 감지 | 미검증 | 앱 ViewModel 생명주기 내 연결만 구현. 상시 서비스·자동 팝업 없음 |
| 위치 기록 | 에뮬레이터 전체 흐름 확인 | FusedLocation 1.1.3 권한 요청·실제 콜백·97건 저장·약 55.6m 추정·종료 리포트 확인. 이동은 GPS 모의 입력 |
| Vehicle 데이터 기록 | 미구현 | 차량 사건·ADAS 분석 미구현 |
| TTS | SDK 재생 콜백 확인 | TextToSpeech 3.3.1 ON_DEVICE로 시작·완료 확인. 수동 목표 안내와 중단 처리 구현 |
| ADAS·외부 지도 앱 | 미검증 | 실제 기능과 지원 범위 별도 확인 필요 |

SDK 상태 화면의 관찰 결과:

> 목적지 정보는 수신했지만 장소명이 없어요.
>
> 목적지 변경 이벤트 0회 수신

이는 실제 SDK 연결과 조회 콜백을 확인한 것이며, 사용자가 다른 지도 앱에 목적지를 설정하면 자동 제안이 뜬다는 의미는 아니다. 다음 실험은 기본 내비에서 직접 목적지를 설정·변경하고 앱 전면/백그라운드에서 콜백 전달을 확인하는 것이다.

## 구현 위치

- `driving/navigation/NavigationConnection.kt`: 초기화, 읽기 전용 이벤트 등록, 목적지 조회, 세대별 오래된 콜백 차단, 타임아웃, 해제.
- `ui/driving/NavigationStatusPanel.kt`: 실제 SDK 상태 표시. 아래의 모의 지도·초대와 구분.

목적지 이름·좌표를 진단 로그에 기록하지 않는다. 해제 실패 로그는 예외 클래스만 남긴다.

## 공식 자료

- [SDK 의존성](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk)
- [권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)
- [초기화·해제](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release)
- [목적지 조회](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations)
- [이벤트](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners)

## 2차 확인

경로 상태 `SAFE_GUIDING`과 사용할 수 없는 목적지를 실제로 수신했다. 유효한 목적지와 `ROUTE_GUIDING` 또는 `SIMULATION_GUIDING` 상태에서만 동의 후 SDK 기록을 시작하도록 구현했다. 전체 위치 기록 동작과 남은 검증은 [위치 기록 검증](live-recording-verification.md)에 구분했다.

## 음성 안내 확인

TextToSpeech 3.3.1의 기기 내 모드로 한국어 목표 문장 재생을 요청해 `onStart`·`onDone` 콜백을 확인했다. 수동 재생·음소거·중단·화면 이탈과 요청 중복 제한을 연결했다. 실제 주행 중 자동 코칭·내비 우선순위 보장은 아직 구현하지 않았다. [음성 안내 검증](voice-guidance-verification.md)에 구현·확인 범위를 기록했다.

## 실제 목적지·위치 기록 실행

실제 내비 목적지와 ROUTE_GUIDING 수신, 권한 거부·허용, SDK 위치 저장, 수동 종료·재조회와 SDK 재연습을 확인했다. [SDK 기록 전체 흐름](sdk-recording-run.md)에 실행 결과와 실차 미검증 범위를 기록했다.
