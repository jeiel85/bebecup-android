# Bebecup AI Curation Pivot Design Spec

> File: `docs/BEBECUP_AI_CURATION_PIVOT_SPEC.md`  
> Target repo: `jeiel85/bebecup-android`  
> Target app: Bebecup Android  
> Direction: Manual baby-photo tournament app → AI-first baby-photo curation and family decision app  
> Draft date: 2026-05-29

---

## 0. Executive Summary

Bebecup should pivot from:

> “Parents manually choose baby photos and run a family photo tournament.”

to:

> “Bebecup quietly scans recent baby photos on-device, removes failed shots, recommends the best moments, and lets parents confirm, compare, and celebrate the final choice.”

The existing tournament concept should not be discarded. It should become the **emotional decision layer** after AI curation.

### New product sentence

**Bebecup is an on-device baby-photo curation app that finds the best baby photos from a parent’s gallery, explains why they are good or poor, and turns the final favorites into a family-friendly tournament and print-ready shortlist.**

---

## 1. Existing App Baseline

The current app is already a good base for this pivot because it has:

- Android / Kotlin / Jetpack Compose app structure.
- Room-based local data persistence.
- Photo candidate management.
- Best-shot selection.
- World Cup tournament flow.
- Winner history.
- Print-cart style flow.
- ZZIXX handoff simulation.
- No hard dependency on remote photo analysis.

### Current conceptual limitation

The existing concept assumes:

1. The parent already knows which photos are good.
2. The parent manually imports or selects candidate photos.
3. The tournament starts only after enough photos are manually curated.

This causes friction because the real user problem is earlier:

> Parents have too many baby photos and do not want to inspect hundreds of near-duplicates, blurry shots, closed-eye shots, and awkward expressions.

### New conceptual foundation

The new design should move the product’s value upstream:

1. Bebecup detects recent photo candidates.
2. AI/CV ranks them.
3. The parent reviews only a small shortlist.
4. The tournament becomes the fun final confirmation step.
5. Print/export becomes an optional outcome.

---

## 2. Product Pivot

## 2.1 Old Flow

```text
Photo Picker
  → Manual photo registration
  → Manual best-shot selection
  → Tournament setup
  → Tournament play
  → Winner
  → Print cart
```

## 2.2 New Flow

```text
Gallery permission / Photo Picker scope
  → Recent photo scan
  → On-device quality analysis
  → AI shortlist
  → Parent review
  → Optional tournament
  → Final favorites
  → Print/export/handoff
```

## 2.3 Core UX Promise

Bebecup should save parents from this:

> “Which one of these 80 similar baby photos is actually the best?”

Bebecup should answer:

> “These 12 are worth keeping. These 3 are the strongest. This one is the best candidate because the face is clear, the expression is natural, and the moment feels warm.”

---

## 3. Feature Scope

## 3.1 MVP Scope

The first AI-centered release should focus only on **photo reading / quality curation**, not photo editing.

### Include

- Recent gallery scan.
- Local-only analysis.
- Blur detection.
- Face detection.
- Eye-open / closed-eye detection.
- Baby-face-centered scoring.
- Duplicate / near-duplicate grouping.
- AI shortlist generation.
- Parent approval screen.
- Tournament generated from approved AI shortlist.
- Winner report with AI reason.

### Exclude for MVP

- Generative photo editing.
- Face retouching.
- Background removal.
- Cloud VLM inference.
- Direct POD order API.
- Payment.
- Shipping address collection.
- Photo upload to external server.

### Reason

The biggest user value is not editing. It is **finding good photos quickly**.

---

## 4. Target User Stories

## 4.1 Parent with too many similar photos

**As a parent**, I want Bebecup to find the best photos from a burst of similar baby photos so that I do not need to compare all of them manually.

Acceptance criteria:

- Similar photos are grouped.
- The app recommends one representative per group.
- Blurry or awkward shots are demoted.
- The user can still override the AI choice.

## 4.2 Parent checking this week’s memories

**As a parent**, I want Bebecup to scan recent photos from the last 7 days so that I can quickly pick this week’s best baby moments.

