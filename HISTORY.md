# History

## 2026-11-24 (CI: Robolectric SDK jar 다운로드 실패 대응)

- **증상**: main 푸시 `Android Build`의 `unit-tests`가 두 번 실패(11-23 07:21, 09:02). Robolectric 테스트 클래스 13개가 전부 `classMethod FAILED` — `MavenArtifactFetcher` → `IOException`. 같은 트리의 PR 실행은 통과.
- **원인**: Robolectric이 테스트 시작 시 android-all SDK jar를 Maven Central에서 받는데(`~/.m2`), 워크플로가 이를 캐시하지 않아 매 실행이 그 다운로드에 걸려 있었다. 일시적 네트워크 오류 한 번이면 클래스 전체가 한꺼번에 실패한다. 코드 결함이 아니다.
- **변경**: `.github/workflows/android-build.yml` — `~/.m2/repository/org/robolectric`을 `actions/cache`로 캐시(키: `libs.versions.toml`+`robolectric.properties` 해시), 캐시 미스일 때를 위해 로그에 `MavenArtifactFetcher`가 있을 때만 최대 3회 재시도(진짜 테스트 실패는 즉시 실패).
- **검증**: CI에서 확인(PR 참고). 로컬 빌드는 하지 않음.
- **미확인**: 캐시가 실제로 `~/.m2/repository/org/robolectric`에 채워지는지는 PR 실행의 두 번째 실행(캐시 히트)에서 확인해야 한다.

## 2026-11-23 (형제끼리 겨루지 않는 월드컵, v0.13.0)

