package com.bebecup.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bebecup.app.ai.AiModelAvailability
import com.bebecup.app.ai.FaceModelInstaller
import com.bebecup.app.ai.FaceQualityAnalyzer
import com.bebecup.app.ai.PhotoQualityAnalyzer
import com.bebecup.app.data.AppDatabase
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.data.BabyPhotoRepository
import com.bebecup.app.data.TournamentRecord
import com.bebecup.app.data.ai.AiCurationSessionEntity
import com.bebecup.app.data.ai.CurationStatus
import com.bebecup.app.data.ai.RoomAiCurationRepository
import com.bebecup.app.data.media.MediaStorePhotoSource
import com.bebecup.app.data.media.PhotoAccess
import com.bebecup.app.data.media.PhotoMetadataReader
import com.bebecup.app.data.prefs.BebecupPrefs
import com.bebecup.app.domain.model.ShortlistItem
import com.bebecup.app.domain.monthly.MonthlyCup
import com.bebecup.app.domain.nudge.NudgePolicy
import com.bebecup.app.domain.profile.BabyAge
import com.bebecup.app.domain.profile.CalendarDate
import com.bebecup.app.domain.tournament.TournamentEngine
import com.bebecup.app.domain.usecase.AnalyzePhotoQualityUseCase
import com.bebecup.app.domain.usecase.BuildAiShortlistUseCase
import com.bebecup.app.domain.usecase.BuildRejectedListUseCase
import com.bebecup.app.domain.usecase.ClusterDuplicatesUseCase
import com.bebecup.app.domain.usecase.CompareParentPickWithAiPickUseCase
import com.bebecup.app.domain.usecase.ExplainTopPhotosUseCase
import com.bebecup.app.domain.usecase.ScanRecentBabyPhotosUseCase
import com.bebecup.app.nudge.NudgeNotifier
import com.bebecup.app.nudge.NudgeScheduler
import com.bebecup.app.nudge.UnsortedPhotoCounter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    /** [monthKey] is set for a 이달의 월드컵 ("yyyy-MM"). */
    data class WorldCupWinner(val winner: BabyPhoto, val bracketSize: Int, val monthKey: String? = null) : UiScreen
    object TournamentHistoryList : UiScreen

    // ③ 인화
    object PrintCart : UiScreen

    object Settings : UiScreen
}

class BabyCupViewModel(application: Application) : AndroidViewModel(application) {

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

    // ---------------- Baby profile (on this device only) ----------------

    var babyName by mutableStateOf(prefs.babyName)
        private set
    /** "yyyy-MM-dd", or "" when the parent has not given it. */
    var babyBirthDate by mutableStateOf(prefs.babyBirthDate)
        private set
    var babyProfilePromptDismissed by mutableStateOf(prefs.babyProfilePromptDismissed)
        private set

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
        scanRecentPhotos = ScanRecentBabyPhotosUseCase(repository, mediaSource)
        analyzePhotoQuality = AnalyzePhotoQualityUseCase(aiRepository, photoQualityAnalyzer)
        clusterDuplicates = ClusterDuplicatesUseCase(aiRepository)
        explainTopPhotos = ExplainTopPhotosUseCase(repository, aiRepository, AiModelAvailability().explainer())
        buildAiShortlist = BuildAiShortlistUseCase(repository, aiRepository)
        buildRejectedList = BuildRejectedListUseCase(repository, aiRepository)

        if (!prefs.onboardingSeen) currentScreen = UiScreen.Onboarding