Acceptance criteria:

- User can choose scan range: 1 day / 7 days / 30 days / custom.
- App shows scan progress.
- App stores analysis results locally.
- Re-scanning does not reprocess unchanged images.

## 4.3 Parent who distrusts AI

**As a parent**, I want to see why a photo was recommended or rejected so that I can decide whether to trust the result.

Acceptance criteria:

- Each recommended photo shows simple reasons.
- Rejected photos are not deleted.
- User can manually promote or demote any photo.
- The final decision always belongs to the parent.

## 4.4 Family tournament experience

**As a family**, we want to run a fun tournament from AI-shortlisted photos so that choosing the best photo feels like a shared memory game.

Acceptance criteria:

- Tournament can start directly from AI shortlist.
- AI pick and parent pick can be compared after the tournament.
- Final report shows match result: AI matched / AI differed.

---

## 5. New App Information Architecture

## 5.1 Main Navigation

Recommended top-level screens:

```kotlin
sealed interface UiScreen {
    object Dashboard : UiScreen
    object AiScanSetup : UiScreen
    object AiScanProgress : UiScreen
    object AiCurationResult : UiScreen
    object AiReview : UiScreen
    object WorldCupSetup : UiScreen
    object WorldCupPlay : UiScreen
    data class WorldCupWinner(...) : UiScreen
    object Favorites : UiScreen
    object PrintExport : UiScreen
    object History : UiScreen
    object Settings : UiScreen
}
```

## 5.2 Dashboard Redesign

Dashboard should shift from “start tournament” to “start AI curation”.

### Primary CTA

```text
이번 주 아기사진 AI 엄선하기
```

### Secondary CTA

```text
직접 사진 골라서 월드컵 시작하기
```

### Dashboard cards

1. **This Week AI Pick**
   - “최근 7일 사진 중 추천 후보 12장”
   - Shows top 3 thumbnails.

2. **Needs Review**
   - “AI가 고른 사진을 부모님이 확인해주세요”

3. **Family World Cup**
   - “엄선된 사진으로 이상형 월드컵 시작”

4. **Print-ready Favorites**
   - “인화 후보 4장”

---

## 6. Recommended Architecture

## 6.1 Architectural Direction

Do not place AI logic inside `MainActivity` or `BabyCupViewModel`.

The current app appears to have a large single-activity Compose structure. The new AI features will become difficult to maintain if added directly to the existing monolithic flow.

Recommended direction:

```text
UI layer
  Compose screens
  ViewModels

Domain layer
  Use cases
  Scoring policy
  Tournament policy

Data layer
  Room DAO
  MediaStore source
  Local model cache
  Repository implementations

AI/CV layer
  Blur detector
  Face detector
  Eye detector
  Duplicate detector
  Optional local VLM explainer
```

## 6.2 New Package Structure

```text
com.bebecup.app
│
├── ai
│   ├── BlurDetector.kt
│   ├── FaceQualityAnalyzer.kt
│   ├── EyeStateAnalyzer.kt
│   ├── DuplicateClusterer.kt
│   ├── AestheticScorer.kt
│   ├── BabyPhotoScorePolicy.kt
│   ├── LocalVisionExplainer.kt
│   └── AiModelAvailability.kt
│
├── data
│   ├── local
│   │   ├── AppDatabase.kt
│   │   ├── entity
│   │   │   ├── BabyPhotoEntity.kt
│   │   │   ├── PhotoAnalysisEntity.kt
│   │   │   ├── PhotoClusterEntity.kt
│   │   │   └── TournamentRecordEntity.kt
│   │   └── dao
│   │       ├── BabyPhotoDao.kt
│   │       ├── PhotoAnalysisDao.kt
│   │       └── TournamentDao.kt
│   │
│   ├── media
│   │   ├── MediaStorePhotoSource.kt
│   │   └── PhotoMetadataReader.kt
│   │
│   └── repository
│       ├── BabyPhotoRepository.kt
│       ├── AiCurationRepository.kt
│       └── TournamentRepository.kt
│
├── domain
│   ├── model
│   │   ├── BabyPhoto.kt
│   │   ├── PhotoAnalysis.kt
│   │   ├── PhotoQualityGrade.kt
│   │   └── AiCurationSession.kt
│   │
│   └── usecase
│       ├── ScanRecentBabyPhotosUseCase.kt
│       ├── AnalyzePhotoQualityUseCase.kt
│       ├── BuildAiShortlistUseCase.kt
│       ├── BuildTournamentBracketUseCase.kt
│       └── CompareParentPickWithAiPickUseCase.kt
│
├── worker
│   ├── AiCurationWorker.kt
│   └── AiModelWarmupWorker.kt
│
└── ui
    ├── dashboard
    ├── ai
    │   ├── AiScanSetupScreen.kt
    │   ├── AiScanProgressScreen.kt
    │   ├── AiCurationResultScreen.kt
    │   └── AiReviewScreen.kt
    ├── tournament
    ├── favorites
    ├── print
    └── settings
```

