# Bebecup AI Curation Pivot — 작업계획 (Work Plan)

> 근거 설계서: [BEBECUP_AI_CURATION_PIVOT_SPEC.md](BEBECUP_AI_CURATION_PIVOT_SPEC.md)
> 작성일: 2026-05-29
> 범위: Phase 1~6 전체 로드맵 / 온디바이스 얼굴·눈 감지는 **ML Kit Face Detection**
> 현재 버전: 0.2.0 (versionCode 2) → 목표 1.0.0

---

## 0. 현재 코드 ↔ 설계서 갭 (Gap Analysis)

작업계획의 출발점. 설계서가 가정한 "monolithic flow"가 실제로 어떤 상태인지 확인됨.

| 영역 | 현재 상태 | 설계서 목표 | 작업 성격 |
|------|-----------|-------------|-----------|
| UI 구조 | `MainActivity.kt` **1,754줄** 단일 파일에 전 화면 | feature별 `ui/*` 분리 | 분해(Phase 1) |
| ViewModel | `BabyCupViewModel` 328줄, 모든 상태/액션 집중 | feature ViewModel + UseCase | 점진 분리 |
| DB 버전 | Room v2 + `fallbackToDestructiveMigration()` | v3 + 명시적 `Migration(2,3)` | 마이그레이션 정책 변경(Phase 2) |
| `BabyPhoto` PK | `Int` autoGenerate, `takenDate`는 **String("yyyy-MM-dd")** | `Long` PK, millis 기반 + AI 필드 분리 | 엔티티 재설계 + 변환 |
| 분석 캐시 | 없음 | `PhotoAnalysisEntity` / `Cluster` / `Session` | 신규(Phase 2) |
| MediaStore 스캔 | 없음 (Photo Picker만) | `MediaStorePhotoSource` 날짜 범위 스캔 | 신규(Phase 2) |
| CV/AI | 없음 | 블러·얼굴·눈·중복·스코어 cascade | 신규(Phase 3,5) |
| ML Kit | build.gradle camera 주석처리, ML Kit 미포함 | ML Kit Face Detection | 의존성 추가(Phase 3) |
| 권한 | AndroidManifest **권한 0개** | `READ_MEDIA_IMAGES` + selected access | 신규(Phase 2) |
| 테스트 | placeholder + Roborazzi 1개 | "기존 테스트 통과" 안전망 | 보강 필요(Phase 1 선행) |
| 로컬 VLM | 없음 | `LocalVisionExplainer` 추상화 | 신규(Phase 6) |

### 핵심 리스크 3가지 (계획 전체에 영향)
1. **데이터 마이그레이션**: `BabyPhoto.id: Int` → `Long`, `takenDate: String` → `takenAtMillis: Long` 변환은 단순 컬럼 추가가 아니라 테이블 재작성. 기존 출시 사용자 데이터 보존이 필요하면 `Migration(2,3)`에서 SQL 변환을 직접 작성해야 함.
2. **테스트 안전망 부재**: Phase 1 리팩터링의 "동작 불변" 보장 근거가 거의 없음. → Phase 1을 **테스트 보강부터** 시작.
3. **단일 Activity AnimatedContent 라우팅**: `viewModel.currentScreen` 기반. Navigation Compose 도입은 선택이지만, Phase 1에서 라우팅 추상화를 어디까지 건드릴지 결정 필요(아래 Phase 1 참조).

---

## 1. 전체 로드맵 (Phase ↔ 버전)

| Phase | 목표 | 산출 버전 | 핵심 위험 |
|-------|------|-----------|-----------|
| **1** | AI-ready 리팩터링 (동작 불변) | 0.3.0 | 회귀, 테스트 부재 |
| **2** | MediaStore 스캔 + 분석 캐시 + 명시 마이그레이션 | 0.3.0 | 데이터 마이그레이션, 권한 friction |
| **3** | 기본 CV 스코어링 (블러·얼굴·눈·노출 + 룰 기반 사유) | 0.4.0 | ML Kit 통합, 기기 성능 |
| **4** | AI Review UX (부모 승인/거부/오버라이드 + AI 숏리스트→월드컵) | 0.4.0 | UX 신뢰, 카피 톤 |
| **5** | 중복/버스트 그룹핑 (pHash + 시간창) | 0.5.0 | 클러스터 대표 선정 정확도 |
| **6** | 선택적 로컬 VLM 설명 | 1.0.0 | 모델 크기/가용성 |

