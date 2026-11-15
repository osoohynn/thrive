# Thrive 디자인 시스템

2026-11-15 최신 기준: Figma 원본의 물결·점무늬 배경과 네이비·민트 팔레트를 유지한다. 앞서 적용한 기능 중심 제목과 간결한 구성은 유지하고, Apple의 표면 위계·가독성·즉각적인 반응 원칙을 참고해 공통 패널과 컨트롤을 다듬었다. 원본 디자인을 기반으로 한 조정이며, 당근·토스의 중립 회색/파랑 테마로 대체하지 않는다.

현재 조정과 검증 기록은 [Figma 기반 다듬기](design-refinement.md), 당근·토스의 이전 참고 내역은 [공식 가이드 검토](design-reference-review.md)에 있다. 새로운 UI 라이브러리나 효과용 의존성은 추가하지 않았다.

## 수정 위치

디자인 변경은 아래 공통 계층부터 수정한다. 별도의 UI 프레임워크나 런타임 의존성은 추가하지 않았다.

- `ui/theme/ThriveTheme.kt`: 색상, 간격, 형태, 화면 폭과 Pretendard 타이포그래피.
- `ui/theme/ThriveSurfaces.kt`: Figma 배경 합성, 공통 반투명 패널.
- `ui/driving/DrivingComponents.kt`: PageTitle/Back, PrimaryAction, SecondaryAction, SelectionChip, ChoiceCard, ThriveToggle, NavRail, ThriveDialog, 반응형 화면 틀.
- `ui/driving/CourseMap.kt`: Figma 지도 에셋의 배치, 경로/주행 상태와 화면별 높이.
- `PlanningScreens.kt`, `ReviewScreens.kt`, `LiveDriveScreens.kt`: 화면 구성. 기존 ViewModel·저장소·SDK 데이터와 콜백을 사용한다.
- `mockup/tokens.css`: 기존 HTML 목업의 대응 토큰. 현재 운전 앱의 기준 구현은 Android다.
- `res/values/themes.xml`, `MainActivity.kt`: 밝은 실행 배경과 시스템 바.

## 색상과 표면

| 토큰 | 값 | 사용 |
| --- | --- | --- |
| Background | `#FFFFFF` | 배경 fallback / 다이얼로그 |
| Brand / Accent | `#0F3281` | 주요 버튼, 강조 텍스트, 선택 테두리 |
| Text | `#0B1424` | 제목과 본문 |
| TextSecondary | `#1C3C52` | 보조 본문 |
| Mint | `#93E8EE` | 선택 배경·내비게이션·지도 강조색 |
| ReadingSurface | white 92% | 설명·입력·일반 패널 |
| Glass | white 86% | 일부 컨트롤 |
| NavigationSurface | transparent | 내비바 바탕색 없음 |
| NavigationSelection | `#E6E8EB` 72% | 회색 선택 영역 |
| ReadingSurfaceSelected | Mint 18% | 선택 카드 |
| GlassSelected | Mint 35% | 선택 칩 |
| GlassSubtle | Brand 4% | 보조 버튼 |
| Border | Text 8% | 패널 테두리 |
| ControlBorder | Text 14% | 컨트롤 테두리 |
| BorderStrong / Focus | Brand | 선택·포커스 |

`ThriveTheme`은 모든 운전 화면에 같은 lightColorScheme을 제공한다. `ThriveColors.Accent`는 Figma 원본의 네이비이며, 민트는 선택 배경으로 구분한다. 강조색은 화면의 핵심 CTA에 집중한다. 시스템 동적 색상은 사용하지 않는다.

## 타이포그래피

Pretendard 가변 폰트의 400/500/600/700 굵기를 사용한다. 제목 40/32/28sp의 자간만 각각 -0.4/-0.2/-0.1sp로 다듬고, 본문·작은 라벨은 기본 자간을 유지한다. sp와 시스템 글자 배율을 유지하고, 긴 문구는 줄바꿈과 스크롤을 허용한다.

| 역할 | 크기 / 행간 (sp) | 굵기 |
| --- | --- | --- |
| 주행 주요 수치 (displaySmall) | 60 / 80 | 700 |
| 페이지 제목 (headlineSmall) | 40 / 52 | 600 |
| 작은 창 제목·요약 (headlineSmall) | 40 / 52 | 600 |
| 패널 제목 (titleMedium) | 32 / 40 | 600 |
| 카드 제목 (titleSmall) | 28 / 36 | 600 |
| 본문 (bodyLarge) | 28 / 36 | 400 |
| 보조 본문 (bodyMedium) | 24 / 32 | 400 |
| 안내문 (bodySmall) | 22 / 30 | 400 |
| 버튼·칩 (labelLarge) | 24 / 28 | 600 |
| 페이지 구분 (labelMedium) | 24 / 32 | 600 |
| 카드 번호·지도 라벨 (labelSmall) | 20 / 24 | 600 |