---

## 7. Data Model Redesign

## 7.1 Keep `BabyPhoto`, but reduce it to photo identity and user-facing state

Current `BabyPhoto` mixes several concerns:

- Image identity.
- Best-shot state.
- Tournament stats.
- Print-cart state.

For the AI pivot, quality analysis should be separated.

### Proposed `BabyPhotoEntity`

```kotlin
@Entity(tableName = "baby_photos")
data class BabyPhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val uriString: String,
    val mediaStoreId: Long? = null,

    val displayName: String? = null,
    val takenAtMillis: Long? = null,
    val addedAtMillis: Long? = null,

    val width: Int? = null,
    val height: Int? = null,

    val isUserFavorite: Boolean = false,
    val isAiShortlisted: Boolean = false,
    val isHiddenFromSuggestions: Boolean = false,

    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)
```

## 7.2 Add `PhotoAnalysisEntity`

This should cache all AI/CV results so that gallery scans remain fast.

```kotlin
@Entity(
    tableName = "photo_analysis",
    foreignKeys = [
        ForeignKey(
            entity = BabyPhotoEntity::class,
            parentColumns = ["id"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("photoId"),
        Index("overallScore"),
        Index("analysisVersion")
    ]
)
data class PhotoAnalysisEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val photoId: Long,
    val analysisVersion: Int,

    val blurScore: Float,
    val faceDetected: Boolean,
    val faceCount: Int,
    val faceCenterScore: Float,
    val eyeOpenScore: Float,
    val expressionScore: Float,
    val exposureScore: Float,
    val compositionScore: Float,
    val duplicatePenalty: Float,

    val overallScore: Float,
    val qualityGrade: String,

    val rejectReasonsJson: String,
    val positiveReasonsJson: String,
    val aiReasonKo: String? = null,

    val analyzedAtMillis: Long = System.currentTimeMillis()
)
```

## 7.3 Add `PhotoClusterEntity`

Used for burst / near-duplicate grouping.

```kotlin
@Entity(
    tableName = "photo_clusters",
    indices = [Index("clusterKey")]
)
data class PhotoClusterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val clusterKey: String,
    val representativePhotoId: Long,
    val photoIdsJson: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)
```

## 7.4 Add `AiCurationSessionEntity`

Tracks each scan session.

```kotlin
@Entity(tableName = "ai_curation_sessions")
data class AiCurationSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val scanRangeStartMillis: Long,
    val scanRangeEndMillis: Long,

    val totalFoundCount: Int,
    val analyzedCount: Int,
    val rejectedCount: Int,
    val shortlistedCount: Int,

    val status: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val completedAtMillis: Long? = null
)
```

### Status values

```text
CREATED
SCANNING
ANALYZING
COMPLETED
FAILED
CANCELED
```

## 7.5 Room version

The current DB uses version 2 and destructive migration. For release quality, this should change.

Recommended:

```kotlin
@Database(
    entities = [
        BabyPhotoEntity::class,
        PhotoAnalysisEntity::class,
        PhotoClusterEntity::class,
        AiCurationSessionEntity::class,
        TournamentRecordEntity::class
    ],
    version = 3,
    exportSchema = true
)
```