각 Phase 종료 시 **컴파일 + 기존 테스트 통과 + 수동 스모크**를 게이트로 둠.

---

## 2. Phase별 상세

### Phase 0 (선행, 0.5일) — 테스트 안전망 + 패키지 골격
리팩터링 전에 회귀를 잡을 최소 안전망.
- [ ] `BabyCupViewModel`의 토너먼트 브래킷 로직(`startWorldCup`/`submitMatchVote`/`progressToNextRound`)에 대한 순수 단위 테스트 추가 — 리팩터링 회귀 감지용.
- [ ] 스코어 정책 등 순수 로직을 테스트 가능하게 분리할 자리 마련.
- [ ] 빈 패키지 골격 생성: `ai/`, `data/{local,media,repository}`, `domain/{model,usecase}`, `worker/`, `ui/{dashboard,ai,tournament,favorites,print,settings}`.

### Phase 1 (2~3일) — AI-ready 리팩터링, 동작 불변
**설계서 Goal 1.** `MainActivity.kt` 1,754줄 분해.

분해 대상(현재 줄 범위 → 목표 파일):
- `DashboardView` (150–516) → `ui/dashboard/DashboardScreen.kt`
- `PhotoManagerView` (518–692) → `ui/photo/PhotoManagerScreen.kt`
- `BestShotSelectorView` (694–795) → `ui/photo/BestShotSelectorScreen.kt`
- `WorldCupSetupView` (797–907) → `ui/tournament/WorldCupSetupScreen.kt`
- `WorldCupPlayView` (909–1110) → `ui/tournament/WorldCupPlayScreen.kt`
- `WorldCupWinnerCelebrationView` (1112–1263) → `ui/tournament/WorldCupWinnerScreen.kt`
- `PrintCartView` (1265–1507) → `ui/print/PrintCartScreen.kt`
- `TournamentHistoryListView` (1509–1603) → `ui/history/TournamentHistoryScreen.kt`
- `ZzixxTransferingOverlay` + `ZzixxSuccessDialog` (1605–1754) → `ui/print/ZzixxHandoff.kt`
- `UiScreen` sealed interface → `ui/navigation/UiScreen.kt` (설계서 5.1 화면 추가는 Phase 4에서)

원칙:
- **동작·픽셀 불변.** 각 화면을 그대로 옮기고 시그니처만 정리. 새 기능 0.
- `BabyCupViewModel`은 살려두되 상태/액션을 영역별로 그룹핑(주석 섹션 또는 내부 분리)해 후속 분리 준비.
- `AnimatedContent` 라우팅은 이번엔 **유지**(Navigation Compose 도입은 Phase 4 화면 추가 시 재평가). 라우팅 추상화 변경은 회귀 위험이 커서 분해와 동시에 하지 않음.
- 게이트: `./gradlew assembleDebug` 통과 + Phase 0 테스트 통과 + Roborazzi 스냅샷 갱신/확인.

### Phase 2 (3~4일) — 스캔 + 분석 캐시 데이터 기반
**설계서 Goal 2 + 8.2.**
- [ ] 권한: `READ_MEDIA_IMAGES`(API 33+), 14+ selected-photo 대응, 13 미만 fallback. 권한 설명 UX(설계서 9.2).
- [ ] `data/media/MediaStorePhotoSource.kt`: `DATE_TAKEN`/`DATE_ADDED`/dimensions/URI/MIME 기준 날짜 범위 스캔(1/7/30/커스텀, 기본 7일, 스크린샷 제외).
- [ ] `data/media/PhotoMetadataReader.kt` + 안전한 썸네일 디코드(분석용 512px / 임베딩 224px).
- [ ] 엔티티 신규: `PhotoAnalysisEntity`, `PhotoClusterEntity`, `AiCurationSessionEntity` (설계서 7.2~7.4).
- [ ] `BabyPhotoEntity` 재설계 결정 — **권장: 새 `Long` PK 엔티티로 이행**하되, 기존 `BabyPhoto`는 호환 위해 단계적 처리.
- [ ] Room **v2→v3 명시적 마이그레이션**: `fallbackToDestructiveMigration()` 제거, `baby_photos` 보존, AI 테이블 신설, `isAiShortlisted ← isSelectedAsBest` 백필. `exportSchema = true`.
- [ ] DAO: 분석 insert/update/query, 세션, 클러스터 대표. 재스캔 시 미변경 이미지 skip(analysisVersion 기준).
- [ ] 리포지토리 인터페이스: `AiCurationRepository` 등, 분석 데이터 local-only.
- 게이트: 마이그레이션 자동 테스트(v2 DB → v3 오픈, 데이터 보존 확인).

