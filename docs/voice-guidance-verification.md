# 음성 목표 안내 구현·검증

확인일: 2026-11-14. Pleos API 34 에뮬레이터, 사용자 10. 별도 검증 앱 사용.

## 구현 범위

- TextToSpeech 3.3.1, `TTS_SERVICE` 권한, `ON_DEVICE` 모드, 한국어 음성 설정을 적용했다. 공식 AAR의 실제 타입을 확인하고 구현했다.
- 음성 SDK의 전이 의존성 `ai.pleos.playground.security:universal-access:0.6.0`을 해석하도록 Pleos 저장소의 허용 그룹을 추가했다. 인증키는 추가하지 않았다.
- 출발 준비와 DEMO 주행에서 사용자가 누른 경우에만 목표별 짧은 문장을 재생한다. 시연 중에는 시연 안내임을 음성에 포함한다. 회전 위치·거리·차량 사건을 관측한 안내가 아니다.
- 초기화·준비·재생·완료·중단·오류 상태를 구분한다. 완료 API 호출을 실제 재생 완료로 표시하지 않으며 `onDone`을 기다린다.
- 한 번에 요청 하나만 허용한다. 같은 요청은 10초 간격으로 제한하고, 5초를 넘겨 초기화된 요청은 폐기한다. 초기화·재생 시작·완료 응답에는 각각 타임아웃이 있다.
- SDK 콜백에 발화 ID가 없으므로 요청별 인스턴스·리스너와 토큰으로 이전 요청의 콜백을 분리한다. 완료·중단 시 구독 제거·stop·release를 요청한다.
- 음소거는 설정 저장이 끝나기 전부터 즉시 적용한다. 화면·목표 변경, 앱 백그라운드 전환, 기록 시작·종료, ViewModel 해제 시 재생을 중단한다.
- 실제 SDK 주행 기록 중에는 이 기능을 제공하지 않는다. 실제 내비·통화·경고음과의 우선순위, 실제 기기의 출력 음량과 음질, 위치·차량 사건 기반 자동 안내는 별도 검증이 필요하다. 오디오 속성은 학습 음성을 나타내는 ASSISTANT/SPEECH로 전달했으며, 우선순위 보장을 의미하지 않는다.

## 검증

- 빌드·Lint 성공, 단위 테스트 29개 통과. 추가 5개는 음소거, 요청 중복, 취소 후 오래된 콜백, 반복 간격 경계, 오래된 초기화를 검증한다.
- 실제 SDK에서 `Connecting → Preparing → Speaking → Done`을 확인했다. 성공 상태는 `onStart`·`onDone` 콜백에 근거한다. 출력 소리의 음질·음량을 청취 평가한 것은 아니다.
- UI에서 음소거 시 재생 버튼 숨김, 재생 중단 버튼 동작, 10초 이내 반복 억제, 뒤로가기로 준비 화면 이탈 시 중단을 확인했다.
- 재생 요청 직후 음소거 시 `Stopped` 전환을 확인했다. 다른 앱으로 전환해 검증 앱이 실제 백그라운드로 이동할 때도 `Stopped`를 확인했다. Pleos 홈 키는 해당 창을 계속 RESUMED 상태로 유지하므로 백그라운드 검증 수단으로 사용하지 않았다.
- 실제 화면 렌더를 확인했다. 테스트에서 기존 주행 기록을 삭제하거나 DB 스키마를 변경하지 않았다.

## 공식 자료

- [소개·모드](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro)
- [의존성](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/importing-sdk)
- [권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/declaring-permissions)
- [초기화·해제](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/TextToSpeech/initialization-and-release)
- [이벤트](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/event-listeners)
- [시작·중단](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/start-and-stop)
