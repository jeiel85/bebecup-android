package com.bebecup.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.bebecup.app.ai.AiModelAvailability
import com.bebecup.app.ai.FaceModelInstaller
import com.bebecup.app.ai.FaceQualityAnalyzer
import com.bebecup.app.ai.PhotoQualityAnalyzer
import com.bebecup.app.ai.faceid.FaceIdModelStore
import com.bebecup.app.ai.faceid.FaceIdUnavailableException
import com.bebecup.app.ai.faceid.FaceIdentityReader
import com.bebecup.app.data.AppDatabase
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.data.BabyPhotoRepository
import com.bebecup.app.data.TournamentRecord
import com.bebecup.app.data.ai.AiCurationSessionEntity
import com.bebecup.app.data.ai.CurationStatus
import com.bebecup.app.data.ai.RoomAiCurationRepository
import com.bebecup.app.data.children.ChildEntity
import com.bebecup.app.data.children.ChildrenDao
import com.bebecup.app.data.children.FaceInPhoto
import com.bebecup.app.data.children.FamilyFaces
import com.bebecup.app.data.media.MediaStorePhotoSource
import com.bebecup.app.data.media.PhotoAccess
import com.bebecup.app.data.media.PhotoMetadataReader
import com.bebecup.app.data.prefs.BebecupPrefs
import com.bebecup.app.domain.model.ShortlistItem
import com.bebecup.app.domain.children.cupsArePerChild
import com.bebecup.app.domain.monthly.MonthlyCup
import com.bebecup.app.domain.nudge.NudgePolicy
import com.bebecup.app.domain.profile.BabyAge
import com.bebecup.app.domain.profile.CalendarDate
import com.bebecup.app.domain.tournament.TournamentEngine
import com.bebecup.app.domain.tournament.TournamentSnapshots
import com.bebecup.app.domain.usecase.AnalyzePhotoQualityUseCase
import com.bebecup.app.domain.usecase.BuildAiShortlistUseCase
import com.bebecup.app.domain.usecase.BuildRejectedListUseCase
import com.bebecup.app.domain.usecase.ClusterDuplicatesUseCase
import com.bebecup.app.domain.usecase.CompareParentPickWithAiPickUseCase
import com.bebecup.app.domain.usecase.ExplainTopPhotosUseCase
import com.bebecup.app.domain.usecase.ScanRecentBabyPhotosUseCase
import com.bebecup.app.ui.theme.ThemeMode
import com.bebecup.app.nudge.NudgeNotifier
import com.bebecup.app.nudge.NudgeScheduler
import com.bebecup.app.nudge.UnsortedPhotoCounter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The app's journey: 쌓인 사진 알림(nudge) → 정리(AI 추천 + 부모 승인, 보관함에 저장)
 * → 이상형 월드컵 → 인화함. Screens below map onto those steps.
 */
sealed interface UiScreen {
    object Onboarding : UiScreen
    object Dashboard : UiScreen

    // ① 정리하기
    object AiScanSetup : UiScreen
    object AiScanProgress : UiScreen
    object AiCurationResult : UiScreen

    // 보관함 (saved best photos, AI-picked or added by hand)
    object BestBox : UiScreen

    // ② 월드컵
    object WorldCupSetup : UiScreen
    object WorldCupPlay : UiScreen
    /** [monthKey] is set for a 이달의 월드컵 ("yyyy-MM"); [childName] for one child's cup. */
    data class WorldCupWinner(
        val winner: BabyPhoto,
        val bracketSize: Int,
        val monthKey: String? = null,
        val childName: String? = null
    ) : UiScreen
    object TournamentHistoryList : UiScreen

    // ③ 인화
    object PrintCart : UiScreen

    /** 우리 아이들: the children, and faces waiting for a name (아이 얼굴 구분). */
    object Children : UiScreen

    object Settings : UiScreen
}