### Phase 3 (4~5일) — 기본 CV 스코어링
**설계서 Goal 3 + 8.4~8.9.**
- [ ] 의존성: **ML Kit Face Detection** 추가(`com.google.mlkit:face-detection`), camera 의존성은 필요 시만.
- [ ] `ai/BlurDetector.kt`: Laplacian variance(얼굴 영역 가중). 임계 0.25/0.45.
- [ ] `ai/FaceQualityAnalyzer.kt`: ML Kit으로 faceCount/box/centerScore/areaRatio/yaw·pitch·roll.
- [ ] `ai/EyeStateAnalyzer.kt`: ML Kit eye-open 확률. "자는 아기 허용" 설정 반영(설계서 8.6).
- [ ] 노출 근사(`exposureScore`) + 표정 라벨(부모 친화, 8.7) + `ai/BabyPhotoScorePolicy.kt`(설계서 16 가중치).
- [ ] 룰 기반 한국어 사유 생성(`buildPositiveReasons`/`buildRejectReasons`, 설계서 16.1).
- [ ] UseCase: `ScanRecentBabyPhotosUseCase`, `AnalyzePhotoQualityUseCase`, `BuildAiShortlistUseCase`. cascade 파이프라인(8.1).
- [ ] 결과를 `PhotoAnalysisEntity`에 캐시, 상위 8/16 숏리스트.
- 게이트: 스코어 정책 단위 테스트(가중치·등급 경계), 샘플 이미지 스모크.

### Phase 4 (3~4일) — AI Review UX
**설계서 5.2, 11.**
- [ ] 화면 신설: `AiScanSetupScreen`(범위 칩+토글), `AiScanProgressScreen`(local-only 카피+5단계), `AiCurationResultScreen`(요약+4섹션), `AiReviewScreen`.
- [ ] `UiScreen`에 신규 화면 추가. 이 시점에 **Navigation Compose 도입 여부 재평가**(화면 수 급증).
- [ ] Dashboard 재설계: primary CTA "이번 주 아기사진 AI 엄선하기" + 카드 4종(설계서 5.2).
- [ ] 포토 카드: 등급칩 S/A/B + 사유칩 + 부모 컨트롤(추천 유지/제외/월드컵 후보 추가).
- [ ] 거부 사진 별도 탭, 삭제 금지, 수동 promote/demote.
- [ ] 월드컵 소스 선택(AI 추천/부모 승인/직접) 기본 "AI 추천". Winner 리포트에 AI매칭 결과(`CompareParentPickWithAiPickUseCase`).
- [ ] WorkManager: 수동 스캔 foreground, opt-in 백그라운드(배터리/저장 제약, 설계서 10).
- 게이트: AI-first 플로우 end-to-end 수동 검증.

### Phase 5 (2~3일) — 중복/버스트 그룹핑
**설계서 8.8.**
- [ ] `ai/DuplicateClusterer.kt`: pHash/dHash + 시간창(3~10초) 그룹핑.
- [ ] 클러스터 대표 선정(스코어 최고 1~2장), 나머지 demote → `duplicatePenalty` 반영.
- [ ] 결과 UI: "비슷한 사진 묶음" 섹션.
- 게이트: 버스트 샘플로 그룹핑 정확도 확인.