Migration policy:

- Remove `fallbackToDestructiveMigration()` before public release with user data.
- Add explicit `Migration(2, 3)`.
- Preserve existing `baby_photos`.
- Add AI-related columns or create new analysis tables.
- Backfill `isAiShortlisted = isSelectedAsBest` if keeping compatibility.

---

## 8. AI / CV Pipeline

## 8.1 Design Principle

Use a **cascade pipeline**.

Do not run a heavy local VLM on every gallery image.

```text
MediaStore scan
  → cheap metadata filter
  → blur filter
  → face filter
  → eye/expression filter
  → duplicate clustering
  → scoring policy
  → optional VLM explanation for top N only
```

## 8.2 Stage 0: MediaStore scan

Purpose:

- Find candidate images.
- Avoid manual picker dependency for AI mode.
- Restrict scan range to reduce cost.

Inputs:

- `DATE_TAKEN`
- `DATE_ADDED`
- image dimensions
- URI
- MIME type

Rules:

- Default range: recent 7 days.
- User can choose 1 / 7 / 30 days.
- Only scan JPEG, PNG, HEIC if decodable.
- Do not analyze screenshots by default unless user enables it.

Output:

```kotlin
List<MediaPhotoCandidate>
```

## 8.3 Stage 1: Decode thumbnail

Use a downscaled bitmap for fast analysis.

Recommended size:

```text
Max side: 512 px for quality analysis
Max side: 224 px for embedding / classifier
```

Do not decode full-resolution images unless exporting or previewing.

## 8.4 Stage 2: Blur Detection

Start with classical CV because it is fast and reliable.

Recommended baseline:

- Laplacian variance.
- Tenengrad score if needed.
- Face-region blur should be weighted higher than full-image blur.

Example scoring:

```text
blurScore = normalizedLaplacianVariance(faceCrop or centerCrop)
```

Decision:

```text
blurScore < 0.25 → reject or heavily demote
0.25 <= blurScore < 0.45 → keep but warn
blurScore >= 0.45 → pass
```

## 8.5 Stage 3: Face Detection

Use lightweight on-device face detection.

Recommended options:

1. ML Kit Face Detection
2. MediaPipe Face Detection / Face Mesh
3. Android built-in face detector only as fallback

Preferred for MVP:

```text
ML Kit Face Detection if acceptable dependency-wise.
MediaPipe if richer landmark control is needed.
```

Required outputs:

```kotlin
data class FaceQualityResult(
    val faceDetected: Boolean,
    val faceCount: Int,
    val largestFaceBox: RectF?,
    val faceCenterScore: Float,
    val faceAreaRatio: Float,
    val headYaw: Float?,
    val headPitch: Float?,
    val headRoll: Float?
)
```

## 8.6 Stage 4: Eye State

Options:

- ML Kit eye-open probability.
- MediaPipe landmarks with Eye Aspect Ratio.
- Fallback: skip if unavailable.

Rules:

```text
both eyes clearly open → high score
one eye unclear → medium score
both eyes closed → reject or demote
```

Do not always reject closed eyes. Some sleeping baby photos are emotionally valuable.

Add context:

```text
If image looks like sleeping baby:
    closed eyes are not a rejection reason.
Else:
    closed eyes reduce score.
```

MVP can implement this as a user setting:

```text
[ ] Sleeping baby photos are allowed as good picks
```

## 8.7 Stage 5: Expression Scoring

Expression scoring should not be framed as judging the baby negatively.

Use parent-friendly labels:

```text
NATURAL_SMILE
PLAYFUL
CALM
SLEEPING
CUTE_POUT
AWKWARD_MOTION
UNCLEAR_FACE
```

Avoid labels like:

```text
BAD_FACE
UGLY
UNATTRACTIVE
```

The app should never describe a baby’s face in harsh terms.

## 8.8 Stage 6: Duplicate / Burst Grouping

Use near-duplicate clustering to avoid recommending 10 nearly identical photos.

MVP options:

- Perceptual hash: pHash / dHash
- Thumbnail embedding with cosine similarity
- Time-window grouping: photos taken within 3–10 seconds

