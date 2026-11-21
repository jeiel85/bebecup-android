# History

## 2026-11-22 (하드닝 이슈 묶음: #3·#8·#11·#14·#15)

- **채널 분리(#3·#8·#11)**: `play`/`github` flavor. GitHub용 새 서명 키를 만들어 로컬 `.keystore/`와 드라이브에 백업(바이트 비교), GitHub Secrets `BEBECUP_GITHUB_*` 4개 등록. Play 업로드 키는 그대로. GitHub 빌드는 번들형 ML Kit, Play 빌드는 Play 서비스 모델 + 업데이트 배너. 릴리스 워크플로가 두 산출물의 인증서 SHA-256을 확인함(`DECISIONS.md`).
- **업데이트 배너(#11)**: 닫기(versionCode별, prefs), 마지막 확인 시각·제공 버전을 prefs에 저장해 콜드 스타트마다 Play에 묻지 않음, 확인 실패를 연속 횟수와 함께 `Log.w`로.
- **사진 데이터(#3·#8)**: 정리 저장 시 보관하지 않은 스캔 행 삭제(분석 행은 FK cascade), 지워진 사진(FileNotFoundException만) 알림·정리, 추천 사유를 이번 스캔의 숏리스트 규칙으로 범위 지정, `CURRENT_ANALYSIS_VERSION` 3 + 옛 버전 분석 행 삭제.
- **날짜(#14)**: 스캔 사진도 EXIF 촬영일 우선, 날짜 문자열 `Locale.ROOT`, 날짜 보정 대상을 제목 대신 보관함·인화함 전체로(새 prefs 키로 한 번 더 실행), 생일 선택을 Material 3 DatePicker로.
- **월드컵 복원(#3)**: `TournamentEngine.Snapshot`을 사진 id 배열로 `SavedStateHandle`에 저장, 프로세스 종료 후 같은 경기로 복원. 플레이 화면을 떠나면 버림.
- **빌드 경고(#3·#8)**: Room `fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)`, AGP `assets.directories`, 어노테이션 대상 경고, Play AAB에 네이티브 기호 표(`debugSymbolLevel`).
- **개인정보(#8·#15)**: ML Kit 지표 전송을 끄지 않고 방침·README·랜딩·설정 문구를 사실대로 고침(`DECISIONS.md`). Play 데이터 보안 양식은 사용자가 고칠 몫.
- **이월**: 형제자매(다자녀) 지원, 넛지의 아기 사진 판별 → `BACKLOG.md`.
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