### Phase 6 (3~5일) — 선택적 로컬 VLM 설명
**설계서 8.10.**
- [ ] `ai/LocalVisionExplainer.kt` 인터페이스(모델 비종속), `AiModelAvailability.kt`.
- [ ] 상위 N장에만 실행, 한국어 자연어 설명 + 부모/AI 비교 요약.
- [ ] 모델 가용성 화면, VLM 없어도 앱 정상 동작(graceful degrade), 설명 캐시.
- 게이트: 모델 미설치 환경에서 전체 플로우 동작.

---

## 3. 횡단 관심사 (모든 Phase 공통)

- **프라이버시(설계서 9)**: 업로드 금지 기본값, 분석 local-only, ad/tracking SDK 금지, "모든 분석 데이터 삭제" 설정. Phase 4에 Settings 반영.
- **카피 톤(설계서 17)**: "나쁜 사진/실패작" 금지, "아쉬운 후보/추천에서 제외" 사용. 거부 사유는 부드럽게.
- **성능(설계서 15.2)**: 썸네일만 디코드, IO 디스패처, 상위 후보만 무거운 분석, 전 결과 캐시.
- **버전/스토어(설계서 18)**: Phase별 버전 범프. 제목 "베베컵 - AI 아기사진 엄선 & 가족 월드컵" 전환은 Phase 4 출시 시.

## 4. 의사결정 기록
1. **기존 사용자 데이터 보존** — ✅ 결정(2026-05-29): 개발 단계라 데이터 폐기 허용. `fallbackToDestructiveMigration()` **유지**하며 v2→v3 범프. `Int→Long` PK 변환은 하지 않고 새 분석 엔티티 FK를 기존 `BabyPhoto.id: Int`에 맞춤. 명시적 `Migration(2,3)`는 공개 출시 직전 과제로 `AppDatabase.kt`에 TODO로 박아둠.
2. **Navigation Compose 도입 시점** — Phase 1 유지 / Phase 4 도입 권장.
3. **수익화(설계서 12)** — MVP는 무료 로컬 큐레이션. 프리미엄 분리는 1.0.0 이후로 미룸(현 계획 범위 외).

---

## 5. 진행 현황
- ✅ 계획 문서 작성
- ✅ **Phase 1** 완료: `MainActivity.kt` 1,755→~130줄 분해(8화면+ZZIXX → feature 패키지). 컴파일+기존 테스트 통과.
- ✅ **Phase 2** 완료: `data/ai`(PhotoAnalysis/PhotoCluster/AiCurationSession 엔티티·DAO·`AiCurationRepository`), `data/media`(MediaStorePhotoSource·PhotoMetadataReader), Room v3, 권한(READ_MEDIA_IMAGES 등), Robolectric DAO 테스트 3건 통과. UI 미연결(동작 불변).
- ✅ **Phase 3** 완료: ML Kit Face Detection 의존성. `ai/`에 BlurDetector(Laplacian)·ExposureAnalyzer·EyeStateAnalyzer·ExpressionAnalyzer(순수)·FaceQualityAnalyzer(ML Kit wrapper)·BabyPhotoScorePolicy·PhotoReasonBuilder·PhotoQualityAnalyzer(cascade). UseCase 3종(Scan/Analyze/BuildShortlist). 순수 로직 단위 테스트 11건 통과(스코어 경계·사유·블러·노출·눈·표정). UI 미연결.
- ✅ **Phase 4** 완료: `ui/ai`에 AiScanSetup(범위 칩·토글·런타임 권한·프라이버시/삭제)·AiScanProgress(local-only 카피·진행 카운트)·AiCurationResult(요약 통계·등급/사유 칩·승인/제외·숏리스트→월드컵) 화면. `BabyCupViewModel`에 UseCase 연결 + AI 상태/액션(`startAiCuration`·`toggleShortlistApproval`·`startWorldCupFromShortlist`·`deleteAllAnalysisData`), 브래킷 빌더 추출. Dashboard "AI 엄선" primary CTA. `assembleDebug` 통과(51MB, ML Kit 포함), 테스트 전부 통과. **AI-first 플로우 end-to-end 연결.**
  - 결정: **Navigation Compose 미도입**(기존 `UiScreen`/`AnimatedContent` 유지 — 회귀 위험 회피). 추후 별도 재평가.
  - 이월: 별도 Settings 화면(삭제 액션은 AiScanSetup에 배치), WorkManager 백그라운드 스캔(§10.2), Winner 리포트 AI매칭(`CompareParentPickWithAiPickUseCase`, §11.7), "부모님 확인 필요/비슷한 사진/제외됨" 4섹션 세분화 → 후속.