Recommended MVP:

```text
time-window grouping + perceptual hash
```

Recommended later:

```text
MobileCLIP / image embedding
```

Cluster rule:

```text
If photos are visually similar and taken close together:
    keep top 1–2 representatives
    demote remaining duplicates
```

## 8.9 Stage 7: Overall Score

Recommended weighted score:

```text
overallScore =
    blurScore * 0.25 +
    faceCenterScore * 0.20 +
    eyeOpenScore * 0.20 +
    expressionScore * 0.15 +
    exposureScore * 0.10 +
    compositionScore * 0.05 +
    uniquenessScore * 0.05
```

### Grade

```text
S: 90–100
A: 80–89
B: 70–79
C: 60–69
D: below 60
```

### Recommendation rule

```text
S/A → strongly recommend
B → keep as backup
C/D → hide from shortlist unless parent opens rejected list
```

## 8.10 Stage 8: Optional Local VLM Explanation

For MVP, do not require VLM for scoring. Use deterministic CV scoring first.

Use VLM only for:

- Top 8–20 photos.
- Generating natural Korean explanation.
- Comparing parent pick vs AI pick.
- Summarizing emotional reason.

Possible model options should be abstracted behind:

```kotlin
interface LocalVisionExplainer {
    suspend fun explain(photo: BabyPhoto, analysis: PhotoAnalysis): AiPhotoExplanation
}
```

Do not hardcode Qwen, MiniCPM, Gemma, or any single model into the domain layer.

---

## 9. Privacy and Permission Strategy

## 9.1 Core Privacy Position

Even after the pivot, Bebecup’s strongest trust message should remain:

```text
사진 분석은 기기 안에서만 진행됩니다.
사진은 외부 서버로 업로드되지 않습니다.
부모가 승인하지 않은 사진은 공유되지 않습니다.
```

## 9.2 Android Permission Model

Use the least invasive flow possible.

### Android 13+

Use:

```text
READ_MEDIA_IMAGES
```

or Android Photo Picker when the user chooses manual scope.

### Android 14+

Support selected-photo access behavior.

### Recommended UX

Offer two modes:

1. **AI Gallery Scan**
   - Needs photo access permission.
   - Explains why permission is needed.

2. **Manual Safe Mode**
   - Uses Photo Picker.
   - No broad gallery scan.

## 9.3 Sensitive Content Handling

Because this app analyzes baby photos:

- Do not upload photos by default.
- Do not collect face embeddings remotely.
- Do not use ad SDKs for photo-based personalization.
- Do not send photo metadata to analytics.
- Avoid storing generated biometric identifiers.
- Allow “delete all local analysis data”.

---

## 10. WorkManager Strategy

AI curation can be expensive. It should not run aggressively.

## 10.1 Manual scan

When user taps:

```text
이번 주 아기사진 AI 엄선하기
```

Run foreground progress UI.

## 10.2 Background scan

Use WorkManager only after opt-in.

Constraints:

```kotlin
Constraints.Builder()
    .setRequiresBatteryNotLow(true)
    .setRequiresStorageNotLow(true)
    .build()
```

For heavier analysis:

```kotlin
Constraints.Builder()
    .setRequiresCharging(true)
    .setRequiresDeviceIdle(true)
    .build()
```

Do not require idle for lightweight scan because it may rarely run on some devices.

## 10.3 Recommended schedule

```text
Daily light metadata scan
Weekly quality analysis
Manual scan anytime
```

---

## 11. UI / UX Specification

## 11.1 AI Scan Setup Screen

### Purpose

Let users choose scope before analysis.

### UI blocks

- Title: `AI가 이번 주 베스트 아기사진을 골라드릴게요`
- Scan range chips:
  - 오늘
  - 최근 7일
  - 최근 30일
  - 직접 선택
- Toggle:
  - `자는 사진도 좋은 사진으로 포함`
  - `비슷한 사진은 한 장만 추천`
  - `흔들린 사진은 자동 제외`
- CTA:
  - `AI 엄선 시작`

## 11.2 AI Scan Progress Screen