## 레이아웃과 상태

1700×1184 앱 영역의 기본값은 왼쪽 28, 위 35, 오른쪽 41dp다. NavRail은 기본 128dp, 콘텐츠와의 간격은 47dp, 콘텐츠 최대 폭은 1480dp다. 시스템 상태바·Driving view·GNB는 앱 내부에 복제하지 않는다.

패널 반경 20dp, 내부 여백 28dp, 섹션 간격 24dp, 선택 카드 간격 16dp를 사용한다. 버튼·칩 터치 높이는 최소 64dp다. 버튼·칩 반경은 16dp다. ChoiceCard의 선택 테두리와 포커스는 2dp이며, 비선택 카드와 일반 패널은 단일 흰색 92% 표면으로 그린다. 일반 패널과 내비바에는 그림자를 사용하지 않으며, 홈 카드에만 3dp의 낮은 그림자를 추가한다. 그림자는 단일 배경 바깥에 그려 내부 색 차이를 만들지 않는다. 다이얼로그는 최대 860dp, 반경 28dp, 여백 44dp다.

콘텐츠 폭 760dp를 기준으로 지도/패널과 3열 카드를 세로로 바꾼다. 글자 배율도 분기 조건에 반영한다. 내비게이션은 네 항목을 28dp 반경의 한 표면에 붙여 배치한다. 내부 여백과 항목 사이 간격은 8dp, 기본 항목 높이는 112dp이며 큰 글자 설정에 맞춰 확대한다. 개별 테두리는 키보드 포커스에만 표시한다. 작은 창에서는 같은 바를 상단 가로 배열로 배치하고, 주행 화면에서는 내비게이션을 숨긴다. 고정 높이로 본문을 잘라내지 않는다.

읽는 중, 저장 오류, 위치 권한 거부, 진행/중단 기록, 조건에 맞는 코스 없음, 음성 오류와 기록 없음 상태는 기존 실제 상태에서 렌더한다. 삭제·기록 동의·참여 제안은 공통 다이얼로그를 사용한다. 실제 기록과 시연 기록의 구분 및 기존 데이터 저장 형식을 유지한다.

## 로컬 에셋

`ThriveBackdrop`은 Figma `104:17094`의 로컬 원본 `home_background_base.png` → 검정 20% → wave(폭 1824/1700, x=-93/1700) → 흰색 20% 순서로 합성한다. 배경의 색과 모양을 새로 그리거나 다른 그라데이션으로 대체하지 않는다.

공통 아이콘·토글·도로·경로·출발/목적지·현재 위치는 Figma가 제공한 SVG를 `assets/figma/`에 보관한다. 같은 SVG를 2배 래스터화한 `drawable-nodpi/*.png` 중 내비게이션 아이콘과 지도 에셋을 네이티브 Image로 표시한다. 토글은 현재 Compose의 트랙·손잡이로 구현하며 원본 이미지는 보관한다. 임시 Figma URL을 런타임에 사용하지 않는다. 에셋의 원본 종횡비를 유지하며 지도 위 숫자는 Compose Text다. 전체 화면 스크린샷을 UI 에셋으로 사용하지 않는다.

## 화면별 Figma 원본

| 흐름 | 기준 노드 | 구현 |
| --- | --- | --- |
| 01 연습하기 | `104:17089`, `104:17162`, `104:17258`, `104:17345`, `104:17436`, `104:17498` | 홈·목표·코스·준비·시연·리포트·다음 연습 |
| 02 운전하기 | `106:17226`, `106:17289`, `106:17347`, `106:17405` | 목적지 검색·검색 상태·코스 |
| 03 실제 지도 연동 | `106:26167`, `106:26266`, `106:26371`, `106:26602`, `106:26642` | 연결·동의·위치 권한·기록·리포트 |
| 04 지도 연동 체험 | `107:34576`, `107:34675` | 연결 상태와 예시 지도·참여 제안 |
| 05 주행 기록 | `107:40247`, `107:40441`, `107:40535`, `107:40629` | 목록·삭제 확인·빈 상태 |
| 06 내 운전 | `109:46498`, `109:46589` | 경험·관심 상황·저장 |
| 07 공통 상태 | `109:50921` ~ `109:51396` | 로딩·복구·권한·오류·빈 상태 |

추가로 구현돼 있던 실제 연습 경로 준비와 참여 제안 알림 설정은 같은 공통 스타일로 유지한다. 실제 연습은 목표 화면의 보조 버튼, 참여 제안 설정은 실제 지도 연동 화면에서 접근한다. Android 권한 팝업은 시스템이 제공한다.

검증은 [Figma 화면 적용 기록](figma-ui-verification.md)을 참고한다.

## Pleos 플랫폼 기준