        bestPhotos = repository.bestPhotos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        printCartPhotos = repository.printCartPhotos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        tournaments = repository.allTournaments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        fixPickedPhotoDates()
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
        currentScreen = screen
    }

    /** Replace the current screen without changing depth (transient hand-offs). */
    private fun navigateReplace(screen: UiScreen) {
        currentScreen = screen
    }

    fun navigateHome() {
        backStack.clear()
        currentScreen = UiScreen.Dashboard
    }

    /** Land on [screen] with only home beneath it (terminal screens). */
    private fun navigateAsRoot(screen: UiScreen) {
        backStack.clear()
        backStack.add(UiScreen.Dashboard)
        currentScreen = screen
    }

    /** Pop one level; false at the root so the OS can leave the app. */
    fun navigateBack(): Boolean {
        if (backStack.isEmpty()) return false
        currentScreen = backStack.removeAt(backStack.size - 1)
        return true
    }

    /**
     * Opened from a reminder: "사진이 쌓였어요" goes straight to sorting,
     * "N월의 월드컵" to that month's cup setup.
     */
    fun handleLaunchIntent(intent: Intent?) {
        if (intent == null) return
        val month = intent.getStringExtra(NudgeNotifier.EXTRA_OPEN_MONTHLY_CUP)
        val curation = intent.getBooleanExtra(NudgeNotifier.EXTRA_OPEN_CURATION, false)
        intent.removeExtra(NudgeNotifier.EXTRA_OPEN_MONTHLY_CUP)
        intent.removeExtra(NudgeNotifier.EXTRA_OPEN_CURATION)
        if (!prefs.onboardingSeen) return
        when {
            month != null -> {
                navigateHome()
                openWorldCup(month)
            }
            curation -> {
                navigateHome()
                navigateTo(UiScreen.AiScanSetup)
            }
        }
    }

    // ================= Onboarding & nudge =================

    fun completeOnboarding() {
        prefs.onboardingSeen = true
        // Start fetching the face model now so the first scan doesn't wait.
        faceModelInstaller.requestInBackground()
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

    // ================= 아기 정보 =================

    /**
     * Save the baby's name ("" to leave it out) and birthday ("yyyy-MM-dd",
     * or null to clear it). Both stay in this app's private storage.
     */
    fun updateBabyProfile(name: String, birthDate: String?) {
        val trimmed = name.trim().take(MAX_BABY_NAME)
        val birth = CalendarDate.parse(birthDate)?.toString() ?: ""
        prefs.babyName = trimmed
        prefs.babyBirthDate = birth
        babyName = trimmed
        babyBirthDate = birth
    }

    fun dismissBabyProfilePrompt() {
        prefs.babyProfilePromptDismissed = true
        babyProfilePromptDismissed = true
    }

    /** How old the baby was in [photo]; null without a birthday or for photos before it. */
    fun ageOf(photo: BabyPhoto): BabyAge? = BabyAge.on(babyBirthDate, photo.takenDate)

    /** How old the baby is today; null without a birthday. */
    fun ageToday(): BabyAge? = BabyAge.on(
        babyBirthDate,
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
    )

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
                    aiFaceModelMissing = true
                    aiScanFailed = true
                    return@launch
                }
                aiFaceModelProgress = null
                aiProgressStep = STEP_SCAN

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

                explainTopPhotos(ExplainTopPhotosUseCase.DEFAULT_LIMIT)

                aiProgressStep = STEP_SHORTLIST
                val scannedIds = photos.map { it.id }.toSet()
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

    fun toggleShortlistApproval(photoId: Int) {
        if (aiApprovedIds.contains(photoId)) aiApprovedIds.remove(photoId) else aiApprovedIds.add(photoId)
    }

    /** Approved items from the round, best-first (shortlist, then rescued rejects). */
    private fun approvedItems(): List<ShortlistItem> =
        (aiShortlist + aiRejectedItems).filter { aiApprovedIds.contains(it.photo.id) }

    /**
     * Save the round: approved photos go into the 보관함 and the photos up to
     * the end of this scan count as "확인함" (the nudge resets). With
     * [thenPlay], immediately start a world cup with the approved photos.
     */
    fun saveCurationRound(thenPlay: Boolean) {
        val approved = approvedItems()
        val endMillis = aiScanWindowEndMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
        viewModelScope.launch {
            repository.markBest(approved.map { it.photo.id }, System.currentTimeMillis())
            prefs.lastReviewedAtMillis = maxOf(prefs.lastReviewedAtMillis, endMillis)
            lastReviewedAtMillis = prefs.lastReviewedAtMillis
            unsortedCount = if (canReadPhotos) 0 else null

            if (thenPlay && approved.size >= 2) {
                // `approved` is score-ordered, so the bracket takes the top N
                // and its first entry is the AI #1 the family is compared with.
                val size = TournamentEngine.availableSizes(approved.size).last()
                val entrants = approved.take(size)
                aiTopPickId = entrants.first().photo.id
                playingMonth = null
                launchTournament(entrants.map { it.photo }, asRoot = true)
            } else {
                navigateAsRoot(UiScreen.BestBox)
            }
            refreshHomeStatus()
        }
    }

    /** Privacy: wipe all on-device analysis/clusters/sessions. */
    fun deleteAllAnalysisData() {
        viewModelScope.launch {
            aiRepository.deleteAllLocalAnalysisData()
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
        }
    }

    /**
     * Photos picked by hand before v0.8 were stored with the day they were
     * picked. Re-read their capture date once so ages come out right; a photo
     * that can no longer be opened keeps its old date.
     */
    private fun fixPickedPhotoDates() {
        if (prefs.pickedPhotoDatesFixed) return
        viewModelScope.launch {
            repository.getPhotosByTitle(PICKED_PHOTO_TITLE).forEach { photo ->
                val taken = metadataReader.readTakenDate(Uri.parse(photo.uriString))
                if (taken != null && taken != photo.takenDate) repository.updateTakenDate(photo.id, taken)
            }
            prefs.pickedPhotoDatesFixed = true
        }
    }

    fun removeFromBest(photo: BabyPhoto) {
        viewModelScope.launch { repository.unmarkBest(photo.id) }
    }

    // ================= ② 월드컵 =================

    /** Open the cup setup, on [month]'s photos ("yyyy-MM") or on the latest saved ones. */
    fun openWorldCup(month: String? = null) {
        worldCupMonth = month
        navigateTo(UiScreen.WorldCupSetup)
    }

    fun selectWorldCupMonth(month: String?) {
        worldCupMonth = month
    }

    /** Last month's cup when it is due (home card); see [MonthlyCup.pending]. */
    fun pendingMonthlyCup(best: List<BabyPhoto>, records: List<TournamentRecord>): MonthlyCup.Month? =
        MonthlyCup.pending(
            today = MonthlyCup.today(),
            takenDates = best.map { it.takenDate },
            decided = records.mapNotNull { it.monthKey }.toSet(),
            skippedKey = monthlyCupSkippedMonth
        )

    /** "10월", with the year when it isn't this year. */
    fun monthLabel(monthKey: String): String = MonthlyCup.label(monthKey, MonthlyCup.today().year)

    /** The parent passed on [month]'s cup; the home card stays away until next month. */
    fun skipMonthlyCup(month: String) {
        prefs.monthlyCupSkippedMonth = month
        monthlyCupSkippedMonth = month
    }

    /**
     * Photos a cup would draw from, most recently saved first: those taken in
     * [month], or all saved photos when [month] is null.
     */
    fun worldCupPool(best: List<BabyPhoto>, month: String?): List<BabyPhoto> =
        best.filter { month == null || MonthlyCup.monthKeyOf(it.takenDate) == month }
            .sortedByDescending { it.bestSelectedTimestamp }

    /** Bracket of [size] from [month]'s saved photos, or the most recently saved ones. */
    fun startWorldCup(size: Int, month: String? = worldCupMonth) {
        val pool = worldCupPool(bestPhotos.value, month)
        if (pool.size < 2) return
        aiTopPickId = null
        playingMonth = month
        launchTournament(pool.take(size.coerceAtMost(pool.size)).shuffled(), asRoot = false)
    }

    private fun launchTournament(entrants: List<BabyPhoto>, asRoot: Boolean) {
        val newEngine = TournamentEngine(entrants.shuffled())
        engine = newEngine
        syncMatchState(newEngine)
        if (asRoot) navigateAsRoot(UiScreen.WorldCupPlay) else navigateTo(UiScreen.WorldCupPlay)
    }

    /** Record a vote. A stale or duplicate tap for an already-decided match is ignored. */
    fun vote(match: TournamentEngine.Match<BabyPhoto>, winner: BabyPhoto) {
        val e = engine ?: return
        if (!e.vote(match, winner)) return
        viewModelScope.launch { repository.incrementMatchWins(winner.id) }
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
        val month = playingMonth
        // Show the winner right away; recording it must not hold the screen
        // hostage to disk I/O (a slow device would sit on a blank play screen).
        navigateAsRoot(UiScreen.WorldCupWinner(winner.copy(winsCount = winner.winsCount + 1), bracketSize, month))
        viewModelScope.launch {
            try {
                repository.incrementChampionships(winner.id)
                repository.insertTournament(
                    TournamentRecord(
                        winnerPhotoId = winner.id,
                        winnerTitle = winner.title,
                        winnerUriString = winner.uriString,
                        totalParticipants = bracketSize,
                        tournamentTitle = month?.let { "${MonthlyCup.label(it, withYear = true)}의 월드컵" }
                            ?: "베스트 아기사진 이상형 월드컵",
                        monthKey = month
                    )
                )
            } catch (e: Exception) {
                // The family still sees their winner; only the hall-of-fame
                // entry is missing, so log it rather than interrupt them.
                Log.e(TAG, "could not record the tournament result", e)
            }
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
        private const val MAX_BABY_NAME = 20

        /** Title of photos the parent added by hand with the Photo Picker. */
        const val PICKED_PHOTO_TITLE = "직접 고른 사진"

        const val STEP_FACE_MODEL = "얼굴 인식 기능을 준비하는 중이에요"
        const val STEP_SCAN = "베베컵이 최근 사진을 찾는 중이에요"
        const val STEP_ANALYZE = "베베컵이 흔들림·표정·눈을 살펴보는 중이에요"
        const val STEP_CLUSTER = "베베컵이 비슷한 사진을 묶는 중이에요"
        const val STEP_SHORTLIST = "베베컵이 베스트 후보를 정리하는 중이에요"
    }
}