### Purpose

Make the scan feel safe and transparent.

Display:

```text
사진을 외부로 보내지 않고 기기 안에서만 분석 중이에요.
```

Progress steps:

1. 최근 사진 찾는 중
2. 흔들림 확인 중
3. 얼굴과 눈 상태 확인 중
4. 비슷한 사진 묶는 중
5. 베스트 후보 정리 중

## 11.3 AI Curation Result Screen

### Top summary

```text
총 184장 중 12장을 추천했어요
```

Stats:

- 추천: 12장
- 후보: 28장
- 제외: 144장
- 비슷한 사진 묶음: 18개

Sections:

1. **AI 추천 베스트**
2. **부모님 확인 필요**
3. **비슷한 사진 묶음**
4. **제외된 사진**

## 11.4 Photo Card UI

Each photo card should show:

- Thumbnail.
- Grade chip: S / A / B.
- Reason chips:
  - 선명해요
  - 눈을 잘 뜨고 있어요
  - 표정이 자연스러워요
  - 얼굴이 중앙에 있어요
- Parent controls:
  - `추천 유지`
  - `제외`
  - `월드컵 후보로 추가`

## 11.5 Rejected Photo UI

Rejected photos should be phrased gently.

Avoid:

```text
표정이 안 좋음
실패 사진
못 나온 사진
```

Use:

```text
추천에서 제외됨
아쉬운 후보
다시 확인 필요
```

Reason examples:

```text
얼굴 부분이 조금 흔들렸어요.
눈이 감겨 있어 활동 사진 후보에서는 제외했어요.
비슷한 사진 중 더 선명한 사진이 있어요.
```

## 11.6 Tournament Integration

Add tournament source options:

```text
월드컵 후보 선택
1. AI 추천 사진으로 시작
2. 부모님이 승인한 사진으로 시작
3. 직접 고른 사진으로 시작
```

Default:

```text
AI 추천 사진으로 시작
```

## 11.7 Winner Report

After tournament:

```text
부모님 선택: A 사진
AI 추천 1위: B 사진
매칭 결과: 다른 선택
```

If matched:

```text
AI도 부모님과 같은 사진을 1위로 골랐어요.
```

If different:

```text
AI는 선명도와 시선 기준으로 다른 사진을 골랐지만, 부모님의 선택이 최종 베스트예요.
```

---

## 12. Business Model Direction

## 12.1 Recommended MVP monetization

Do not start with direct POD API, payment, and shipping.

Start with:

1. Free local curation.
2. Premium batch analysis.
3. Premium weekly memory report.
4. Premium export pack.
5. Optional print-site handoff.

## 12.2 Print flow

Recommended near-term flow:

```text
Final favorite
  → Export / Share
  → Open print partner page
```

Avoid collecting:

- Shipping address.
- Payment info.
- Contact number.

Until there is a real partner API and privacy policy update.

## 12.3 Future POD direct order

Only add direct POD order if:

- A real partner contract exists.
- API terms are confirmed.
- Photo upload consent is explicit.
- Payment/legal flow is ready.
- Privacy policy is updated.
- User can preview exactly what is uploaded.

---

## 13. Implementation Plan

## Phase 1: Refactor for AI-ready architecture

Goal:

- Separate UI, domain, data, and AI logic.

Tasks:

- Split `MainActivity.kt` into screen files.
- Move screen state into feature ViewModels.
- Introduce repository interfaces.
- Introduce use cases.
- Keep existing behavior working.

Deliverables:

- `ui/dashboard/DashboardScreen.kt`
- `ui/tournament/*`
- `ui/print/*`
- `domain/usecase/*`
- Existing tournament tests still pass.

## Phase 2: Local photo scan and analysis cache

Goal:

- Add MediaStore scan and local analysis tables.

Tasks:

- Add permissions.
- Add `MediaStorePhotoSource`.
- Add `PhotoAnalysisEntity`.
- Add `AiCurationSessionEntity`.
- Add explicit Room migration.
- Implement thumbnail decode.

Deliverables:

- User can scan recent images.
- Results are cached.
- Re-scan skips already analyzed photos.

## Phase 3: Basic CV scoring

Goal:

- Detect objectively weak photos.

Tasks:

- Blur detection.
- Face detection.
- Eye-open scoring.
- Exposure approximation.
- Score policy.
- Reason generation from rule-based analyzer.

Deliverables:

- Each photo gets score, grade, and reasons.
- AI shortlist generated locally.

## Phase 4: AI Review UX

Goal:

- Let parent approve AI picks.

Tasks:

- Build AI result screen.
- Build rejected photo screen.
- Add manual override.
- Add “start tournament from AI shortlist”.

Deliverables:

- AI-first app flow works end-to-end.

## Phase 5: Duplicate grouping

Goal:

- Reduce burst-photo overload.

Tasks:

- Add pHash/dHash.
- Add time-window grouping.
- Add cluster representative selection.

Deliverables:

- Similar photos grouped.
- Best representative recommended.

## Phase 6: Optional local VLM explanation

Goal:

- Add natural language feedback without depending on cloud.

Tasks:

- Define `LocalVisionExplainer`.
- Add model availability screen.
- Run only on top N photos.
- Cache VLM explanations.

Deliverables:

- Top photos have richer Korean explanations.
- App remains usable without VLM model.

---

## 14. Recommended Development Goals for Coding Agent

## Goal 1: AI-ready refactor without behavior change

```text
Refactor the existing bebecup-android app so that the current dashboard, photo manager, best-shot selector, tournament, winner, print-cart, and history flows are split out of MainActivity.kt into feature-oriented Compose screen files. Preserve the existing app behavior. Introduce a cleaner package structure under ui/dashboard, ui/photo, ui/tournament, ui/print, and ui/history. Do not add AI behavior yet. Keep BabyCupViewModel working, but prepare it for later separation by grouping state and actions clearly. The app must compile and existing tests must pass.
```

## Goal 2: Add local AI curation data foundation

```text
Add the data foundation for AI photo curation. Create PhotoAnalysisEntity, PhotoClusterEntity, and AiCurationSessionEntity. Update Room database version with an explicit migration instead of destructive migration. Add DAO methods for inserting, updating, and querying photo analysis results, curation sessions, and cluster representatives. Preserve existing baby photo and tournament data. Add repository interfaces for AI curation and keep all analysis data local-only.
```

## Goal 3: Implement MVP AI shortlist pipeline

```text
Implement the first MVP version of the on-device photo curation pipeline. Add a MediaStore photo source that can scan recent images by date range. Decode thumbnails safely. Implement blur scoring, basic face detection integration, simple eye-open scoring when available, exposure approximation, and rule-based overall scoring. Generate Korean reason strings from deterministic rules. Cache analysis results and build an AI shortlist of the top 8 or 16 photos. Add an AI curation result screen where the parent can approve, reject, or manually add photos to the tournament.
```

---

## 15. Technical Risks

## 15.1 Permission friction

Risk:

- Users may hesitate to grant gallery access.

Mitigation:

- Offer manual Photo Picker mode.
- Explain local-only processing clearly.
- Allow selected-photo access.

## 15.2 Device performance

Risk:

- Large gallery scans cause heat or slow UI.

Mitigation:

- Scan recent range by default.
- Decode thumbnails only.
- Use coroutine dispatcher for image work.
- Limit heavy analysis to top candidates.
- Cache every result.

## 15.3 False rejection

Risk:

- AI rejects emotionally meaningful photos.

Mitigation:

- Never delete rejected photos.
- Show rejected photos in separate tab.
- Let parent override all AI decisions.
- Use gentle labels.

## 15.4 Baby photo sensitivity

Risk:

- Privacy trust can be damaged if users think photos are uploaded.

Mitigation:

- Make local-only claim visible.
- Avoid external upload unless explicit export.
- Do not add ad/tracking SDKs.
- Add “Delete all analysis data” setting.

---

## 16. Suggested Scoring Policy v1

