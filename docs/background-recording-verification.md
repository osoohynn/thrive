# 백그라운드 주행 기록 검증

검증일: 2026-11-15. Pleos 에뮬레이터의 실제 FusedLocation 서비스와 별도 검증 패키지에서 실행했다. 실차·장시간 절전·제조사별 서비스 유지 검증과 구분한다.

## 변경

- `DriveRecorder`가 앱 프로세스 수명 동안 세션·단조 시계·위치 구독·저장을 소유한다. 화면과 ViewModel은 상태를 관찰하고 시작·복구·종료 의도를 전달한다.
- SDK 세션은 화면에서 사용자가 시작한 후 `location` 유형의 포그라운드 서비스 승인을 확인하고 기록한다. 화면 종료, 다른 앱 전환, 새 ViewModel 생성은 기록을 중단시키지 않는다.
- 프로세스가 새로 시작될 때만 이전 미완료 기록을 `Interrupted`로 바꾼다. 사용자가 같은 기록을 복구하면 중단 시간을 제외하고 위치 구간을 분리한다.
- 위치 권한은 Pleos SDK 권한과 Android의 정확한 위치 권한을 함께 확인한다. 알림 권한은 선택 사항이다. 알림을 거부해도 사용자가 시작한 기록 서비스는 실행할 수 있다.
- 차량 알림함에 소리·진동 없는 기본 중요도의 기록 알림과 종료 버튼을 제공한다. 권한을 나중에 허용하면 이미 실행 중인 기록의 알림을 즉시 갱신한다.
- 종료 요청은 세션 ID를 확인하고 DB 완료를 저장한 뒤 타이머·위치·서비스를 해제한다. 오래된 알림의 요청으로 다른 세션을 종료하지 않는다.
- 서비스가 예기치 않게 종료되면 체크포인트와 중단 상태를 저장하고 직접 복구하도록 한다. UI도 중단 상태에 맞는 이어가기·종료 동작을 표시한다.
- 새 DB 컬럼이나 의존성을 추가하지 않았다. Room v3 스키마는 유지한다.

## 실행 결과

| 시나리오 | 결과 |
|---|---|
| 정확한 위치 권한 거부 | 기록을 생성하지 않고 권한 안내 표시 |
| 권한 허용 후 SDK 기록 시작 | 실제 `isForeground=true` 위치 서비스 실행 |
| 알림 권한 없이 홈·다른 앱 사용 | 같은 세션의 시간·SDK 위치 샘플 계속 증가 |
| 화면을 직접 종료 | Activity 종료 후에도 동일 세션에서 시간·위치 저장 유지 |
| 화면 재생성·이어가기 | 동일 ID 유지, 새 기록·추가 복구 공백 없음 |
| 다른 세션 ID로 종료 요청 | 현재 기록을 종료하지 않음 |
| 서비스 강제 해제 | 동일 기록이 Interrupted로 저장됨 |
| 서비스 해제 후 복구 | 동일 ID로 Recording 복구, 공백 1회 추가 |
| 테스트 프로세스 종료 후 앱 재실행 | 중단 기록 표시·같은 기록 수동 복구 |
| 실제 차량 알림에서 기록 종료 | Completed·activeSlot 해제·종료 시각 저장, 서비스 제거 |
| 종료 후 대기 | 시간·위치 저장이 추가 증가하지 않음 |
| 기록 도중 알림 권한 허용 | 알림이 즉시 나타나며 종료 버튼 정상 동작 |
| 기존 기록 | 이전 기록 6개의 모든 필드 보존 |

최종 화면 생명주기 통합 검사에서, 화면 종료 전후 저장 시간은 1,300,817ms → 1,305,905ms, 위치 반영 건수는 1,254 → 1,261건으로 증가했다. 체크포인트는 5초 주기이므로 DB 시간은 매초 표시 시간과 갱신 시점이 다르다. 해당 세션의 종료 기록은 1,367,111ms·위치 1,316건·복구 공백 5회이며 정지한 에뮬레이터 좌표이므로 추정 거리는 0m다. 실제 운전 실력을 평가한 수치가 아니다.

빌드·Lint·단위 테스트 45개와 기기 통합 검사를 통과했다. 단위 테스트 개수에 기기 시나리오를 더해 표시하지 않는다.

## 기기 통합 검사 재실행

기본 패키지 데이터를 변경하지 않도록 `RecordingLifecycleChecks`는 `.verification` 패키지에서만 실행된다. 먼저 검증 앱에서 실제 SDK 기록을 하나 시작하고 위치 권한을 허용해야 한다. 테스트 실행은 검증 프로세스를 재시작할 수 있으므로 실행 중 세션이 중단·복구될 수 있다. 메인 패키지에 실행하지 않는다.

```sh
./scripts/android.sh -PthriveVerification assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument --user 10 -w com.thrive.pleos.verification.test/com.thrive.pleos.driving.RecordingLifecycleChecks
```

사용자 ID는 실행 환경에 맞게 확인한다. 성공 시 `PASS: Activity destroyed`와 시간·위치 변화가 출력된다. 검사 종료 시 Android가 테스트 프로세스를 끝낼 수 있으므로 이후 앱에서 중단 기록을 직접 복구하거나 종료한다. 실제 알림 버튼은 차량 알림함에서 별도로 확인한다.

## 확인 과정에서 수정한 문제

- 내비를 여는 것만으로는 차량 분할 화면의 Activity가 종료되지 않았다. 시스템 Activity 이력의 존재 여부를 종료 판정에 사용하지 않고, 테스트 코드에서 실제 Activity를 종료하고 생명주기 콜백을 확인했다.
- 알림 권한이 없는 상태에서 생성한 알림은 권한을 나중에 허용해도 자동으로 재게시되지 않았다. 권한 허용 직후와 앱 복귀 시 실행 중 알림을 갱신한다.
- 차량에서 낮은 중요도의 포그라운드 서비스 알림이 필터링될 수 있어 기본 중요도와 무음·무진동을 사용한다. 내비나 메시지 카테고리로 위장하지 않는다.

## 남은 범위

이 서비스는 `START_NOT_STICKY`로 동작한다. 시스템 강제 종료·앱 강제 중지·재부팅 뒤에 자동으로 기록을 재개하지 않는다. 저장한 체크포인트를 사용자가 직접 이어가며, 최종 체크포인트 이후의 시간은 최대 약 5초와 저장 실패 범위만큼 누락될 수 있다. 장시간 절전과 제조사별 정책은 실차에서 추가 확인해야 한다.

목적지 설정 시 참여 제안 알림, 자동 음성 코칭, 차량·ADAS 사건 분석은 이번 배치의 완료 범위가 아니다.

## 공식 근거

- [Android 위치 포그라운드 서비스 유형과 권한](https://developer.android.com/develop/background-work/services/fgs/service-types#location)
- [포그라운드 서비스 시작·승격](https://developer.android.com/develop/background-work/services/fgs/launch)
- [알림 런타임 권한](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [차량 알림 동작](https://developers.google.com/cars/design/automotive-os/product-experience/system-ui/notifications/how-car-notifications-work)