- **요청**: "아이가 두 명인데 서로 월드컵을 하면 안 될 것 같아. 인식된 아이별로 따로 해야 하지 않을까?" → 확인해 보니 아이별 월드컵은 v0.12.0에 있었지만 섞이는 경로가 셋 남아 있었음: ① 준비 화면 기본값 "모두" ② 추천 결과 "바로 월드컵"은 아이 구분 없이 시작 ③ 이달의 사진·홈 카드가 달 단위(둘째 월드컵이 첫째의 "10월의 사진"을 가져감). 사용자가 "모두"를 아예 없애는 쪽을 고름.
- **변경**: `cupsArePerChild`(아이 ≥2) 규칙 하나로 준비 화면(모두 없음, `defaultCupChild`로 첫 아이·그달 아직 안 한 아이 우선, 아이별 사진이 없으면 "우리 아이들" 안내), `startWorldCup` 거부, `saveCurationRound(child)`·"누구의 월드컵부터 할까요?" 대화상자, Room v7 `tournament_records.childId`(v0.12.0 제목에서 되찾기), `MonthlyCup.pending`/`titleHolders`를 (달, 아이)로, 알림·홈 카드·우승 화면·우승 전당에 아이 이름.
- **검증**: 단위 테스트(새로 아이별 이달의 월드컵·칭호, v6→v7 마이그레이션 되찾기, 형제 월드컵 흐름 4개). 에뮬레이터 `bebecup-phone-api36`에 v0.11.0 위로 GitHub 디버그를 덮어 설치(기기 SQLite에서 v5→v7), 두 아이·사진을 DB에 넣고 확인: 홈 "10월의 하린 월드컵" → 준비 화면에 "모두" 없음·하린 10월 4장 → 4강 진행 → 기록 `childId=1` → 홈 카드가 "10월의 도윤 월드컵"으로 바뀜.
- **리뷰(PR #28)**: Codex 지적 없음. 독립 리뷰 4건 + nit 1건 — 한 아이 집에서 같은 달 왕관 둘(아이 없을 때 칭호는 달 단위로) 수정, 달 칩을 누르면 기본 아이가 바뀜(열 때 아이 고정) 수정, 콜드 스타트에 아이 목록을 읽기 전 가족 모드(`childrenLoaded` 전에는 준비 화면·시작 막음) 수정·저장된 대진표 복원 검사는 BACKLOG로 이월, 건너뛰기가 그달 모든 아이에 적용되는 것은 한 달 한 번 알림이라는 설계대로 유지, 대화상자 회전 유지(`rememberSaveable`) 수정. 테스트 추가: 형제 알림(이름·아이 id), 한 아이 칭호, 같은 이름 두 아이 마이그레이션, 기본 아이.
- **미확인**: 에뮬레이터에서 "바로 월드컵" 대화상자(얼굴 구분 정리를 다시 돌려야 해 Robolectric 테스트로 갈음), 실제 형제 사진.

## 2026-11-23 (아이 얼굴 구분·아이별 월드컵, v0.12.0)

- **요청**: 아이가 둘인데 한 '아기 사진'으로 합쳐져 월드컵이 섞임 → 두 아이를 따로 인식, 인식된 아이마다 이름 설정. 모델은 앱에 넣지 말고 설정·온보딩에서 받기.
- **원인**: ML Kit 얼굴 인식은 위치·눈·표정만 주고 사람을 구별하지 않음. 앱도 아기 한 명(prefs 이름·생일)만 알았음.
- **모델**: OpenCV Zoo SFace(Apache-2.0)를 TFLite로 옮김. onnx2tf가 Windows에서 `ai_edge_litert` 없이 돌지 않아, 27단 직렬 구조를 Keras로 직접 옮기는 `scripts/face-id-model/convert_sface.py`를 씀(float32는 ONNX와 동일, int8 9.9MB는 실제 얼굴에서 0.997+). GitHub 릴리스 `face-id-model-v1`(prerelease, latest 아님)에 LICENSE·NOTICE와 함께 올림. `DECISIONS.md` 참고.
- **변경**: Room v6(`children`·`photo_faces`·`face_references`·`faceIdVersion`), `FaceIdentityReader`(ML Kit 눈·입꼬리 → 정렬 → TFLite), `FaceMatcher`(예시 top-3 평균 0.40 + 차이 0.06, 평균 연결 0.42로 묶기), `FamilyFaces`, 우리 아이들 화면·얼굴 묶음 이름 붙이기·사진별 고치기·보관함 아이 필터·아이별 월드컵·온보딩 스위치·설정 카드. `github` APK는 네이티브 라이브러리 압축.
- **에뮬레이터에서 잡은 것 2가지**: ① ML Kit의 `RIGHT_EYE`가 화면 오른쪽 — 이름대로 템플릿에 맞추면 얼굴이 뒤집혀 다른 사람끼리 코사인 0.56. 정렬이 얼굴 방향으로 좌우를 정하게 고침(같은 사람 ≥0.53, 다른 사람 ≤0.39, LFW 3명 24장이 정확히 3묶음). ② R8 full mode가 `InterpreterFactoryImpl.<init>`을 지워 릴리스 빌드에서만 얼굴 구분이 실패(v0.7.1의 ML Kit와 같은 종류). keep 규칙 + CI seeds 확인 추가.
- **검증**: 단위 테스트(Play 112·GitHub 116, 새로 FaceMatcher·FamilyFaces·아이별 월드컵·v6 마이그레이션). 에뮬레이터 `bebecup-phone-api36`에서 GitHub 디버그와 R8 릴리스(디버그 키 재서명): 온보딩 스위치로 GitHub 릴리스에서 모델 받기(SHA 확인) → 정리 → "얼굴 3명" → 이름 붙이기·아이 아님 → 보관함 이름 표시·필터 → 아이별 월드컵(그 아이 사진만).
- **미확인**: 실제 아기·형제자매 사진의 정확도, Play 빌드의 얼굴 구분(에뮬레이터에 Play 계정이 없어 ML Kit 모듈을 못 받음 — 실기기 필요).
- **리뷰(PR #26)**: Codex 2건 + 독립 리뷰 5건(1건 겹침) 모두 수정 — 늦게 끝난 재매칭이 부모 지정을 덮어씀(Mutex + `state != manual` 조건), "아이 아님" 점수를 가장 가까운 예시 하나로, Android 11 이하 SQLite 변수 999개 한도(나눠 보내기), 추론 중 인터프리터 해제, 작업 중 담은 사진 누락, 아이 2명일 때 홈 생일 카드가 아이를 하나 더 만듦.

## 2026-11-22 (사용자 피드백: 맡기기 멈춤·설정·업데이트·테마)

- **피드백**: "베베컵에게 맡기기"가 "사진을 살펴보다 멈췄어요"만 보이고 멈춤 / 설정이 너무 적고 얼굴 인식(ML Kit) 설정이 없음 / 앱이 전반적으로 제 기능을 하는지 / 다른 앱처럼 자동 업데이트 확인 / 테마가 어두움.
- **원인(맡기기)**: Play 배포본과 같은 R8 릴리스 빌드를 에뮬레이터에 깔아 재현. `FaceDetection.getClient()`가 NPE — R8 full mode(AGP 9)가 매니페스트 메타데이터로 리플렉션 생성되는 ML Kit `ComponentRegistrar`(`FaceRegistrar`·`VisionCommonRegistrar`·`CommonComponentRegistrar`)의 기본 생성자를 지움. 디버그 빌드는 축소하지 않아 단위·계측 테스트에 안 걸림. v0.7.1부터 v0.10.0까지 릴리스 빌드 전부(번들형 ML Kit을 쓰는 v0.10.0 GitHub APK 포함).
- **변경**: `proguard-rules.pro`에 `ComponentRegistrar` 구현체의 `<init>()` keep, CI가 두 채널 `seeds.txt`로 세 생성자를 확인. 테마는 밝게 기본 + 설정에서 선택(`ThemeMode`, container·surfaceContainer 색 지정, 시스템 바 아이콘 색 연동). 설정에 화면 테마·얼굴 인식 기능(Play: 상태·지금 받기, GitHub: 내장)·앱 업데이트(상태·업데이트 확인) 카드. GitHub 빌드에 앱 내 업데이트(`GithubUpdates`: `update.json` 확인 → APK 받기 → 크기·SHA-256 확인 → Android 설치 화면, Markleaf·Homebase와 같은 방식)를 `src/github`에만 넣고, 릴리스 워크플로가 `update.json`을 붙임(`DECISIONS.md`).
- **진행 중 겹침**: 작업 중 다른 세션이 v0.9.0·v0.10.0(채널 분리)을 내서, 처음 만든 flavor 분리는 버리고 main 구조(`UpdateChannel`, flavor별 `FaceModelInstaller`) 위에 다시 얹음.
- **검증**: R8 릴리스 빌드(디버그 키 재서명)를 에뮬레이터에서 확인 — GitHub 빌드로 맡기기→보관→월드컵→인화함 전체 흐름, Play 빌드는 NPE 없음(모델 받기는 에뮬레이터에 Play 계정이 없어 미확인). 세부는 PR #23.
- **자동 업데이트 보강**: 사용자 요청으로 포너디·Homebase의 업데이터를 따라 12시간 백그라운드 확인(WorkManager)·버전당 한 번 알림·`PackageInstaller` 세션 설치(Android 12+ 앱을 쓰지 않을 때 확인 없이)·설치 뒤 "업데이트했어요" 알림·받은 APK의 패키지/버전 확인·서명 키 사전 확인을 넣음. 에뮬레이터(API 36·29)에서 디버그 훅으로 확인: 화면에서 설치, 백그라운드 무확인 설치, 앱이 보일 때는 알림만, API 29 확인 화면·취소 후 재시도.
- **릴리스**: v0.11.0(versionCode 12). `update.json`을 붙이는 첫 릴리스라, v0.10.0 GitHub 앱 사용자는 이번만 직접 받아 설치해야 하고 v0.12.0부터 앱 안에서 업데이트됨.

## 2026-11-22 (하드닝 #18: 이달의 월드컵)

- **같은 달 중복 기록**: 기록을 지우지 않고 표시로 해결. `MonthlyCup.titleHolders`가 달마다 가장 나중 기록만 "N월의 사진"으로 고르고, 우승 전당은 이전 기록을 일반 우승("👑 N강 우승")으로 보여 줌. 다시 한 달도 계속 "한 달"로 셈(홈 카드 안 뜸).
- **날짜 고정 테스트**: `MonthlyCup.clock`(기본 기기 달력)을 두고 뷰모델·워커가 모두 `MonthlyCup.today()`로 읽게 함. `MonthlyCupFlowTest`는 2026-11-21로 고정.
- **형식이 틀린 날짜**: 확인 결과 Kotlin `toIntOrNull`이 유니코드 숫자(아랍·페르시아 숫자)를 읽어 예전 로케일 날짜도 달을 찾음. v0.10.0부터 새 날짜는 `Locale.ROOT`. 테스트로 고정.
- **알림 우선순위**: `MonthlyCupReminderTest`(work-testing): '모든 사진' 접근 없이도 달마다 한 번 알림, 그날은 정리 알림 경로를 타지 않음, 꺼져 있으면 없음.
- **이월**: 실기기 확인(에뮬레이터를 다른 프로젝트가 쓰는 중) — #18에 남김.

## 2026-11-22 (하드닝 이슈 묶음: #3·#8·#11·#14·#15)

- **채널 분리(#3·#8·#11)**: `play`/`github` flavor. GitHub용 새 서명 키를 만들어 로컬 `.keystore/`와 드라이브에 백업(바이트 비교), GitHub Secrets `BEBECUP_GITHUB_*` 4개 등록. Play 업로드 키는 그대로. GitHub 빌드는 번들형 ML Kit, Play 빌드는 Play 서비스 모델 + 업데이트 배너. 릴리스 워크플로가 두 산출물의 인증서 SHA-256을 확인함(`DECISIONS.md`).
- **업데이트 배너(#11)**: 닫기(versionCode별, prefs), 마지막 확인 시각·제공 버전을 prefs에 저장해 콜드 스타트마다 Play에 묻지 않음, 확인 실패를 연속 횟수와 함께 `Log.w`로.
- **사진 데이터(#3·#8)**: 정리 저장 시 보관하지 않은 스캔 행 삭제(분석 행은 FK cascade), 지워진 사진(FileNotFoundException만) 알림·정리, 추천 사유를 이번 스캔의 숏리스트 규칙으로 범위 지정, `CURRENT_ANALYSIS_VERSION` 3 + 옛 버전 분석 행 삭제.
- **날짜(#14)**: 스캔 사진도 EXIF 촬영일 우선, 날짜 문자열 `Locale.ROOT`, 날짜 보정 대상을 제목 대신 보관함·인화함 전체로(새 prefs 키로 한 번 더 실행), 생일 선택을 Material 3 DatePicker로.
- **월드컵 복원(#3)**: `TournamentEngine.Snapshot`을 사진 id 배열로 `SavedStateHandle`에 저장, 프로세스 종료 후 같은 경기로 복원. 플레이 화면을 떠나면 버림.
- **빌드 경고(#3·#8)**: Room `fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)`, AGP `assets.directories`, 어노테이션 대상 경고. Play AAB 네이티브 기호(`debugSymbolLevel`)는 넣어 봤으나 유일한 네이티브 라이브러리(`libandroidx.graphics.path.so`)가 기호 없이 배포돼 AAB에 아무것도 들어가지 않음을 v0.10.0 AAB로 확인하고 설정을 뺐다(Play 경고는 남음).
- **개인정보(#8·#15)**: ML Kit 지표 전송을 끄지 않고 방침·README·랜딩·설정 문구를 사실대로 고침(`DECISIONS.md`). Play 데이터 보안 양식은 사용자가 고칠 몫.
- **이월**: 형제자매(다자녀) 지원, 넛지의 아기 사진 판별 → `BACKLOG.md`.
- **릴리스**: v0.10.0(versionCode 11)으로 GitHub 릴리스와 Play 비공개 테스트 - Alpha에 함께 올림. 채널 분리 후 첫 릴리스라 GitHub APK는 새 키로 서명됨.
- **검증**: PR에 기록.
## 2026-11-21 (이달의 월드컵)

- **배경**: 벤치마크 1순위 두 번째. 경쟁 앱이 사람을 다시 오게 하는 장치는 매달 나오는 결과물인데, 베베컵의 넛지는 장수 기준뿐이었음.
- **변경**: `domain/monthly/MonthlyCup`(사진 `takenDate`의 "yyyy-MM"으로 달을 나누고, 지난달만 제안 — 사진 2장 이상, 아직 안 했고 건너뛰지 않은 달), 홈 카드(열기/건너뛰기), 월드컵 준비 화면의 달 선택, 우승 화면 "10월의 사진!", 우승 전당 "2026년 10월의 사진". `tournament_records.monthKey` 열 추가(DB v5, `MIGRATION_4_5`). 알림 워커는 정리 알림보다 먼저 이달의 월드컵을 달마다 한 번 알림(같은 날 두 개 보내지 않음, 새 채널 `monthly_cup`).
- **함께 고침**(Codex 리뷰): 알림 켜기가 '모든 사진' 접근까지 요구해 '선택한 사진만' 사용자는 이달의 월드컵 알림도 못 켜던 문제. `NudgeOptIn`이 알림 권한만 확인하고, 쌓인 사진 알림은 워커가 접근 범위를 보고 스스로 건너뜀.
- **검증**: PR에 기록.
- **릴리스**: v0.9.0(versionCode 10)으로 GitHub 릴리스와 Play 비공개 테스트 - Alpha에 함께 올림.

## 2026-11-21 (육아 앱 벤치마크 → 아기 정보·월령 라벨)

- **조사**: 국내외 육아·아기 사진 앱 19개를 비교해 `docs/PARENTING_APP_BENCHMARK.md`에 정리. 경쟁 앱 대부분이 아이 나이(월령) 기준 정리를 기본으로 하는데 베베컵엔 생일 정보가 없었음 → 1순위로 아기 정보와 월령 라벨.
- **변경**: `domain/profile/BabyAge`(java.time 없이 날짜 계산 — minSdk 24, 생일 = D+1, 만 개월은 짧은 달 말일에 채움), `BebecupPrefs`에 이름·생일(한 아이만, 기기 저장), 설정 "아기 정보" 카드와 홈 생일 요청 카드(보관함에 사진이 있을 때만, "다음에"로 숨김), 보관함·월드컵·우승·인화함 월령 표시.
- **함께 고침**: 사진첩에서 직접 담은 사진이 `takenDate`=담은 날로 저장되던 문제. `PhotoMetadataReader.readTakenDate`(EXIF DateTimeOriginal → DATE_TAKEN)로 읽고, 기존 "직접 고른 사진"은 앱 시작 때 한 번 다시 읽음(`pickedPhotoDatesFixed`).
- **개인정보처리방침**: 아기 이름·생일의 기기 저장과 Google 백업 포함 가능성을 추가.
- **검증**: PR에 기록.
- **릴리스**: v0.8.0(versionCode 9)으로 GitHub 릴리스와 Play 비공개 테스트 - Alpha에 함께 올림.

## 2026-11-21 (Play 업데이트 배너)

- **문제**: Play 비공개 테스트 테스터가 Play 자동 업데이트를 꺼 두면 새 버전이 나와도 모름.
- **변경**: Play In-App Updates API(`com.google.android.play:app-update`)로 Play 앱에 새 버전이 있는지 묻고(`update/StoreUpdates`), 있으면 홈 맨 위에 배너를 띄워 Play 스토어 페이지를 연다(Claude Usage 앱과 같은 방식). 홈이 앞에 올 때 최대 6시간에 한 번 묻는다. 디버그 빌드는 `--ei debug_fake_update <versionCode>`로 배너를 띄워 볼 수 있다.
- **검증**: PR에 기록.
- **릴리스**: v0.7.2(versionCode 8)로 GitHub 릴리스와 Play 비공개 테스트 - Alpha에 함께 올림. 실제 Play 설치본에서 배너 → Play 스토어 흐름은 다음 Alpha 버전이 나올 때 처음 확인할 수 있음.

## 2026-11-21 (v0.7.1 앱 용량 다이어트)

- **문제**: v0.7.0 Play AAB가 약 21.5MB. 그중 번들형 ML Kit 얼굴 인식 네이티브 라이브러리(4개 ABI, 약 13MB)와 모델(약 3MB)이 대부분이었음. 다른 앱(4~9MB)과 달리 Chrome 확장 업로드 한도(10MB)를 넘었음.
- **변경**: `com.google.mlkit:face-detection` → `com.google.android.gms:play-services-mlkit-face-detection`(비번들). `ai/FaceModelInstaller`가 Play 서비스 `ModuleInstallClient`로 모델을 받음 — 온보딩 완료 시 백그라운드 요청, 정리 시작 시 없으면 진행률과 함께 받고, 못 받으면 빈 결과 대신 이유를 안내하고 멈춤(`DECISIONS.md`).
- **검증**: PR에 기록.

## 2026-11-21 (Play Alpha 업로드 자동화)

- **문제**: 태그 CI가 만든 22MB AAB를 Chrome 확장(10MB 제한)으로 올릴 수 없어 v0.7.0(vc6) Alpha 업로드를 사용자가 직접 끌어다 놓아야 했음.
- **결정**: gradle-play-publisher·fastlane supply·서드파티 Action·직접 API 스크립트를 비교해 직접 API 스크립트를 택함(`DECISIONS.md`).
- **구현**: `scripts/play_upload.py`(edit → AAB → mapping → alpha 트랙 → commit, 실패 시 edit 삭제, 기본 draft, production은 `--allow-production` 필요), 릴리스 워크플로 `play-alpha` 작업(시크릿 `PLAY_SERVICE_ACCOUNT_JSON` 없으면 경고 후 건너뜀), 로컬 재시도 `scripts/play-upload.sh`.
- **검증**: 단위 테스트 8건(가짜 세션), 실제 google-auth/requests로 로컬 가짜 Play API 서버에 토큰 발급부터 commit까지 끝까지 업로드, `scripts/play-upload.sh 0.7.0 --dry-run`(v0.7.0 CI 산출물 받기·서명 지문·mapping 받기 포함). 실제 Play API 호출은 서비스 계정 키가 없어 확인하지 못함.

## 2026-11-20 (v0.7.0 여정 재구성)

- **컨셉 점검**: "쌓이면 넛지 → 정리 → 월드컵 → 나중에 인화" 기준으로 보니 넛지는 버튼으로 띄우는 가짜 배너, 승인 결과는 메모리에만 있어 재시작 시 소실, 인화는 가짜 진행 화면 뒤 찍스 홈만 여는 상태였음.
- **재구성**: `BebecupPrefs`(마지막 정리 시점), `NudgePolicy`/`NudgeWorker`(옵트인 일일 점검), 보관함(`BestBoxScreen`), `TournamentEngine`, `PrintShare`(FileProvider 공유)로 여정을 다시 짬. 단일 컬럼 업데이트 DAO 쿼리로 오래된 `BabyPhoto` 복사본 덮어쓰기를 없앰.
- **분석 버그**: EXIF 회전 반영(분석 버전 2), 흔들림 제외 토글 연결(`ShortlistRanking`), 자는 아기 설정 변경 시 분석 캐시 무효화, 픽 URI 영구 권한.
- **그림**: Codex(image_gen)로 마스코트 일관 일러스트 9장 생성 → 6장 앱 적용(WebP, drawable-nodpi), 아이콘·스토어 아이콘 교체. 피처 그래픽은 생성만 하고 미적용.
- **환경**: 이 PC의 Gradle 9.3.1이 모든 Kotlin DSL settings 스크립트를 컴파일하지 못해(캐시 정리·JDK 교체로도 불가) wrapper를 9.5.1로 올림. 메모리 압박으로 Gradle 데몬이 죽어 `-Xmx1536m`, workers 1로 빌드함.
- **검증**: 단위 테스트(신규 `TournamentEngineTest`, `NudgeAndRankingTest`, DAO 테스트, Robolectric `JourneyFlowTest` 화면 캡처) 통과. 실기기 검증 결과는 PR에 기록.

## 2026-05-24 (Play Console 최초 등록 준비)

- **릴리즈 키스토어 생성**: 기존 release keystore가 없어 `.keystore/bebecup-release.p12`를 PKCS12/RSA 4096bit로 생성하고, `release-signing.properties` 및 `.keystore/release-signing-backup.properties`에 서명 값을 저장함.
- **비밀 파일 보호**: `.keystore/`를 `.gitignore`에 추가하여 릴리즈 키스토어와 비밀번호 백업이 Git에 포함되지 않도록 처리함.
- **Play Console 등록값 정리**: `docs/PLAY_CONSOLE_INITIAL_REGISTRATION.md`에 최초 앱 생성, 스토어 등록정보, 앱 콘텐츠, 그래픽 자산, 릴리즈 업로드 값을 정리함.
- **스토어 메타데이터 추가**: fastlane `title`, `short_description`, `full_description`을 ko-KR/en-US로 작성함.
- **개인정보처리방침 초안 추가**: `docs/PRIVACY_POLICY_DRAFT.md`에 로컬 사진 URI 저장, 외부 업로드 없음, 광고/분석/추적 SDK 없음 정책을 정리함.

## 2026-05-23 (v0.2.0 릴리즈 작업)

- **데이터베이스 강건화**: `BabyPhotoDao`에 `pruneMockPhotos` 쿼리를 신설하고, `BabyCupViewModel` 기동 시 비정상/목업 URI 데이터를 자동 안전 소거하도록 처리함.
- **CI/CD 파이프라인 수립**: `markleaf-android` 프로젝트에서 `android-build.yml` 파일을 이식하여 테스트, 린트, APK/AAB 서명/비서명 빌드 및 Tag push 시 릴리즈 자동 생성 액션을 통합함.
- **버전 2차 범프**: `app/build.gradle.kts`의 `versionCode = 2`, `versionName = "0.2.0"`으로 올리고, 다국어 fastlane changelog `2.txt`를 신설함.
- **산출물 내보내기 및 검증**: `./gradlew :app:exportReleaseToDesktop`을 정상 구동하여 바탕화면에 AAB 및 통합 릴리즈 노트 TXT가 올바르게 내보내짐을 로컬 검증함.

## 2026-05-23

- `D:\Project\markleaf-android`의 release artifact export 규칙을 베베컵에 맞게 이식했다.
- `applicationId`를 `com.bebecup.app`으로 설정하고 release signing 값을 선택적으로 읽도록 변경했다.
- 첫 실행 목업 사진 주입을 제거하고, 사진 등록을 실제 Photo Picker URI 기반으로 정리했다.
- Play Console 제출용 AAB와 bilingual release-notes TXT를 바탕화면에 내보내는 `:app:exportReleaseToDesktop` task를 추가했다.