2026-11-14에 다음 두 원본 파일의 레이어와 렌더를 확인했다. 이후 페이지 제작과 리뷰에서는 Thrive의 브랜드 스타일과 함께 이 플랫폼 기준을 참조한다.

- [Pleos Connect design assets 215](https://www.figma.com/design/vFqBHzfHKQuQjUz4cEYFUI/Pleos_Connect_design_assets_215?node-id=0-1): 타이포, 색상, 아이콘, 앱 아이콘 및 컴포넌트 기준.
  - Foundation 페이지 `0:1`: Typography `67:11477`, Colors `67:14834`, Iconography `67:13755`, App Cover `67:15104`.
  - Components 페이지 `1:2`: 버튼, 슬라이더, 스테퍼, 드롭다운, 입력 필드, 페이지 내비게이션, 팝업, 탭, 선택 컨트롤, 스피너, 시스템 알림.
- [Pleos Connect display guide 215](https://www.figma.com/design/ggi7jxUpX7nx8F8DLgIncn/Pleos_Connect_display_guide_215): Form factors & Display 페이지 `0:1`. 시스템 영역, 단일 앱의 가로 창, 앱 2개 표시와 폼팩터별 배치 기준.
- [공식 UI Design Guide](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro): 사용성·가독성·대비 등 공식 요건을 확인할 문서.

이 문서의 Thrive 토큰은 현재 구현한 앱의 값이고, Pleos 원본 표는 비교·설계 기준이다. 둘을 같은 값으로 표현하거나 현재 구현이 공식 기준을 모두 충족한다고 간주하지 않는다. 공식 필수 요건, 권장값, 의도적인 브랜드 조정을 구분하고 충돌하는 부분은 리뷰에서 근거와 함께 기록한다. 파일명의 `_215`만으로 설치된 에뮬레이터나 SDK 버전을 추정하지 않는다.


### Pleos 원본 타이포와의 비교

[Typography 프레임 `67:11477`](https://www.figma.com/design/vFqBHzfHKQuQjUz4cEYFUI/Pleos_Connect_design_assets_215?node-id=67-11477)의 `Size / Line height` 열에서 확인한 수치다.

| Pleos 역할 | Large | Medium | Small |
| --- | --- | --- | --- |
| Headline | 60 / 80 | 56 / 72 | 48 / 64 |
| Title | 40 / 52 | 36 / 44 | 32 / 40 |
| Body | 30 / 38 | 28 / 36 | 26 / 34 |
| Label | 해당 없음 | 24 / 28 | 20 / 24 |

현재 Thrive의 본문 28sp·보조 정보 24sp와 행간은 앱용 조정값이며 Pleos의 역할 표와 실제 렌더를 함께 비교한다. 향후 페이지 리뷰에서는 같은 역할끼리 비교해 읽기 어려운 축소가 없는지 확인한다. 원본 수치는 실제 글리프 높이·물리적 mm 값이 아니므로 Android dp/sp나 CSS px로 옮길 때 화면 밀도, 글자 배율, 폰트 메트릭과 실제 렌더를 함께 확인한다. 최소값 안내만으로 가독성을 통과시키거나 모든 표 수치를 같은 sp로 일괄 적용하지 않는다.


### Pleos 디스플레이의 앱 영역

다음은 디스플레이 가이드의 설계 좌표 예시다. 전체 디스플레이 크기를 앱이 사용할 수 있는 창 크기로 계산하지 않는다.

| 전체 디스플레이 | 상단 바 높이 | 하단 GNB 높이 | 단일 앱의 가로 창 | 앱 2개일 때 각 앱 창 |
| --- | --- | --- | --- | --- |
| 2560×1440 | 96 | 160 | 1700×1184 | 840×1184 |
| 1920×1080 | 84 | 141 | 1274×855 | 628×855 |

원본 노드: 2560×1440 단일 앱 `2124:7106` / 앱 2개 `2124:7119`, 1920×1080 단일 앱 `2124:7134` / 앱 2개 `2124:7190`.

Status bar·Driving view·GNB는 시스템 영역, Application window는 Thrive의 구현 영역으로 구분한다. 위 좌표와 현재 에뮬레이터의 경계가 같다고 가정하지 않고 실제 창 크기·인셋·배율을 측정해 확인한다. 넓은 창과 분할 창에서 레이아웃을 확인하고, 시스템 영역의 디자인 문제와 앱 자체의 문제를 분리해 기록한다.

## 화면 강조와 모션

홈은 상단 목적지/연습 시작 카드와 하단 오늘의 연습/주행 기록/내 운전 대시보드로 구성한다. 상단은 845:611, 넓은 하단은 728:352:352 비율이며, 콘텐츠 폭 1200dp(글자 배율 반영) 미만에서는 연습 카드와 기록/설정 묶음을 세로로 배치한다. 기록 수·최근 기록·운전 경험·관심 상황은 저장된 실제 값을 사용한다. 시연 진입은 하단의 작은 텍스트 버튼으로 제공한다. 별도 장식 경로 그림과 카드 자체의 그라데이션은 추가하지 않는다. 원본의 물결 배경은 유지한다. 연습 단계는 3개의 얇은 진행 막대로 표시하고 접근성 설명을 제공한다. 기록에는 실제·시연 상태 배지를 유지하고, 목록 행으로 날짜·시간·동작을 정렬한다.

모션 기준은 `ui/driving/DrivingMotion.kt`의 `ThriveMotion`과 `MotionMode`다. 추가 런타임 의존성은 없다.

| 대상 | 효과 | 기본 시간 |
| --- | --- | --- |
| 버튼·칩·선택 카드 누름 | 그리기만 98.5%로 축소, 터치·포커스 영역 유지 | 120ms |
| 선택 카드 | 옅은 민트 강조층·체크 페이드, 체크 90→100% | 160ms |
| 토글 | 40dp 손잡이 이동, 연속 입력 시 현재 값에서 재설정 | 180ms |
| 내비게이션 선택 | 회색 선택 영역 이동, 아이콘·터치 영역 고정 | 180ms |
| 연습 단계 본문 | 16dp 이동＋페이드, 뒤로 갈 때 반대 방향 | 180ms |
| 다이얼로그 | 중앙 97→100%＋페이드와 배경 어둡게 | 열기 220ms / 닫기 140ms |
| 저장 성공 | 실제 저장 성공 후 같은 자리에 체크만 페이드 | 160ms |

공통 곡선은 `CubicBezierEasing(.23f, 1f, .32f, 1f)`다. 내비게이션의 선택 영역 이동에는 `EaseInOut(.77f, 0f, .175f, 1f)`을 사용한다. 아이콘은 선택 시 Brand, 비선택 시 순수 검정이며 라벨은 검정을 유지한다. 모션 줄이기에서는 위치 이동 대신 100ms 교차 페이드, 모션 Off에서는 즉시 선택 상태를 반영한다. 탭의 실제 화면 전환은 지연하지 않는다. 주행 화면·수치·지도와 배경에는 장식 모션을 추가하지 않는다. 저장·출발·확정 콜백을 모션 완료까지 미루지 않는다. 공통 다이얼로그 호스트가 화면 밖에서 닫힘을 처리하고 중복 확정을 막는다.

최신 5초 이내 P단 응답이 있고, 화면이 주행 중이 아니며, 진행 중인 기록도 없고, 터치 입력·시스템 애니메이션이 켜져 있을 때만 커스텀 모션을 허용한다. 차량 읽기는 기존 `ParkConnection`으로 화면이 활성화된 동안 연결·구독하며 3초마다 갱신한다. 응답 만료·연결 끊김·기어 변경·화면 비활성화 시 효과를 즉시 차단한다. 차량 정보가 없을 때 P단으로 가정하지 않는다. 새로운 권한 요청 흐름은 추가하지 않았다.

프로필의 ‘화면 움직임 줄이기’는 기기에 저장한다. 켜면 이동·확대를 생략하고 100ms 페이드만 사용한다. 키보드 입력이나 시스템 애니메이션 배율 0에서는 즉시 상태를 반영한다. 배율 변경은 화면을 다시 열지 않아도 감지한다.

프로필·음성 설정·리포트 느낌 저장의 완료 표시는 DataStore/DB 작업이 성공한 뒤에만 발행한다. 더 최근 요청이 있으면 앞 요청의 완료 표시를 버리고, 2.5초가 지난 완료 표시를 화면 재진입 때 다시 재생하지 않는다. 시각적으로는 체크만 표시하며 접근성의 polite live region으로 ‘저장 완료’를 알린다.

### Practice curriculum implementation (2026-11-15)

Figma sections `136:71252` and `137:71252` replace the legacy three-goal entry with 17 beginner and 14 returning-driver topics. Track selection persists in DataStore. Three-column browsing stacks on narrow content widths. Stationary topics require fresh P state; beginner road preparation requires companion confirmation. Destination, parking, and conditional night/rain topics have separate branches. Topic/course IDs persist through Room migration 4 to 5; progress counts unique completed topics. Removing the only completed record for a topic also removes its completion. Reports suggest the next topic or a shorter repeat based on the saved feeling.

Conditional practice uses an explicit current-coordinate lookup against Open-Meteo, with a 30-minute freshness gate. Optional one-shot reminders check the saved area for up to seven days using Android JobScheduler; they do not track background location. System scheduling and network availability affect delivery time. Courses, places, and navigation remain explicitly labeled Figma examples.

Validation: 65 unit tests passed; verification and regular APK builds passed. The user took over manual screen testing before the automated UI scenario completed. No full end-to-end UI pass is claimed.
