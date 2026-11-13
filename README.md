# 헤일로 (Halo) — 궂은 날의 안전 지킴이

눈·비·안개·살얼음처럼 날씨가 궂은 날, 차가 먼저 알아채고 운전자에게 한 문장으로 묻는 Pleos Connect 차량 앱입니다.
운전자는 “응” 한마디로 답하고 앞만 보면 됩니다. 판단은 앱이 대신하되, 결정은 운전자가 합니다.

- 먼저 알아챈다: 차량 외기온도·와이퍼·기어·속도와 경로 위 위험 지점을 함께 본다
- 먼저 묻는다: 한 번에 질문 하나, 방향지시등·급감속·내비 음성 안내 중에는 묻지 않는다 (비상등 제안만 예외)
- “응” 한마디로 끝낸다: 대답이 없으면 아무것도 바꾸지 않고 질문을 화면에 남긴다

## 기능 (1·2단계)

| 기능 | 내용 |
| --- | --- |
| 시동 브리핑 | 외기온도, 최근 강수(녹았다 언 길), 예보, 경로 위 위험 구간, 감속하면 늦어지는 시간, 날씨 관련 차 점검(공기압·와이퍼·워셔액·오토 디포그·전조등) |
| 살얼음·안개 레이더 | 관심·주의·위험 3단계. 관심은 화면에만, 주의는 1km 전 한 번, 위험은 1km·300m 전 두 번. 기상청 관측이 없는 구간(교량·터널 출구)은 내 차 센서로 판단하고 근거를 보여 준다 |
| 비·눈 모드 | 와이퍼가 오래 움직이거나 영하 근처에서 눈·진눈깨비가 오면 묻는다. 차가 이미 자동으로 하는 것(오토 디포그·오토 라이트)은 그대로 두고 자동이 아닌 열선만 켜며, 날이 개거나 도착하면 켠 것만 끌지 묻는다 |
| 날씨 맞춤 등화 | 비·눈·안개인데 전조등이 꺼져 있으면, 짙은 안개 구간 앞에서 안개등, 궂은 날 앞차들이 급하게 느려지면 비상등을 묻는다. 앱이 켤 수 없는 차량·등화(비상등)는 스위치 위치를 알려 주고 켜졌는지 확인한다 |
| “응 / 아니” 음성 답 | 질문을 읽은 뒤 대답을 듣는다. 화면 버튼은 동승자·음성 인식을 쓸 수 없을 때를 위한 보조 |
| 꼼꼼히 안내 | 1.5km 전부터, 이유를 덧붙여, 못 들으면 안전한 때 한 번 더 묻는다 (처음 운전하는 사람·고령 운전자) |
| Gleo AI | “헤일로, 길 위험 다시 알려 줘 / 브리핑 다시 / 눈 모드 켜 줘” (`assets/schema.json`) |
| 시연 시나리오 | 기획서의 장면 3가지(1월 출근길 영하 4도 · 7월 장마 퇴근길 · 10월 새벽 강변 안개)를 같은 판단 엔진으로 재생 |

## 구조

```
app/src/main/java/com/halo
├── domain/        판단 엔진 (안드로이드 의존 없음, 단위 테스트로 검증)
│   ├── HaloEngine.kt      상태를 받아 읽을 말·질문·차량 제어를 낸다
│   ├── RiskEngine.kt      살얼음·안개·폭우 단계와 판단 근거
│   ├── AlertPlanner.kt    음성 경보 시점 (1km · 300m)
│   ├── Planners.kt        비·눈 모드, 등화, 질문 시점, 대답 읽기
│   ├── Briefing.kt        시동 브리핑과 지연 시간
│   ├── Phrases.kt         음성·화면 문장
│   ├── LiveTrip.kt        실제 주행: 내비 주의 지점과 지나온 거리
│   └── GleoCommand.kt     Gleo AI 기능 호출과 응답
├── demo/          시연 시나리오 3가지
├── data/          차량·내비·음성 연결
│   ├── CarVehicleFeed.kt  AAOS 차량 속성 + Pleos Vehicle SDK
│   ├── pleos/             SDK 리스너를 자바로 감싼 부분
│   ├── PleosNaviFeed.kt   NaviHelper (남은 시간·도착·주의 지점)
│   └── speech/            Pleos TTS·STT (없으면 안드로이드 기본 음성)
├── gleo/          Gleo AI 브로드캐스트 수신
├── ui/            Jetpack Compose 화면
└── HaloViewModel.kt   엔진 효과를 차례로 처리 (음성 줄 세우기, 제어, 안내)
```

## 빌드

GitHub Actions(`.github/workflows/android.yml`)가 푸시마다 단위 테스트를 돌리고 디버그 APK를 만듭니다.
`v*` 태그를 올리면 릴리스에 APK가 붙습니다.

로컬에서는 JDK 17과 Android SDK(compileSdk 35)가 있으면 됩니다.

```bash
./gradlew testDebugUnitTest assembleDebug
# 화면 확인 (에뮬레이터 없이): app/src/test/snapshots/images
./gradlew recordPaparazziDebug
```

Pleos Connect SDK는 `https://nexus-playground.pleos.ai/repository/maven-releases/` 에서 받습니다.

## 실행

- 차량(AAOS): 실제 차량 값으로 동작합니다. 설정에서 시연 시나리오로 바꿀 수 있습니다.
- 차량이 아닌 기기(태블릿·일반 에뮬레이터): 시연 시나리오로 시작합니다. 화면 아래 시연 바에서 장면을 넘깁니다.

권한

| 권한 | 쓰는 곳 |
| --- | --- |
| `android.car.permission.CAR_SPEED` | 속도 (급감속, 주행 중 여부) |
| `android.car.permission.CAR_EXTERIOR_ENVIRONMENT` | 외기온도 |
| `android.car.permission.CAR_POWERTRAIN` | 시동·기어 |
| `android.permission.RECORD_AUDIO` | 차량이 아닌 기기에서 기본 음성 인식으로 대답을 들을 때 |

## 사용한 Pleos Connect SDK

| SDK | 쓰는 것 |
| --- | --- |
| Vehicle 2.3.2 | 기어, 방향지시등, 와이퍼 상태·스위치, 전조등·안개등 상태·스위치·제어, 사이드미러·핸들 열선, 공조 AUTO, 앞유리 김서림 방지, 워셔액, 타이어 공기압 |
| NaviHelper 3.0.5 | 목적지 남은 거리·시간, 도착, 주의 지점(미끄럼 주의) |
| TextToSpeech / SpeechToText 3.3.1 | 읽어 주기, “응 / 아니” 듣기 |
| Gleo AI | `schema.json` 함수 3개 (길 위험 · 브리핑 · 날씨 모드) |

## 아직 하지 않은 것

- 기상청 도로위험기상정보(살얼음·가시거리) 연동: 시연 시나리오는 예시 값을 쓰고, 실제 주행에서는 차량 센서와 내비 주의 지점만으로 판단합니다.
- 비상등은 SDK에 제어 기능이 없어 버튼 위치만 안내합니다. 전조등·안개등·열선 제어는 차량의 제어 권한 승인 여부에 따라 안내로 바뀝니다.
- 실제 차량에서의 동작(권한, 차종별 지원 기능, 음성 인식 정확도)은 확인이 필요합니다.

## 라이선스 고지

- 글꼴: [Pretendard](https://github.com/orioncactus/pretendard) — SIL Open Font License 1.1 (`app/src/main/assets/licenses/pretendard_OFL.txt`)