```kotlin
data class PhotoScoreInput(
    val blurScore: Float,
    val faceDetected: Boolean,
    val faceCenterScore: Float,
    val eyeOpenScore: Float,
    val expressionScore: Float,
    val exposureScore: Float,
    val compositionScore: Float,
    val uniquenessScore: Float,
    val sleepingModeEnabled: Boolean
)

fun calculateOverallScore(input: PhotoScoreInput): Float {
    if (!input.faceDetected) {
        return weightedScore(input) * 0.65f
    }

    return (
        input.blurScore * 0.25f +
        input.faceCenterScore * 0.20f +
        input.eyeOpenScore * 0.20f +
        input.expressionScore * 0.15f +
        input.exposureScore * 0.10f +
        input.compositionScore * 0.05f +
        input.uniquenessScore * 0.05f
    ) * 100f
}
```

## 16.1 Reason generation examples

```kotlin
fun buildPositiveReasons(analysis: PhotoAnalysis): List<String> {
    val reasons = mutableListOf<String>()

    if (analysis.blurScore >= 0.75f) reasons += "얼굴이 선명하게 보여요"
    if (analysis.eyeOpenScore >= 0.75f) reasons += "눈을 또렷하게 뜨고 있어요"
    if (analysis.expressionScore >= 0.75f) reasons += "표정이 자연스럽고 사랑스러워요"
    if (analysis.faceCenterScore >= 0.70f) reasons += "아기가 사진의 중심에 잘 담겼어요"
    if (analysis.uniquenessScore >= 0.80f) reasons += "비슷한 사진 중에서도 가장 좋은 후보예요"

    return reasons
}
```

```kotlin
fun buildRejectReasons(analysis: PhotoAnalysis): List<String> {
    val reasons = mutableListOf<String>()

    if (analysis.blurScore < 0.35f) reasons += "얼굴 부분이 조금 흔들렸어요"
    if (analysis.eyeOpenScore < 0.35f) reasons += "활동 사진 기준으로는 눈이 감겨 있어요"
    if (analysis.exposureScore < 0.35f) reasons += "사진이 조금 어둡거나 밝게 찍혔어요"
    if (analysis.duplicatePenalty > 0.50f) reasons += "비슷한 사진 중 더 선명한 후보가 있어요"

    return reasons
}
```

---

## 17. App Copy Direction

## 17.1 Recommended wording

Use:

```text
AI 엄선
추천 후보
부모님 확인
아쉬운 후보
비슷한 사진
선명한 순간
자연스러운 표정
```

Avoid:

```text
나쁜 사진
망한 사진
표정 안 좋음
못생김
실패작
```

## 17.2 Main CTA examples

```text
이번 주 아기사진 AI 엄선하기
최근 사진에서 베스트 후보 찾기
AI 추천 사진으로 월드컵 시작
부모님이 직접 최종 선택하기
```

## 17.3 Trust copy

```text
사진은 외부로 전송되지 않고 기기 안에서만 분석돼요.
AI 추천은 참고용이며, 최종 선택은 언제나 부모님이 결정해요.
제외된 사진도 삭제되지 않아요.
```

---

## 18. Release Strategy

## 18.1 Version suggestion

Current app version appears to be early-stage. The AI pivot is large enough for a minor version bump.

Recommended:

```text
0.3.0 — AI Curation Foundation
0.4.0 — AI Shortlist MVP
0.5.0 — Duplicate Grouping and Parent Review
1.0.0 — Stable AI-first Bebecup
```

## 18.2 Store positioning

New title/subtitle direction:

```text
베베컵 - AI 아기사진 엄선 & 가족 월드컵
```

Short description:

```text
흔들린 사진과 비슷한 사진은 줄이고, 이번 주 최고의 아기사진을 기기 안에서 AI로 엄선해요.
```

---

## 19. Final Recommendation

The strongest direction is not to build a general “photo editor” or “AI image app”.

The strongest direction is:

```text
Baby-photo-specific local curation
  + parent approval
  + emotional family tournament
  + print-ready favorites
```

This keeps Bebecup unique.

Many apps can edit photos.  
Fewer apps can help parents choose the best baby photo from hundreds of similar memories.  
That is the product opportunity.
