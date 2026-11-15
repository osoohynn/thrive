# 내비 목적지 기반 참여 제안 검증

검증일: 2026-11-15. Pleos API 34 에뮬레이터의 실제 내비 서비스·Android 차량 속성·차량 알림함을 사용했다. 실차 검증과 구분한다.

## 구현한 흐름

1. 홈의 ‘내비에서 연습 이어가기’에서 참여 제안을 켠다. 기본값은 꺼짐이며 설정은 재실행 후에도 유지한다.
2. 앱의 내비 연결이 유지되는 동안, 사용자가 다른 앱으로 전환한 상태에서 유효한 실제 안내 경로와 최신 주차(P) 응답을 확인한다.
3. 기록 중인 세션이 없고 차량 정보·알림 권한이 있으면 무음·무진동 참여 제안을 보낸다. 시뮬레이션 안내는 대상에서 제외한다.
4. ‘연습 준비’를 누르면 앱에서 목적지와 주차 상태를 새로 조회한다. 목적지가 바뀌었거나 주차 상태를 확인할 수 없으면 준비 진입을 막는다. 이후 별도의 저장 동의를 해야 기록이 시작된다.
5. ‘나중에’ 또는 알림 지우기는 해당 제안을 닫는다. 같은 목적지는 24시간, 다른 목적지도 최소 1분 간격으로 억제한다. 이력은 목적지 식별자의 해시와 시각으로 기기에 저장하며 최근 32개로 제한한다.
6. 제안은 최대 2분 뒤 만료된다. 목적지 변경·설정 해제·권한 부족·기록 시작·주차 확인 실패 시에도 제안을 취소한다. 이미 소비한 토큰은 재사용하지 않는다.

기록 종료 알림과 참여 제안은 다른 알림이다. 기존 주행 기록·종료·복구 흐름을 유지한다. Room 스키마는 v3으로 동일하다.

## 차량 정보와 연결 수명

기존 Pleos Vehicle 2.3.2의 Gear API는 Android GEAR_SELECTION 사용을 권장하며 폐기 예정으로 표시된다. 최종 구현은 Android CarPropertyManager의 GEAR_SELECTION을 읽기 전용으로 조회·구독한다. CAR_POWERTRAIN 권한과 android.car 플랫폼 라이브러리를 사용한다. 차량 상태를 설정하는 API는 호출하지 않는다.

AVAILABLE 상태의 정수 값에서 GEAR_PARK만 인정한다. 조회 수신 시각 기준 5초 이내 응답만 제안·수락 판단에 사용하며, 알 수 없는 값·미지원·오류·연결 끊김을 주차로 해석하지 않는다. 동기 조회는 IO 스레드에서 실행하고, 연결·요청 세대와 타임아웃으로 늦은 응답을 폐기한다. 구독·연결 방식은 API 28–34와 호환되는 진입점을 사용한다.

내비와 참여 제안 처리는 화면 ViewModel 대신 앱 프로세스 수명에 속한다. 앱을 계속 살려 두는 서비스·WakeLock·부팅 수신기는 추가하지 않았다. **앱 미실행·프로세스 종료 후 감지와 다른 지도 앱 전체 연동은 지원하지 않는다.** 시스템이 앱 프로세스를 정리하면 연결도 종료되며 다시 앱을 실행해야 한다. 프로세스 종료 뒤 남은 옛 알림을 열면 만료 안내를 제공한다.

## 실행 확인

| 항목 | 결과 |
|---|---|
| 실제 표준 기어 응답 | GEAR_PARK 수신 확인 |
| 차량 분할 화면 | 홈 버튼만으로는 앱이 계속 RESUMED일 수 있음을 확인. 실제 다른 앱 전환으로 STOPPED 상태에서 검사 |
| 참여 제안 표시 | 실제 주차 응답과 기본 내비 경로로 차량 알림함에 ‘이번 길을 연습해 볼까요?’ 표시 |
| 수락 | 실제 알림의 ‘연습 준비’ → 최신 목적지·주차 재조회 → 현재 내비 목적지 준비 화면 진입 |
| 자동 기록 방지 | 알림 표시·수락 모두 활성 세션이 생성되지 않음 |
| 목적지 변경 이벤트 | 임시 좌표 경로를 실제 SDK로 계산한 뒤 새 목적지의 제안 수신 |
| 나중에 | 실제 알림 PendingIntent로 수신기를 실행해 알림 제거 |
| 중복 억제 | 같은 목적지 재조회 뒤 다시 제안하지 않음, 억제 이력 저장 확인 |
| 테스트 정리 | 임시 내비 목적지를 원래 목적지로 복원 |

참여 제안을 활성화한 상태에서 기존 화면 종료·서비스 중단·오래된 종료 요청·같은 기록 복구·종료까지 회귀 검사를 통과했다. 이전 기록 8개의 모든 필드가 보존됐으며, 제안 확인·거절 자체로 기록이 생성되지 않았다.

단위 테스트 51개가 통과했다. 새 6개 검사는 주차·경로 응답의 신선도, 실제 안내/시뮬레이션 구분, 사용자 설정·권한·화면 노출·활성 기록 조건, POI 입구 좌표 변경에 대한 식별자 안정성, 24시간·1분 억제 경계, 시계 역행·잘못된 이력, 제안 만료를 다룬다.

## 기기 검사

별도 검증 앱에서 참여 제안을 켜고 기본 내비에 안내 중인 목적지를 설정해야 한다. 진행 중인 기록이 없는 상태에서 실행한다. 다음 검사는 실제 내비 목적지를 잠시 변경한 뒤 복원한다. 운영 패키지에서는 실행하지 않는다.

```sh
./scripts/android.sh -PthriveVerification assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument --user 10 -w -e scenario invitation_decline com.thrive.pleos.verification.test/com.thrive.pleos.driving.RecordingLifecycleChecks
```

사용자 ID는 환경에 맞게 확인한다. 성공 시 PASS: real navigation destination change 문구가 출력된다. 수락 화면과 알림 표시의 실제 렌더는 차량 알림함에서 별도 확인했다.

## 제약과 후속 검증

- 조용한 알림함 제안이며 헤드업 팝업을 보장하지 않는다. 제조사의 표시 정책을 우회하거나 내비·메시지 카테고리로 위장하지 않는다.
- 억제 이력을 게시 전에 저장한다. 그 사이 상태가 바뀌거나 게시가 실패하면 해당 제안도 억제될 수 있다. 중복 방지를 우선하며 전송·열람 보장은 제공하지 않는다.
- 24시간 억제는 경로 전체의 동일성이 아닌 목적지 식별자 기준이다. 같은 목적지의 다른 경로도 억제된다.
- 프로세스 종료·실차·장시간 절전·제조사별 차량 정보 지원, 실제 주행 상태 전환과 재연결은 추가 검증이 필요하다.
- 자동 음성 코칭·ADAS 분석·도로 적합성 검토는 이 기능의 완료 범위가 아니다.

## 근거

- [NaviHelper 이벤트](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners)
- [Vehicle SDK 소개](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/intro) 및 실제 Vehicle 2.3.2 AAR의 폐기 예정 표시
- [Android GEAR_SELECTION](https://developer.android.com/reference/android/car/VehiclePropertyIds#GEAR_SELECTION)
- [CarPropertyManager](https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager)
- [차량 알림](https://developer.android.com/training/cars/platforms/automotive-os/notifications)