class BabyCupViewModel(
    application: Application,
    /** Survives Android ending the app in the background; holds an unfinished world cup. */
    private val savedState: SavedStateHandle = SavedStateHandle()
) : AndroidViewModel(application) {

    private val prefs = BebecupPrefs(application)
    private val repository: BabyPhotoRepository
    private val aiRepository: RoomAiCurationRepository
    private val mediaSource = MediaStorePhotoSource(application)
    private val unsortedCounter = UnsortedPhotoCounter(mediaSource, prefs)
    private val scanRecentPhotos: ScanRecentBabyPhotosUseCase
    private val analyzePhotoQuality: AnalyzePhotoQualityUseCase
    private val clusterDuplicates: ClusterDuplicatesUseCase
    private val explainTopPhotos: ExplainTopPhotosUseCase
    private val buildAiShortlist: BuildAiShortlistUseCase
    private val buildRejectedList: BuildRejectedListUseCase
    private val comparePicks = CompareParentPickWithAiPickUseCase()
    private val faceModelInstaller = FaceModelInstaller(application)
    private val metadataReader = PhotoMetadataReader(application)
    private val childrenDao: ChildrenDao
    private val faceIdStore = FaceIdModelStore(application)
    private var faceIdReader: FaceIdentityReader? = null
    private val familyFaces: FamilyFaces

    val bestPhotos: StateFlow<List<BabyPhoto>>
    val printCartPhotos: StateFlow<List<BabyPhoto>>
    val tournaments: StateFlow<List<TournamentRecord>>

    // ---------------- Navigation ----------------

    var currentScreen by mutableStateOf<UiScreen>(UiScreen.Dashboard)
        private set

    // Back stack of screens *behind* [currentScreen] (deepest last).
    private val backStack = mutableStateListOf<UiScreen>()

    val canNavigateBack: Boolean get() = backStack.isNotEmpty()

    // ---------------- Home status (nudge) ----------------

    /** Photos added since the last saved round; null when unknown (no gallery access). */
    var unsortedCount by mutableStateOf<Int?>(null)
        private set
    var canReadPhotos by mutableStateOf(false)
        private set
    var lastReviewedAtMillis by mutableStateOf(prefs.lastReviewedAtMillis)
        private set
    var nudgeEnabled by mutableStateOf(prefs.nudgeEnabled)
        private set

    // ---------------- Curation settings ----------------

    var aiScanRangeDays by mutableStateOf(prefs.scanRangeDays)
        private set
    var aiSleepingModeEnabled by mutableStateOf(prefs.sleepingModeEnabled)
        private set
    var aiSimilarOnly by mutableStateOf(prefs.similarOnly)
        private set
    var aiExcludeBlurry by mutableStateOf(prefs.excludeBlurry)
        private set

    // ---------------- Appearance & face model (설정) ----------------

    var themeMode by mutableStateOf(ThemeMode.fromStorageKey(prefs.themeMode))
        private set

    /** GitHub build: the face model is inside the APK; Play build: Play services fetches it. */
    val faceModelBundled: Boolean get() = faceModelInstaller.bundled

    var faceModelState by mutableStateOf(FaceModelState.UNKNOWN)
        private set
    /** Download progress 0..1 while [FaceModelState.DOWNLOADING] and the size is known. */
    var faceModelProgress by mutableStateOf<Float?>(null)
        private set

    // ---------------- Children (on this device only) ----------------

    /** The family's children, oldest entry first. */
    var children by mutableStateOf<List<ChildEntity>>(emptyList())
        private set
    /** photoId → ids of the children whose faces are in it (face-ID and the parent's corrections). */
    var photoChildren by mutableStateOf<Map<Int, Set<Long>>>(emptyMap())
        private set
    var babyProfilePromptDismissed by mutableStateOf(prefs.babyProfilePromptDismissed)
        private set

    // ---------------- 아이 얼굴 구분 (face-ID) ----------------

    var faceIdState by mutableStateOf(if (faceIdStore.isInstalled()) FaceIdState.READY else FaceIdState.NOT_INSTALLED)
        private set
    /** Model download progress 0..1 while [FaceIdState.DOWNLOADING]. */
    var faceIdProgress by mutableStateOf<Float?>(null)
        private set
    /** Looking at kept photos in the background (after the download, at start). */
    var faceIdBusy by mutableStateOf(false)
        private set
    /** Unknown faces grouped by likeness, waiting for the parent to name them. */
    var faceGroups by mutableStateOf<List<FamilyFaces.Group>>(emptyList())
        private set
    private var newestUnknownFaceId by mutableStateOf(0L)
    private var faceNamingDismissedFaceId by mutableStateOf(prefs.faceNamingDismissedFaceId)
    private var faceIdJob: Job? = null
    /** Photos were kept while [faceIdJob] ran; it looks again before it ends. */
    private var faceIdLookAgain = false

    /** Home asks for names when there are groups the parent has not passed on yet. */
    val showFaceNamingPrompt: Boolean
        get() = faceGroups.isNotEmpty() && newestUnknownFaceId > faceNamingDismissedFaceId

    // ---------------- Curation round (transient) ----------------

    var aiIsScanning by mutableStateOf(false)
        private set
    var aiProgressStep by mutableStateOf("")
        private set
    var aiAnalyzedCount by mutableStateOf(0)
        private set
    var aiTotalCount by mutableStateOf(0)
        private set
    var aiRejectedCount by mutableStateOf(0)
        private set
    var aiClusterCount by mutableStateOf(0)
        private set
    var aiShortlist by mutableStateOf<List<ShortlistItem>>(emptyList())
        private set
    var aiRejectedItems by mutableStateOf<List<ShortlistItem>>(emptyList())
        private set
    val aiApprovedIds = mutableStateListOf<Int>()
    var aiScanFailed by mutableStateOf(false)
        private set
    /** The scan stopped because the face model could not be downloaded. */
    var aiFaceModelMissing by mutableStateOf(false)
        private set
    /** Face model download progress 0..1 while known, null otherwise. */
    var aiFaceModelProgress by mutableStateOf<Float?>(null)
        private set
    private var aiScanWindowEndMillis = 0L

    // ---------------- Kept photos whose file is gone ----------------

    var missingPhotoIds by mutableStateOf<Set<Int>>(emptySet())
        private set
    private var missingCheck: Job? = null

    // ---------------- World cup ----------------

    private var engine: TournamentEngine<BabyPhoto>? = null
    var currentMatch by mutableStateOf<TournamentEngine.Match<BabyPhoto>?>(null)
        private set
    var currentRoundName by mutableStateOf("")
        private set
    var currentMatchLabel by mutableStateOf("")
        private set

    /** AI #1 among the photos in this tournament; null for hand-picked brackets. */
    private var aiTopPickId: Int? = null

    /** Month chosen on the setup screen ("yyyy-MM"); null = the most recently saved photos. */
    var worldCupMonth by mutableStateOf<String?>(null)
        private set
    /** Month of the cup being played; recorded with its winner. */
    private var playingMonth: String? = null
    /** Child chosen on the setup screen; null = every saved photo (only without siblings). */
    var worldCupChildId by mutableStateOf<Long?>(null)
        private set

    /** Siblings never compete: every cup is one child's ([cupsArePerChild]). */
    val cupsPerChild: Boolean get() = cupsArePerChild(children.size)
    /** Child of the cup being played; its name goes into the recorded title. */
    private var playingChildId: Long? = null
    var monthlyCupSkippedMonth by mutableStateOf(prefs.monthlyCupSkippedMonth)
        private set

    init {
        val database = AppDatabase.getDatabase(application)
        repository = BabyPhotoRepository(database.babyPhotoDao())
        aiRepository = RoomAiCurationRepository(
            analysisDao = database.photoAnalysisDao(),
            clusterDao = database.photoClusterDao(),
            sessionDao = database.aiCurationSessionDao()
        )
        val photoQualityAnalyzer = PhotoQualityAnalyzer(metadataReader, FaceQualityAnalyzer())
        scanRecentPhotos = ScanRecentBabyPhotosUseCase(repository, mediaSource, metadataReader::readTakenDate)
        analyzePhotoQuality = AnalyzePhotoQualityUseCase(aiRepository, photoQualityAnalyzer)
        clusterDuplicates = ClusterDuplicatesUseCase(aiRepository)
        explainTopPhotos = ExplainTopPhotosUseCase(repository, aiRepository, AiModelAvailability().explainer())
        buildAiShortlist = BuildAiShortlistUseCase(repository, aiRepository)
        buildRejectedList = BuildRejectedListUseCase(repository, aiRepository)
        childrenDao = database.childrenDao()
        familyFaces = FamilyFaces(childrenDao, reader = ::faceReaderOrNull)

        if (!prefs.onboardingSeen) currentScreen = UiScreen.Onboarding

        bestPhotos = repository.bestPhotos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        printCartPhotos = repository.printCartPhotos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        tournaments = repository.allTournaments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        viewModelScope.launch { childrenDao.observeChildren().collect { children = it } }
        viewModelScope.launch {
            childrenDao.observePhotoChildren().collect { rows ->
                photoChildren = rows.groupBy({ it.photoId }, { it.childId }).mapValues { it.value.toSet() }
            }
        }
        viewModelScope.launch {
            childrenDao.observeNewestUnknownFaceId(FamilyFaces.VERSION).collect { newestUnknownFaceId = it }
        }

        fixKeptPhotoDates()
        moveLegacyProfile()
        restoreTournament()
        if (faceIdState == FaceIdState.READY) lookAtKeptPhotos()
    }

    override fun onCleared() {
        faceIdReader?.close()
        faceIdReader = null
    }

    // ================= Navigation =================

    /** Forward navigation; pops back to [screen] if it is already in the stack. */
    fun navigateTo(screen: UiScreen) {
        if (screen == currentScreen) return
        val existingIndex = backStack.indexOfLast { it == screen }
        if (existingIndex >= 0) {
            while (backStack.size > existingIndex) backStack.removeAt(backStack.size - 1)
        } else {
            backStack.add(currentScreen)
        }
        show(screen)
    }

    /** Replace the current screen without changing depth (transient hand-offs). */
    private fun navigateReplace(screen: UiScreen) {
        show(screen)
    }

    fun navigateHome() {
        backStack.clear()
        show(UiScreen.Dashboard)
    }

    /** Land on [screen] with only home beneath it (terminal screens). */
    private fun navigateAsRoot(screen: UiScreen) {
        backStack.clear()
        backStack.add(UiScreen.Dashboard)
        show(screen)
    }

    /** Pop one level; false at the root so the OS can leave the app. */
    fun navigateBack(): Boolean {
        if (backStack.isEmpty()) return false
        show(backStack.removeAt(backStack.size - 1))
        return true
    }

    /**
     * Every screen change goes through here. Leaving the play screen abandons
     * the bracket (there is no way back into it), so it is not restored later.
     */
    private fun show(screen: UiScreen) {
        currentScreen = screen
        if (screen !is UiScreen.WorldCupPlay && engine != null) {
            engine = null
            saveTournament(null)
        }
    }

    /**
     * Opened from a reminder: "사진이 쌓였어요" goes straight to sorting,
     * "N월의 월드컵" to that month's cup setup.
     */
    fun handleLaunchIntent(intent: Intent?) {
        if (intent == null) return
        val month = intent.getStringExtra(NudgeNotifier.EXTRA_OPEN_MONTHLY_CUP)
        val monthChild = intent.getLongExtra(NudgeNotifier.EXTRA_MONTHLY_CUP_CHILD, 0L).takeIf { it > 0 }
        val curation = intent.getBooleanExtra(NudgeNotifier.EXTRA_OPEN_CURATION, false)
        intent.removeExtra(NudgeNotifier.EXTRA_OPEN_MONTHLY_CUP)
        intent.removeExtra(NudgeNotifier.EXTRA_MONTHLY_CUP_CHILD)
        intent.removeExtra(NudgeNotifier.EXTRA_OPEN_CURATION)
        if (!prefs.onboardingSeen) return
        when {
            month != null -> {
                navigateHome()
                openWorldCup(month, monthChild)
            }
            curation -> {
                navigateHome()
                navigateTo(UiScreen.AiScanSetup)
            }
        }
    }

    // ================= Onboarding & nudge =================

    /** [downloadFaceId]: the parent asked to turn on 아이 얼굴 구분 on the last page. */
    fun completeOnboarding(downloadFaceId: Boolean = false) {
        prefs.onboardingSeen = true
        // Start fetching the face model now so the first scan doesn't wait.
        faceModelInstaller.requestInBackground()
        if (downloadFaceId) downloadFaceIdModel()
        navigateHome()
        refreshHomeStatus()
    }

    /**
     * Turn the "사진이 쌓이면 알려주기" reminder on/off. The caller requests the
     * gallery + notification permissions first; the worker re-checks them.
     */
    fun updateNudgeEnabled(enabled: Boolean) {
        prefs.nudgeEnabled = enabled
        nudgeEnabled = enabled
        val app = getApplication<Application>()
        if (enabled) NudgeScheduler.enable(app) else NudgeScheduler.disable(app)
    }

    /** Recount unsorted photos; call when the home screen becomes visible. */
    fun refreshHomeStatus() {
        val app = getApplication<Application>()
        canReadPhotos = PhotoAccess.canReadPhotos(app)
        lastReviewedAtMillis = prefs.lastReviewedAtMillis
        if (!canReadPhotos) {
            unsortedCount = null
            return
        }
        viewModelScope.launch {
            unsortedCount = try {
                unsortedCounter.count()
            } catch (e: SecurityException) {
                Log.w(TAG, "gallery access revoked while counting", e)
                null
            }
        }
    }

    fun daysSinceReview(): Long? =
        NudgePolicy.daysSinceReview(lastReviewedAtMillis, System.currentTimeMillis())

    // ================= 설정: 화면 테마 · 얼굴 인식 기능 =================

    fun updateThemeMode(mode: ThemeMode) {
        prefs.themeMode = mode.storageKey
        themeMode = mode
    }

    /** Whether the face model is on this device (설정 shows it). Always ready in the GitHub build (bundled). */
    fun refreshFaceModelState() {
        if (faceModelState == FaceModelState.DOWNLOADING) return
        viewModelScope.launch {
            faceModelState = FaceModelState.CHECKING
            faceModelState = when (faceModelInstaller.isInstalled()) {
                true -> FaceModelState.READY
                false -> FaceModelState.NOT_INSTALLED
                null -> FaceModelState.UNAVAILABLE
            }
        }
    }

    /** 설정 > 지금 받기: download the face model now instead of at the first scan. */
    fun installFaceModelNow() {
        if (faceModelState == FaceModelState.DOWNLOADING || aiIsScanning) return
        viewModelScope.launch {
            faceModelState = FaceModelState.DOWNLOADING
            faceModelProgress = null
            val ok = faceModelInstaller.ensureInstalled { faceModelProgress = it }
            faceModelProgress = null
            faceModelState = if (ok) FaceModelState.READY else FaceModelState.FAILED
        }
    }

    // ================= 우리 아이들 =================

    /** Add a child (name required, birthday "yyyy-MM-dd" or null); [faceIds] are named as them at once. */
    fun addChild(name: String, birthDate: String?, faceIds: List<Long> = emptyList()): Job {
        val trimmed = name.trim().take(MAX_CHILD_NAME)
        val birth = CalendarDate.parse(birthDate)?.toString() ?: ""
        return viewModelScope.launch {
            if (trimmed.isEmpty()) return@launch
            val id = childrenDao.insertChild(
                ChildEntity(name = trimmed, birthDate = birth, createdAtMillis = System.currentTimeMillis())
            )
            if (faceIds.isNotEmpty()) {
                familyFaces.name(faceIds, id)
                refreshFaceGroups()
            }
        }
    }

    /** Rename a child or change their birthday (null or an impossible date clears it). */
    fun updateChild(id: Long, name: String, birthDate: String?) {
        val trimmed = name.trim().take(MAX_CHILD_NAME)
        if (trimmed.isEmpty()) return
        val birth = CalendarDate.parse(birthDate)?.toString() ?: ""
        viewModelScope.launch { childrenDao.updateChild(id, trimmed, birth) }
    }

    /** Remove a child. Their faces become unknown again; the photos stay. */
    fun deleteChild(id: Long) {
        if (worldCupChildId == id) worldCupChildId = null
        viewModelScope.launch {
            childrenDao.deleteChild(id)
            familyFaces.rematch()
            refreshFaceGroups()
        }
    }

    fun dismissBabyProfilePrompt() {
        prefs.babyProfilePromptDismissed = true
        babyProfilePromptDismissed = true
    }

    /** The children whose faces are in [photo]. */
    fun childrenIn(photo: BabyPhoto): List<ChildEntity> {
        val ids = photoChildren[photo.id] ?: return emptyList()
        return children.filter { it.id in ids }
    }

    /**
     * How old the child in [photo] was: the one child found in it, or — in a
     * one-child family — that child for photos where nobody was found. Null with
     * no birthday, several children in the photo, or photos before the birthday.
     */
    fun ageOf(photo: BabyPhoto): BabyAge? {
        val inPhoto = childrenIn(photo)
        val child = when {
            inPhoto.size == 1 -> inPhoto.single()
            inPhoto.isEmpty() && children.size == 1 -> children.single()
            else -> return null
        }
        return BabyAge.on(child.birthDate, photo.takenDate)
    }

    /** Each child with a birthday and how old they are today. */
    fun agesToday(): List<Pair<ChildEntity, BabyAge>> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        return children.mapNotNull { child -> BabyAge.on(child.birthDate, today)?.let { child to it } }
    }

    /** Before v6 the one baby's name and birthday lived in prefs; they become the first child. */
    private fun moveLegacyProfile() {
        if (prefs.legacyProfileMoved) return
        viewModelScope.launch {
            val name = prefs.babyName.trim().take(MAX_CHILD_NAME)
            val birth = CalendarDate.parse(prefs.babyBirthDate)?.toString() ?: ""
            if ((name.isNotEmpty() || birth.isNotEmpty()) && childrenDao.getChildren().isEmpty()) {
                childrenDao.insertChild(
                    ChildEntity(
                        name = name.ifEmpty { DEFAULT_CHILD_NAME },
                        birthDate = birth,
                        createdAtMillis = System.currentTimeMillis()
                    )
                )
            }
            prefs.legacyProfileMoved = true
        }
    }

    // ================= 아이 얼굴 구분 =================

    private fun faceReaderOrNull(): FaceIdentityReader? {
        if (!faceIdStore.isInstalled()) return null
        return faceIdReader ?: FaceIdentityReader(getApplication(), metadataReader, faceIdStore).also { faceIdReader = it }
    }

    /** Onboarding or 설정: download the face-ID model (~10 MB), then look at the kept photos. */
    fun downloadFaceIdModel() {
        if (faceIdState == FaceIdState.DOWNLOADING) return
        viewModelScope.launch {
            faceIdState = FaceIdState.DOWNLOADING
            faceIdProgress = 0f
            faceIdState = try {
                faceIdStore.download { p -> viewModelScope.launch { faceIdProgress = p } }
                FaceIdState.READY
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "face-ID model download failed", e)
                FaceIdState.FAILED
            }
            faceIdProgress = null
            if (faceIdState == FaceIdState.READY) lookAtKeptPhotos()
        }
    }

    /**
     * 설정 > 받은 파일 지우기: frees the space and stops looking at new photos. Names
     * already given and photos already sorted by child stay.
     */
    fun deleteFaceIdModel() {
        if (faceIdState == FaceIdState.DOWNLOADING || faceIdBusy || aiIsScanning) return
        faceIdReader?.close()
        faceIdReader = null
        faceIdStore.delete()
        faceIdState = FaceIdState.NOT_INSTALLED
    }

    /** Find faces in kept photos (보관함·인화함) not looked at yet, in the background. */
    fun lookAtKeptPhotos() {
        if (!faceIdStore.isInstalled()) return
        if (faceIdJob?.isActive == true) {
            faceIdLookAgain = true
            return
        }
        faceIdJob = viewModelScope.launch {
            faceIdBusy = true
            try {
                do {
                    faceIdLookAgain = false
                    familyFaces.identify(familyFaces.keptPhotosToLookAt())
                } while (faceIdLookAgain)
                refreshFaceGroups()
            } catch (e: FaceIdUnavailableException) {
                Log.w(TAG, "face-ID unavailable", e)
                faceIdState = FaceIdState.UNAVAILABLE
            } finally {
                faceIdBusy = false
            }
        }
    }

    /** 설정 > 다시 시도 after [FaceIdState.UNAVAILABLE]. */
    fun retryFaceId() {
        if (faceIdBusy || aiIsScanning) return
        faceIdReader?.close()
        faceIdReader = null
        faceIdState = if (faceIdStore.isInstalled()) FaceIdState.READY else FaceIdState.NOT_INSTALLED
        if (faceIdState == FaceIdState.READY) lookAtKeptPhotos()
    }

    fun refreshFaceGroups() {
        viewModelScope.launch { faceGroups = familyFaces.unknownGroups() }
    }

    /** The parent named faces: [childId], or null for "none of the children". */
    fun nameFaces(faceIds: List<Long>, childId: Long?): Job =
        viewModelScope.launch {
            familyFaces.name(faceIds, childId)
            refreshFaceGroups()
        }

    suspend fun facesIn(photo: BabyPhoto): List<FaceInPhoto> = familyFaces.facesIn(photo.id)

    suspend fun faceOf(child: ChildEntity): FaceInPhoto? = familyFaces.faceOf(child.id)

    fun dismissFaceNaming() {
        prefs.faceNamingDismissedFaceId = newestUnknownFaceId
        faceNamingDismissedFaceId = newestUnknownFaceId
    }

    // ================= ① 정리하기 (AI curation) =================

    fun updateScanRangeDays(days: Int) {
        aiScanRangeDays = days
        prefs.scanRangeDays = days
    }

    fun updateSleepingMode(enabled: Boolean) {
        if (enabled == aiSleepingModeEnabled) return
        aiSleepingModeEnabled = enabled
        prefs.sleepingModeEnabled = enabled
        // Eye/expression scores depend on this setting, so cached scores are
        // stale: drop them and let the next scan re-analyze.
        viewModelScope.launch { aiRepository.invalidateAnalyses() }
    }

    fun updateSimilarOnly(enabled: Boolean) {
        aiSimilarOnly = enabled
        prefs.similarOnly = enabled
    }

    fun updateExcludeBlurry(enabled: Boolean) {
        aiExcludeBlurry = enabled
        prefs.excludeBlurry = enabled
    }

    /** Scan window for the current settings: since the last saved round, or N days. */
    private fun scanWindow(nowMillis: Long): LongRange {
        val start = if (aiScanRangeDays == BebecupPrefs.SCAN_SINCE_LAST_REVIEW) {
            NudgePolicy.unsortedWindowStart(prefs.lastReviewedAtMillis, nowMillis)
        } else {
            nowMillis - aiScanRangeDays.toLong() * DAY_MILLIS
        }
        return start..nowMillis
    }

    /**
     * Foreground AI curation: scan the window, analyze on-device, rank, then
     * show the result for the parent to approve. Requires gallery permission.
     */
    fun startAiCuration() {
        if (aiIsScanning) return
        viewModelScope.launch {
            aiIsScanning = true
            aiScanFailed = false
            aiFaceModelMissing = false
            aiFaceModelProgress = null
            aiAnalyzedCount = 0
            aiTotalCount = 0
            aiRejectedCount = 0
            aiClusterCount = 0
            aiShortlist = emptyList()
            aiRejectedItems = emptyList()
            aiApprovedIds.clear()
            aiProgressStep = STEP_SCAN
            navigateReplace(UiScreen.AiScanProgress)

            val window = scanWindow(System.currentTimeMillis())
            aiScanWindowEndMillis = window.last
            var sessionId = 0
            try {
                // Without the face model every photo would fail analysis and the
                // result would be silently empty, so get it first or stop here.
                val faceModelReady = faceModelInstaller.ensureInstalled { progress ->
                    aiProgressStep = STEP_FACE_MODEL
                    aiFaceModelProgress = progress
                }
                if (!faceModelReady) {
                    faceModelState = FaceModelState.FAILED
                    aiFaceModelMissing = true
                    aiScanFailed = true
                    return@launch
                }
                aiFaceModelProgress = null
                faceModelState = FaceModelState.READY
                aiProgressStep = STEP_SCAN
                aiRepository.deleteStaleAnalyses()

                sessionId = aiRepository.startSession(
                    AiCurationSessionEntity(
                        scanRangeStartMillis = window.first,
                        scanRangeEndMillis = window.last,
                        status = CurationStatus.SCANNING,
                        createdAtMillis = window.last
                    )
                ).toInt()

                val photos = scanRecentPhotos(window.first, window.last, includeScreenshots = false)
                aiTotalCount = photos.size

                aiProgressStep = STEP_ANALYZE
                analyzePhotoQuality(
                    photos = photos,
                    sleepingModeEnabled = aiSleepingModeEnabled,
                    nowMillis = System.currentTimeMillis()
                ) { done, total ->
                    aiAnalyzedCount = done
                    aiTotalCount = total
                }

                if (aiSimilarOnly) {
                    aiProgressStep = STEP_CLUSTER
                    aiClusterCount = clusterDuplicates(System.currentTimeMillis())
                }

                val scannedIds = photos.map { it.id }.toSet()
                explainTopPhotos(
                    limit = ExplainTopPhotosUseCase.DEFAULT_LIMIT,
                    photoIds = scannedIds,
                    excludeBlurry = aiExcludeBlurry
                )

                aiProgressStep = STEP_SHORTLIST
                val analyzedInScan = aiRepository.getAnalyzedPhotoIds().count { it in scannedIds }
                val shortlist = buildAiShortlist(
                    limit = BuildAiShortlistUseCase.DEFAULT_LIMIT,
                    photoIds = scannedIds,
                    excludeBlurry = aiExcludeBlurry
                )
                aiShortlist = shortlist
                aiRejectedItems = buildRejectedList(
                    shortlistLimit = BuildAiShortlistUseCase.DEFAULT_LIMIT,
                    photoIds = scannedIds,
                    excludeBlurry = aiExcludeBlurry
                )
                aiApprovedIds.addAll(shortlist.map { it.photo.id })
                aiRejectedCount = (analyzedInScan - shortlist.size).coerceAtLeast(0)

                // 아이 얼굴 구분: only the photos shown in the result can be kept, so
                // only those (with a face) are looked at.
                if (faceIdState == FaceIdState.READY) {
                    aiProgressStep = STEP_FACE_ID
                    faceIdJob?.join()
                    try {
                        familyFaces.identify(
                            (shortlist + aiRejectedItems).filter { it.analysis.faceDetected }.map { it.photo }
                        )
                        faceGroups = familyFaces.unknownGroups()
                    } catch (e: FaceIdUnavailableException) {
                        Log.w(TAG, "face-ID unavailable during the scan", e)
                        faceIdState = FaceIdState.UNAVAILABLE
                    }
                }

                aiRepository.getSession(sessionId)?.let { s ->
                    aiRepository.updateSession(
                        s.copy(
                            totalFoundCount = photos.size,
                            analyzedCount = analyzedInScan,
                            shortlistedCount = shortlist.size,
                            rejectedCount = aiRejectedCount,
                            status = CurationStatus.COMPLETED,
                            completedAtMillis = System.currentTimeMillis()
                        )
                    )
                }
                if (currentScreen is UiScreen.AiScanProgress) navigateReplace(UiScreen.AiCurationResult)
            } catch (e: Exception) {
                Log.e(TAG, "startAiCuration failed", e)
                aiScanFailed = true
                if (sessionId != 0) {
                    aiRepository.getSession(sessionId)?.let { s ->
                        aiRepository.updateSession(s.copy(status = CurationStatus.FAILED))
                    }
                }
            } finally {
                aiIsScanning = false
            }
        }
    }

    /** A finished scan's result with every [shortlist] photo approved, as [startAiCuration] leaves it. */
    @VisibleForTesting
    internal fun showCurationResult(shortlist: List<ShortlistItem>) {
        aiShortlist = shortlist
        aiRejectedItems = emptyList()
        aiApprovedIds.clear()
        aiApprovedIds.addAll(shortlist.map { it.photo.id })
        navigateTo(UiScreen.AiCurationResult)
    }

    fun toggleShortlistApproval(photoId: Int) {
        if (aiApprovedIds.contains(photoId)) aiApprovedIds.remove(photoId) else aiApprovedIds.add(photoId)
    }

    /**
     * With siblings: the children who have at least two approved photos in
     * this round, with that count; "바로 월드컵" plays one of their cups.
     */
    fun curationCupChoices(): List<Pair<ChildEntity, Int>> {
        val approved = approvedItems()
        return children
            .map { child -> child to approved.count { child.id in photoChildren[it.photo.id].orEmpty() } }
            .filter { it.second >= 2 }
    }

    /** Approved items from the round, best-first (shortlist, then rescued rejects). */
    private fun approvedItems(): List<ShortlistItem> =
        (aiShortlist + aiRejectedItems).filter { aiApprovedIds.contains(it.photo.id) }

    /**
     * Save the round: approved photos go into the 보관함 and the photos up to
     * the end of this scan count as "확인함" (the nudge resets). With
     * [thenPlay], immediately start a world cup with the approved photos.
     */
    fun saveCurationRound(thenPlay: Boolean, child: Long? = null) {
        val approved = approvedItems()
        // With siblings the cup is [child]'s approved photos only; without a child it doesn't start.
        val perChild = cupsPerChild
        val contenders =
            if (perChild) approved.filter { child != null && child in photoChildren[it.photo.id].orEmpty() } else approved
        val endMillis = aiScanWindowEndMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
        viewModelScope.launch {
            repository.markBest(approved.map { it.photo.id }, System.currentTimeMillis())
            // Every scan adds a row per gallery photo; keep only what the family kept.
            // Their faces go with them (named faces stay as examples).
            val pruned = repository.pruneUnkeptPhotos()
            if (pruned > 0) Log.i(TAG, "dropped $pruned scanned photos that were not kept")
            refreshFaceGroups()
            prefs.lastReviewedAtMillis = maxOf(prefs.lastReviewedAtMillis, endMillis)
            lastReviewedAtMillis = prefs.lastReviewedAtMillis
            unsortedCount = if (canReadPhotos) 0 else null

            if (thenPlay && contenders.size >= 2) {
                // `approved` is score-ordered, so the bracket takes the top N
                // and its first entry is the AI #1 the family is compared with.
                val size = TournamentEngine.availableSizes(contenders.size).last()
                val entrants = contenders.take(size)
                aiTopPickId = entrants.first().photo.id
                playingMonth = null
                playingChildId = if (perChild) child else null
                launchTournament(entrants.map { it.photo }, asRoot = true)
            } else {
                navigateAsRoot(UiScreen.BestBox)
            }
            refreshHomeStatus()
        }
    }

    /**
     * Privacy: wipe all on-device analysis/clusters/sessions and every face vector
     * (which faces are whose, too). The children's names and birthdays stay.
     */
    fun deleteAllAnalysisData() {
        viewModelScope.launch {
            faceIdJob?.cancelAndJoin()
            aiRepository.deleteAllLocalAnalysisData()
            familyFaces.deleteAll()
            faceGroups = emptyList()
            aiShortlist = emptyList()
            aiRejectedItems = emptyList()
            aiApprovedIds.clear()
            aiAnalyzedCount = 0
            aiTotalCount = 0
            aiRejectedCount = 0
        }
    }

    // ================= 보관함 =================

    /**
     * Add photos the parent picked with the system Photo Picker straight into
     * the 보관함. Persistable read access is taken so the photos still open
     * after the app restarts.
     */
    fun addPickedPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val app = getApplication<Application>()
        viewModelScope.launch {
            val ids = mutableListOf<Int>()
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
            uris.forEach { uri ->
                try {
                    app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: SecurityException) {
                    // Some providers don't offer persistable grants; the photo
                    // stays readable for this session and shows a placeholder later.
                    Log.w(TAG, "no persistable permission for $uri", e)
                }
                val uriString = uri.toString()
                val existing = repository.getPhotoByUri(uriString)
                ids += existing?.id ?: repository.insertPhoto(
                    BabyPhoto(
                        uriString = uriString,
                        title = PICKED_PHOTO_TITLE,
                        description = "",
                        takenDate = metadataReader.readTakenDate(uri) ?: today
                    )
                ).toInt()
            }
            repository.markBest(ids, System.currentTimeMillis())
            if (faceIdState == FaceIdState.READY) lookAtKeptPhotos()
        }
    }

    /**
     * Before the channel split (after v0.9.0), photos picked by hand were stored with the day they were
     * picked and scanned photos with the gallery's date, which can be the day a
     * messenger saved the file. Re-read every kept photo's capture date once so
     * ages come out right; a photo that can no longer be opened keeps its date.
     */
    private fun fixKeptPhotoDates() {
        if (prefs.keptPhotoDatesFixed) return
        viewModelScope.launch {
            repository.getKeptPhotos().forEach { photo ->
                val taken = metadataReader.readTakenDate(Uri.parse(photo.uriString))
                if (taken != null && taken != photo.takenDate) repository.updateTakenDate(photo.id, taken)
            }
            prefs.keptPhotoDatesFixed = true
        }
    }

    /**
     * Look for kept photos whose file was deleted from the gallery, so the
     * screen can offer to take them out. Only a definite "not found" counts.
     */
    fun findMissingPhotos(photos: List<BabyPhoto>) {
        missingCheck?.cancel()
        missingCheck = viewModelScope.launch {
            missingPhotoIds = photos.distinctBy { it.id }
                .filter { metadataReader.isGone(Uri.parse(it.uriString)) }
                .mapTo(HashSet()) { it.id }
        }
    }

    /** Take photos found by [findMissingPhotos] out of the 보관함 and 인화함. */
    fun removeMissingPhotos() {
        val ids = missingPhotoIds.toList()
        if (ids.isEmpty()) return
        missingPhotoIds = emptySet()
        viewModelScope.launch { repository.deletePhotos(ids) }
    }

    fun removeFromBest(photo: BabyPhoto) {
        viewModelScope.launch { repository.unmarkBest(photo.id) }
    }

    // ================= ② 월드컵 =================

    /**
     * Open the cup setup, on [month]'s photos ("yyyy-MM") or on the latest
     * saved ones; [child] picks whose cup (otherwise the last choice stays).
     */
    fun openWorldCup(month: String? = null, child: Long? = null) {
        worldCupMonth = month
        if (child != null) worldCupChildId = child
        navigateTo(UiScreen.WorldCupSetup)
    }

    fun selectWorldCupMonth(month: String?) {
        worldCupMonth = month
    }

    /** Whose cup: [childId], or null for every saved photo. */
    fun selectWorldCupChild(childId: Long?) {
        worldCupChildId = childId
    }

    /** Last month's cup when it is due (home card), and whose; see [MonthlyCup.pending]. */
    fun pendingMonthlyCup(best: List<BabyPhoto>, records: List<TournamentRecord>): MonthlyCup.Due? =
        MonthlyCup.pending(
            today = MonthlyCup.today(),
            childIds = children.map { it.id },
            photos = best.map { MonthlyCup.Photo(it.takenDate, photoChildren[it.id].orEmpty()) },
            held = records.mapNotNull { r -> r.monthKey?.let { MonthlyCup.Held(it, r.childId) } },
            skippedKey = monthlyCupSkippedMonth
        )

    /**
     * Whose cup the setup opens on when no child is chosen: with siblings, of
     * the children with at least two saved photos, the first who can hold
     * [month]'s cup and has not yet (then any who can hold it, then the first
     * one); null without siblings (the family cup) or when no child has two.
     */
    fun defaultCupChild(best: List<BabyPhoto>, month: String?, records: List<TournamentRecord>): Long? {
        if (!cupsPerChild) return null
        val able = children.filter { photosOf(best, it.id).size >= 2 }
        if (month == null) return able.firstOrNull()?.id
        val held = records.filter { it.monthKey == month }.mapNotNull { it.childId }.toSet()
        val inMonth = able.filter { worldCupPool(best, month, it.id).size >= MonthlyCup.MIN_PHOTOS }
        return (inMonth.firstOrNull { it.id !in held } ?: inMonth.firstOrNull() ?: able.firstOrNull())?.id
    }

    /** "10월", with the year when it isn't this year. */
    fun monthLabel(monthKey: String): String = MonthlyCup.label(monthKey, MonthlyCup.today().year)

    /** The parent passed on [month]'s cup; the home card stays away until next month. */
    fun skipMonthlyCup(month: String) {
        prefs.monthlyCupSkippedMonth = month
        monthlyCupSkippedMonth = month
    }

    /** Saved photos with [childId] in them, or all of them when [childId] is null. */
    fun photosOf(best: List<BabyPhoto>, childId: Long?): List<BabyPhoto> =
        if (childId == null) best else best.filter { childId in photoChildren[it.id].orEmpty() }

    /**
     * Photos a cup would draw from, most recently saved first: those taken in
     * [month] (all months when null) with [childId] in them (anyone when null).
     */
    fun worldCupPool(best: List<BabyPhoto>, month: String?, childId: Long? = worldCupChildId): List<BabyPhoto> =
        photosOf(best, childId)
            .filter { month == null || MonthlyCup.monthKeyOf(it.takenDate) == month }
            .sortedByDescending { it.bestSelectedTimestamp }

    /** Bracket of [size] from [month]'s saved photos, or the most recently saved ones, of the chosen child. */
    fun startWorldCup(size: Int, month: String? = worldCupMonth, child: Long? = worldCupChildId) {
        val childId = child?.takeIf { id -> children.any { it.id == id } }
        if (childId == null && cupsPerChild) return // siblings never share a bracket
        val pool = worldCupPool(bestPhotos.value, month, childId)
        if (pool.size < 2) return
        aiTopPickId = null
        playingMonth = month
        playingChildId = childId
        launchTournament(pool.take(size.coerceAtMost(pool.size)).shuffled(), asRoot = false)
    }

    private fun launchTournament(entrants: List<BabyPhoto>, asRoot: Boolean) {
        val newEngine = TournamentEngine(entrants.shuffled())
        engine = newEngine
        saveTournament(newEngine)
        syncMatchState(newEngine)
        if (asRoot) navigateAsRoot(UiScreen.WorldCupPlay) else navigateTo(UiScreen.WorldCupPlay)
    }

    /** Record a vote. A stale or duplicate tap for an already-decided match is ignored. */
    fun vote(match: TournamentEngine.Match<BabyPhoto>, winner: BabyPhoto) {
        val e = engine ?: return
        if (!e.vote(match, winner)) return
        viewModelScope.launch { repository.incrementMatchWins(winner.id) }
        saveTournament(e)
        syncMatchState(e)
        e.champion?.let { declareChampion(it, e.bracketSize) }
    }

    private fun syncMatchState(e: TournamentEngine<BabyPhoto>) {
        currentMatch = e.currentMatch
        currentRoundName = TournamentEngine.roundLabel(e.roundSize)
        currentMatchLabel = if (e.matchesInRound > 1) "${e.currentMatchNumber + 1} / ${e.matchesInRound}" else ""
    }

    private fun declareChampion(winner: BabyPhoto, bracketSize: Int) {
        engine = null
        saveTournament(null)
        val month = playingMonth
        val childName = children.firstOrNull { it.id == playingChildId }?.name
        // Show the winner right away; recording it must not hold the screen
        // hostage to disk I/O (a slow device would sit on a blank play screen).
        navigateAsRoot(UiScreen.WorldCupWinner(winner.copy(winsCount = winner.winsCount + 1), bracketSize, month, childName))
        viewModelScope.launch {
            try {
                repository.incrementChampionships(winner.id)
                repository.insertTournament(
                    TournamentRecord(
                        winnerPhotoId = winner.id,
                        winnerTitle = winner.title,
                        winnerUriString = winner.uriString,
                        totalParticipants = bracketSize,
                        tournamentTitle = cupTitle(month, childName),
                        monthKey = month,
                        childId = playingChildId
                    )
                )
            } catch (e: Exception) {
                // The family still sees their winner; only the hall-of-fame
                // entry is missing, so log it rather than interrupt them.
                Log.e(TAG, "could not record the tournament result", e)
            }
        }
    }

    /** "10월의 월드컵", "10월의 하린 월드컵", "하린 이상형 월드컵" or the family cup. */
    private fun cupTitle(month: String?, childName: String?): String = when {
        month != null && childName != null -> "${MonthlyCup.label(month, withYear = true)}의 $childName 월드컵"
        month != null -> "${MonthlyCup.label(month, withYear = true)}의 월드컵"
        childName != null -> "$childName 이상형 월드컵"
        else -> "베스트 아기사진 이상형 월드컵"
    }

    /** Keep [e]'s unfinished bracket (as photo ids) in [savedState]; null forgets it. */
    private fun saveTournament(e: TournamentEngine<BabyPhoto>?) {
        val snapshot = e?.snapshot()
        if (snapshot == null) {
            savedState.remove<IntArray>(KEY_TOURNAMENT)
            savedState.remove<Int>(KEY_AI_TOP_PICK)
            savedState.remove<String>(KEY_MONTH)
            savedState.remove<Long>(KEY_CHILD)
            return
        }
        savedState[KEY_TOURNAMENT] = TournamentSnapshots.encode(snapshot.map { it.id })
        savedState[KEY_AI_TOP_PICK] = aiTopPickId ?: 0
        savedState[KEY_MONTH] = playingMonth
        savedState[KEY_CHILD] = playingChildId ?: 0L
    }

    /**
     * Android may end the app while the family is in another app mid-bracket.
     * Coming back, put them on the match they left, as long as every photo in
     * it still exists; otherwise forget the bracket.
     */
    private fun restoreTournament() {
        val saved = TournamentSnapshots.decode(savedState.get<IntArray>(KEY_TOURNAMENT)) ?: return
        viewModelScope.launch {
            val photos = (saved.pool + saved.winners).distinct().associateWith { repository.getPhotoById(it) }
            val restored = if (photos.values.all { it != null }) {
                TournamentEngine.restore(saved.map { photos.getValue(it)!! })
            } else {
                null
            }
            if (restored == null || currentScreen != UiScreen.Dashboard) {
                Log.w(TAG, "could not resume the world cup")
                saveTournament(null)
                return@launch
            }
            aiTopPickId = savedState.get<Int>(KEY_AI_TOP_PICK)?.takeIf { it > 0 }
            playingMonth = savedState.get<String>(KEY_MONTH)
            playingChildId = savedState.get<Long>(KEY_CHILD)?.takeIf { it > 0 }
            engine = restored
            syncMatchState(restored)
            navigateAsRoot(UiScreen.WorldCupPlay)
        }
    }

    /** Did the family's winner match the AI #1? Null for hand-picked brackets. */
    fun aiMatchForWinner(winnerId: Int): Boolean? = comparePicks(winnerId, aiTopPickId)?.matched

    // ================= ③ 인화함 =================

    fun addToPrintCart(photo: BabyPhoto) {
        viewModelScope.launch { repository.setInPrintCart(photo.id, true) }
    }

    fun updatePrintCartItem(photo: BabyPhoto, size: String, qty: Int) {
        viewModelScope.launch { repository.updatePrintOptions(photo.id, size, qty.coerceIn(1, 99)) }
    }

    fun removeFromPrintCart(photo: BabyPhoto) {
        viewModelScope.launch { repository.setInPrintCart(photo.id, false) }
    }

    fun clearPrintCart() {
        viewModelScope.launch { repository.clearPrintCart() }
    }

    companion object {
        private const val TAG = "BabyCupViewModel"
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
        private const val MAX_CHILD_NAME = 20
        /** Name for the pre-v6 baby profile that had a birthday but no name. */
        private const val DEFAULT_CHILD_NAME = "우리 아기"
        private const val KEY_TOURNAMENT = "tournament"
        private const val KEY_AI_TOP_PICK = "tournament_ai_top_pick"
        private const val KEY_MONTH = "tournament_month"
        private const val KEY_CHILD = "tournament_child"

        /** Title of photos the parent added by hand with the Photo Picker. */
        const val PICKED_PHOTO_TITLE = "직접 고른 사진"

        const val STEP_FACE_MODEL = "얼굴 인식 기능을 준비하는 중이에요"
        const val STEP_SCAN = "베베컵이 최근 사진을 찾는 중이에요"
        const val STEP_ANALYZE = "베베컵이 흔들림·표정·눈을 살펴보는 중이에요"
        const val STEP_CLUSTER = "베베컵이 비슷한 사진을 묶는 중이에요"
        const val STEP_SHORTLIST = "베베컵이 베스트 후보를 정리하는 중이에요"
        const val STEP_FACE_ID = "베베컵이 누구 얼굴인지 살펴보는 중이에요"
    }
}

/** The on-device face model (ML Kit), as 설정 shows it. */
enum class FaceModelState { UNKNOWN, CHECKING, READY, NOT_INSTALLED, DOWNLOADING, FAILED, UNAVAILABLE }

/**
 * 아이 얼굴 구분 (the downloaded face-ID model). UNAVAILABLE: downloaded, but
 * TensorFlow Lite could not run it here (e.g. no Google Play services, play build).
 */
enum class FaceIdState { NOT_INSTALLED, DOWNLOADING, READY, FAILED, UNAVAILABLE }
