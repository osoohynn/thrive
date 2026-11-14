# Thrive 디자인 시스템

브랜드 스타일 기준: [Thrive Figma · 3안_메인, 2:251](https://www.figma.com/design/V1lYJYRGTstcbi3U6VqEAP/Thrive?node-id=2-251).

3안의 네이비·블루/시안 파형, 반투명 표면, 흰색 타이포, 얇은 테두리와 둥근 컨트롤을 기존 휴식 대시보드에 적용한다. 검색·채팅 기능, 국회도서관 로고, 사용자 프로필은 가져오지 않는다. 휴식 선택·시작·종료와 고정 차량 예시 데이터는 기존 동작을 유지한다.

## Pleos 플랫폼 기준

2026-11-14에 다음 두 원본 파일의 레이어와 렌더를 확인했다. 이후 페이지 제작과 리뷰에서는 Thrive의 브랜드 스타일과 함께 이 플랫폼 기준을 참조한다.

- [Pleos Connect design assets 215](https://www.figma.com/design/vFqBHzfHKQuQjUz4cEYFUI/Pleos_Connect_design_assets_215?node-id=0-1): 타이포, 색상, 아이콘, 앱 아이콘 및 컴포넌트 기준.
  - Foundation 페이지 `0:1`: Typography `67:11477`, Colors `67:14834`, Iconography `67:13755`, App Cover `67:15104`.
  - Components 페이지 `1:2`: 버튼, 슬라이더, 스테퍼, 드롭다운, 입력 필드, 페이지 내비게이션, 팝업, 탭, 선택 컨트롤, 스피너, 시스템 알림.
- [Pleos Connect display guide 215](https://www.figma.com/design/ggi7jxUpX7nx8F8DLgIncn/Pleos_Connect_display_guide_215): Form factors & Display 페이지 `0:1`. 시스템 영역, 단일 앱의 가로 창, 앱 2개 표시와 폼팩터별 배치 기준.
- [공식 UI Design Guide](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro): 사용성·가독성·대비 등 공식 요건을 확인할 문서.

이 문서의 Thrive 토큰은 현재 구현한 앱의 값이고, Pleos 원본 표는 비교·설계 기준이다. 둘을 같은 값으로 표현하거나 현재 구현이 공식 기준을 모두 충족한다고 간주하지 않는다. 공식 필수 요건, 권장값, 의도적인 브랜드 조정을 구분하고 충돌하는 부분은 리뷰에서 근거와 함께 기록한다. 파일명의 `_215`만으로 설치된 에뮬레이터나 SDK 버전을 추정하지 않는다.

## 구현 위치

- `app/src/main/java/com/thrive/pleos/ui/theme/ThriveTheme.kt`: Android 토큰의 기준. `ThriveColors`, `ThriveSpace`, `ThriveShape`, `ThriveSize`, Material 3 Typography/ColorScheme.
- `app/src/main/java/com/thrive/pleos/ui/theme/ThriveSurfaces.kt`: 로컬 배경을 합성하는 `ThriveBackdrop`, 콘텐츠 패널 `ThriveGlassPanel`.
- `mockup/tokens.css`: 기존 HTML 검토용 미리보기의 대응 토큰. Android 토큰 변경 시 함께 갱신한다.
- `app/src/main/res/values/themes.xml`: 앱 실행 직전의 배경·시스템 색상. 배경·강조색 변경 시 Compose와 맞춘다.

새 Compose 화면은 `ThriveTheme` 안에서 `MaterialTheme.typography`와 토큰을 사용한다. 반투명 패널은 배경 위에 배치하고, 중첩 패널은 최소화한다. 색상이나 글자 크기를 화면마다 새로 선언하지 않는다.

## 색상과 표면

| Kotlin 토큰 | CSS 토큰 | 값 | 근거 / 용도 |
| --- | --- | --- | --- |
| Background | --color-background | `#0B1424` | 어두운 원본 배경에 맞춘 앱용 단색 fallback |
| Brand | --color-brand | `#0F3281` | Figma 원본 배경·프로필 색상 |
| Text | --color-text | `#FFFFFF` | Figma 제목·주요 텍스트 |
| TextSecondary | --color-text-secondary | `#D5DEEA` | 원본 보조색을 밝게 보정해 본문 대비 확보 |
| Accent | --color-accent | `#93E8EE` | 배경의 시안 계열을 옮긴 앱용 강조색. 상태·선택 번호·배터리 |
| Glass | --color-glass | white 10% | Figma 왼쪽 패널·검색창 |
| GlassSubtle | --color-glass-subtle | white 5% | Figma 보조 안내 패널. 작은 보조 면에 사용 가능 |
| GlassSelected | --color-glass-selected | white 16% | 간단한 반투명 강조 면 |
| ReadingSurface | --color-reading-surface | `#142238`, alpha 235/255 | 설명·입력·버튼·패널의 읽기용 표면 |
| ReadingSurfaceSelected | --color-reading-surface-selected | `#1C3C52`, alpha 240/255 | 선택 카드·칩의 읽기용 표면 |
| ControlBorder | --color-control-border | white 44% | 보조 버튼·입력창·선택 칩 구분 |
| Border | --color-border | white 18% | 앱의 일반 패널 구분을 위해 확장 |
| BorderStrong | --color-border-strong | white 60% | Figma 검색창 테두리 → 선택 강조 |
| Scrim | --color-scrim | black 64% | Figma 원본의 40%를 앱 가독성에 맞춰 확장 |
| Focus | --color-text | `#FFFFFF` | 키보드·차량 컨트롤 포커스. 2dp 테두리 |

Figma에 실제 연결된 변수는 아이콘 회색과 자간 0뿐이어서, 위 토큰은 `get_design_context`의 레이어 값에서 추출했다. 앱용으로 확장한 값은 표에 별도로 표시했다. 시스템의 동적 색상으로 테마가 바뀌지 않도록 고정 다크 테마를 사용한다.

## 타이포그래피

원본과 같은 Pretendard를 앱에 포함한다. 가변 폰트의 400 / 500 / 600 / 700 굵기를 명시해 시스템의 가짜 굵기를 피한다. 자간은 원본 기준 0이다. Figma의 위계와 서체를 유지하면서 차량 화면에 맞게 글자 크기를 확대한다. 시스템 글자 배율을 강제로 변경하거나 고정하지 않고 sp 단위를 사용한다.

아래 표는 현재 Thrive 구현의 토큰이다.

| Material 역할 | 크기 / 행간 (sp) | 굵기 | 사용 |
| --- | --- | --- | --- |
| displayLarge | 72 / 84 | 500 | 차량 배터리 수치 |
| headlineLarge | 56 / 72 | 600 | 넓은 화면 인사말 |
| headlineMedium | 48 / 64 | 600 | 넓은 창의 페이지 제목·휴식 제목 |
| headlineSmall | 40 / 52 | 600 | 좁은 창의 페이지 제목·주행 요약 |
| titleMedium | 32 / 44 | 600 | 섹션 제목 |
| titleSmall | 28 / 38 | 600 | 활동 제목·차량 제목 |
| bodyLarge | 28 / 40 | 400 | 본문 |
| bodyMedium | 24 / 34 | 400 | 보조 정보 |
| bodySmall | 22 / 32 | 400 | 공통 시연 안내·지도 라벨 |
| labelLarge | 26 / 36 | 600 | CTA |
| labelMedium | 22 / 30 | 500 | 상태·시간·번호 |

전체 Material 3 타이포 역할에도 Pretendard를 설정해 다른 화면에서 기본 글꼴이 섞이지 않게 했다. 기본 본문은 28sp, 보조 정보는 24sp로 확대하고 제목의 굵기·행간을 구분했다. 가장 작은 보조 역할은 20sp이며 공통 시연 안내는 22sp로 표시한다. 페이지 제목은 넓은 창에서 48sp, 좁은 창에서 40sp를 사용한다. [Pleos 공식 Typography 가이드](https://document.pleos.ai/docs/connect/guide/docs-design/foundations/typography)의 정보 위계·가독성 원칙을 참고했다. CTA의 문구·시간과 배터리 수치·레이블은 공간이 부족하면 줄을 바꿔 표시한다.

### Pleos 원본 타이포와의 비교

[Typography 프레임 `67:11477`](https://www.figma.com/design/vFqBHzfHKQuQjUz4cEYFUI/Pleos_Connect_design_assets_215?node-id=67-11477)의 `Size / Line height` 열에서 확인한 수치다.

| Pleos 역할 | Large | Medium | Small |
| --- | --- | --- | --- |
| Headline | 60 / 80 | 56 / 72 | 48 / 64 |
| Title | 40 / 52 | 36 / 44 | 32 / 40 |
| Body | 30 / 38 | 28 / 36 | 26 / 34 |
| Label | 해당 없음 | 24 / 28 | 20 / 24 |

현재 Thrive의 본문 28sp·보조 정보 24sp와 행간은 앱용 조정값이며 Pleos의 역할 표와 실제 렌더를 함께 비교한다. 향후 페이지 리뷰에서는 같은 역할끼리 비교해 읽기 어려운 축소가 없는지 확인한다. 원본 수치는 실제 글리프 높이·물리적 mm 값이 아니므로 Android dp/sp나 CSS px로 옮길 때 화면 밀도, 글자 배율, 폰트 메트릭과 실제 렌더를 함께 확인한다. 최소값 안내만으로 가독성을 통과시키거나 모든 표 수치를 같은 sp로 일괄 적용하지 않는다.

## 간격·형태·레이아웃

| 구분 | 토큰 / 값 | 규칙 |
| --- | --- | --- |
| 간격 | Xs 4, Sm 8, Md 12, Lg 16, Xl 20, Xxl 24, Xxxl 32, Section 40 dp | 원본의 8·16·24·40 간격을 기본으로 4 단위 확장 |
| 형태 | Small 8, Panel 20 dp, Pill 50% | 패널은 원본 20, CTA·상태는 완전한 pill |
| 테두리 | 기본 1dp, 포커스 2dp | 포커스가 생겨도 내부 배치는 이동하지 않음 |
| 콘텐츠 | 최대 1200dp, 넓은 화면 전환 760dp | 기존 대시보드의 1.7:1 열 비율 유지 |
| 여백 | 넓은 화면 32dp, 좁은 화면 24dp | 시스템 safe drawing inset 추가 |
| 터치 | CTA 최소 64dp, 활동 카드 최소 128dp | 글자가 커지면 카드 높이도 늘어남 |
| 카드 | 최소 320dp | 고정 높이·문구 생략 없이 스크롤 허용 |

### Pleos 디스플레이의 앱 영역

다음은 디스플레이 가이드의 설계 좌표 예시다. 전체 디스플레이 크기를 앱이 사용할 수 있는 창 크기로 계산하지 않는다.

| 전체 디스플레이 | 상단 바 높이 | 하단 GNB 높이 | 단일 앱의 가로 창 | 앱 2개일 때 각 앱 창 |
| --- | --- | --- | --- | --- |
| 2560×1440 | 96 | 160 | 1700×1184 | 840×1184 |
| 1920×1080 | 84 | 141 | 1274×855 | 628×855 |

원본 노드: 2560×1440 단일 앱 `2124:7106` / 앱 2개 `2124:7119`, 1920×1080 단일 앱 `2124:7134` / 앱 2개 `2124:7190`.

Status bar·Driving view·GNB는 시스템 영역, Application window는 Thrive의 구현 영역으로 구분한다. 위 좌표와 현재 에뮬레이터의 경계가 같다고 가정하지 않고 실제 창 크기·인셋·배율을 측정해 확인한다. 넓은 창과 분할 창에서 레이아웃을 확인하고, 시스템 영역의 디자인 문제와 앱 자체의 문제를 분리해 기록한다.

## 컴포넌트와 상태

아래는 현재 Thrive 컴포넌트의 동작·스타일이다. 새 페이지에서 같은 역할의 요소를 만들거나 리뷰할 때 Pleos Components 페이지의 해당 원본도 함께 확인한다. 상태·정렬·간격·터치 영역을 비교하되 모든 원본 컴포넌트를 앱에 도입하거나 시스템 바를 앱 내부에 복제하지 않는다.

- **배경**: 원본 이미지 두 장을 `drawable-nodpi`에 포함하고, Figma와 같은 순서로 네이비 → 파형 이미지(124.61% × 124.68%, -21.01% / -19.37% 위치) → 전체 배경 이미지(cover) → 앱용 검정 64%를 합성한다. 파일명은 `aurora_wave.png`, `aurora_grain.png`. 현재 두 원본은 불투명해서 위의 전체 배경 이미지가 아래 이미지를 덮는다. 원본 레이어 구조를 보존하되 스크린샷을 UI로 사용하지 않는다. 외부 URL·네트워크 권한은 필요 없다.
- **일반 패널**: ReadingSurface + Border + Panel, 내부 여백 24dp. 어두운 반투명 표면으로 파형이 설명 글자와 겹치지 않게 한다. 별도 blur 효과는 추가하지 않는다.
- **주요 버튼**: 흰색 표면 + 네이비 텍스트, pill, 최소 64dp. 짧은 문구와 시간을 함께 표시한다. Material ripple로 즉각적인 누름 피드백을 제공한다.
- **활동 선택**: 일반 ReadingSurface/Border → 선택 ReadingSurfaceSelected/BorderStrong. 번호와 선택 점은 Accent. 단일 선택 그룹과 Material Surface의 기본 체크 상태를 제공해 색상 이외에도 선택 여부를 전달한다.
- **선택 칩·상단 메뉴**: 공통 `SelectionChip`에 읽기용 표면·밝은 테두리·체크 표시를 적용한다. 메뉴 선택은 하위 화면에서도 해당 흐름의 상위 메뉴를 유지한다.
- **입력창·보조 버튼**: ReadingSurface와 ControlBorder를 사용한다. 입력창은 최소 88dp이며 포커스 테두리와 커서에는 Accent를 적용한다.
- **포커스**: 활동은 흰색 2dp, 주요 버튼은 시안색 2dp. HTML은 `:focus-visible` outline으로 표시한다.
- **배터리**: 높이 6dp, Accent 채움, Border 트랙. 보조 기술에 78%를 전달한다. 수치는 기존 정적 예시다.
- **모션**: 상시 배경 움직임·진입 애니메이션은 추가하지 않는다. 자주 쓰는 선택·시작 조작은 바로 반영한다. HTML도 이동 애니메이션 없이 누름 밝기 피드백만 사용한다.

## 참고한 Codex 스킬

- `figma-design-to-code`: 실제 레이어 값·원본 렌더 확인, 기존 Compose 구조에 맞춘 변환, 로컬 에셋 사용.
- `emil-design-eng`: 반복 조작의 즉각적인 응답, 절제된 모션, 타이포와 상호작용의 일관성.
- `ponytail`: 기존 Compose/Material 3와 CSS로 구현, 추가 UI 라이브러리·테마 프레임워크 없이 구성.
- `thrive-design-review`: 완성된 페이지를 Pleos 원본·현재 Thrive 토큰·실제 렌더와 비교하고, 우선순위와 근거를 포함한 리뷰를 제공한다. 기본 동작은 리뷰이며 수정은 별도로 요청한 범위에 따른다. 호출 예: `$thrive-design-review 완성된 전체 페이지를 리뷰해줘`.

## 에셋 출처

배경 이미지는 위 Figma 3안의 원본 에셋이다. 다른 서비스의 로고·아이콘·문구는 포함하지 않았다.

Pretendard v1.3.9는 [공식 저장소](https://github.com/orioncactus/pretendard/tree/v1.3.9)의 가변 TTF를 수정 없이 사용한다. [공식 SIL Open Font License](https://github.com/orioncactus/pretendard/blob/v1.3.9/LICENSE)는 `app/src/main/assets/licenses/Pretendard-OFL.txt`에 포함되어 APK 안에도 배포된다.

## 운전 적응 화면 적용

2026-11-14: `ui/driving/`에 홈·지도 연동 체험·연습 목표·목적지·코스 선택·출발 준비·주행 시연·리포트·기록·프로필을 추가했다. 앞서 제작한 HTML 디자인의 정보 구조를 이 문서의 네이비·시안 토큰과 기존 Pretendard/배경/표면으로 변환했다. 최초 구현에서는 기존 테마 값을 유지했다. 이후 가독성 수정으로 타이포·읽기용 표면·배경 스크림을 조정하고 `mockup/tokens.css`에도 같은 값을 반영했다.

- 화면은 `ThriveTheme`와 `ThriveBackdrop` 안에서 렌더한다. 시스템 바·공조·계기판을 앱 내부에 복제하지 않는다.
- 패널·선택 카드·CTA는 기존 간격·라운드·표면·포커스·최소 터치 크기를 따른다. 화면별 색상·서체를 새로 정의하지 않는다.
- 지도는 `ThriveColors`로 그린 독립적인 모의 도식이며 실제 지도나 경로 데이터가 아니다.
- 760dp 이상에서는 지도와 정보를 두 열로 배치하고 분할 창에서는 세로로 배치한다. 큰 글자에서 콘텐츠 높이가 늘어날 수 있도록 스크롤을 허용한다.
- 연동 체험의 제안은 앱 내부 UI다. 다른 지도 앱 감지나 차량 헤드업 알림 지원을 의미하지 않는다.
- 기존 휴식 화면은 보존하며 신규 앱 진입점만 운전 적응 홈으로 연결한다.

2026-11-14 가독성 수정: 파형 위 설명의 대비, 보조 버튼·검색창의 경계, 작은 본문, 선택 상태를 공통 컴포넌트에서 보완했다. 큰 글자에서는 창 폭과 글자 배율을 함께 고려해 두 열을 세로 배치로 전환한다. 지도 라벨과 공통 시연 안내에도 읽기용 표면을 적용했다. 지도 라벨은 좌측 상단에 두어 출발 표시를 가리지 않는다.

실제 렌더 검증 결과는 별도 구현 검증 기록에 남긴다. 소스 토큰 재사용만으로 플랫폼 요건 충족을 단정하지 않는다.