- ✅ **Phase 5** 완료: `PerceptualHash`(dHash, 순수)·`DuplicateClusterer`(union-find, hamming 임계)·`DuplicatePenaltyPolicy`(순수 점수 재계산). `PhotoAnalysisEntity`에 `dHash` 컬럼(DB v4), 분석 시 캐시. `ClusterDuplicatesUseCase`가 묶음 저장 + 비대표 demote(점수↓·사유 추가). VM이 분석→**클러스터링**→숏리스트 순으로 실행(`aiSimilarOnly` 토글 연동), 결과 화면에 "비슷한 사진 묶음 N개" 표시. 순수 로직 단위 테스트 4건 추가(해시 동일/상이, 클러스터 대표 선정, demote 점수 하락). 전체 21건 통과, APK 빌드 OK.
  - 비고: 설계서의 "시간창(3~10초)" 게이트는 `BabyPhoto`가 day 단위 날짜만 보관해 보류하고, **dHash 시각 유사도 단독**으로 그룹핑(버스트 근접복제 핵심 가치 달성). 캡처 millis 보관 시 시간창 추가 가능.
- ✅ **Phase 6** 완료: `LocalVisionExplainer` 인터페이스 + `RuleBasedVisionExplainer`(모델 비번들 기본), `AiModelAvailability`(graceful degrade), `ExplainTopPhotosUseCase`(상위 N장만 한국어 설명 캐시). 결과 카드에 자연어 설명 표시. 테스트 2건.
- ✅ **상용화 강화** 완료:
  - **데이터 보존 마이그레이션**: `exportSchema=true`(스키마 추적), 명시적 `Migration(2,3)`/`(3,4)`(Room 생성 SQL 그대로), `fallbackToDestructiveMigration` 제거 → 다운그레이드만 fallback. v2→v4 보존+스키마 정합을 Robolectric `MigrationTest`로 실증.
  - **우승 AI매칭(§11.7)**: `CompareParentPickWithAiPickUseCase`, AI 1위 추적, 우승 화면에 일치/불일치 메시지.
  - **제외 사진 섹션(§11.5/§15.3)**: `BuildRejectedListUseCase` + 접이식 "아쉬운 후보" + "삭제되지 않아요" 안내.
  - **Settings 화면**: 스캔 기본값·토글(SharedPreferences 영속), 프라이버시(전체 삭제), 앱 버전. Dashboard 설정 진입.
  - **버전 0.5.0 (vc3)**, Play 스토어 카피(ko/en title·short·full·changelog 3) AI 포지셔닝으로 갱신.
  - **테스트 강화**: `PhotoAnalyzer` 인터페이스 추출, `CurationUseCasesTest`(in-memory Room 통합: 숏리스트/제외/클러스터/설명/분석 스킵). **전체 31건 통과.**
  - **상용화 게이트**: `assembleRelease`(R8 minify, 39.6MB) + `bundleRelease`(AAB 23MB) + `lintDebug` 에러 0. ML Kit/Room keep 규칙 정상.

## 6. 잔여 항목 (실기기/후속)
- **실기기 QA 미수행**: 이 환경엔 디바이스/에뮬레이터가 없어 ML Kit 실제 얼굴 감지·권한 팝업·실 갤러리 스캔·UI 렌더는 코드/단위·통합 테스트까지만 검증됨. Android Studio 또는 `connectedAndroidTest`(에뮬레이터)로 1회 스모크 권장.
- WorkManager 백그라운드 스캔(§10.2 opt-in), 시간창 버스트 게이트(캡처 millis 보관 필요), 로컬 VLM 실제 모델 번들 → 후속.
