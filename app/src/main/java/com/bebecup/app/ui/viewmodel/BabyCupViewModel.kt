package com.bebecup.app.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bebecup.app.ai.AiModelAvailability
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
import com.bebecup.app.data.media.PhotoMetadataReader
import com.bebecup.app.domain.model.ShortlistItem
import com.bebecup.app.domain.usecase.AnalyzePhotoQualityUseCase
import com.bebecup.app.domain.usecase.BuildAiShortlistUseCase
import com.bebecup.app.domain.usecase.BuildRejectedListUseCase
import com.bebecup.app.domain.usecase.ClusterDuplicatesUseCase
import com.bebecup.app.domain.usecase.CompareParentPickWithAiPickUseCase
import com.bebecup.app.domain.usecase.ExplainTopPhotosUseCase
import com.bebecup.app.domain.usecase.ScanRecentBabyPhotosUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiScreen {
    object Dashboard : UiScreen
    object PhotoManager : UiScreen
    object BestShotSelector : UiScreen      // Activated by simulation of baby push notification alert
    object WorldCupSetup : UiScreen
    object WorldCupPlay : UiScreen
    data class WorldCupWinner(val winner: BabyPhoto, val bracketSize: Int) : UiScreen
    object PrintCart : UiScreen
    object TournamentHistoryList : UiScreen

    // AI curation (Phase 4)
    object AiScanSetup : UiScreen
    object AiScanProgress : UiScreen
    object AiCurationResult : UiScreen
    object Settings : UiScreen
}

class BabyCupViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BabyPhotoRepository

    // AI curation collaborators (all on-device; no upload).
    private val aiRepository: RoomAiCurationRepository
    private val scanRecentPhotos: ScanRecentBabyPhotosUseCase
    private val analyzePhotoQuality: AnalyzePhotoQualityUseCase
    private val clusterDuplicates: ClusterDuplicatesUseCase
    private val explainTopPhotos: ExplainTopPhotosUseCase
    private val buildAiShortlist: BuildAiShortlistUseCase
    private val buildRejectedList: BuildRejectedListUseCase

    // Database flows
    val allPhotos: StateFlow<List<BabyPhoto>>
    val bestPhotos: StateFlow<List<BabyPhoto>>
    val printCartPhotos: StateFlow<List<BabyPhoto>>
    val tournaments: StateFlow<List<TournamentRecord>>

    // Navigation and screen states
    var currentScreen by mutableStateOf<UiScreen>(UiScreen.Dashboard)
        private set

    // Best-shot reminder state
    var showNotificationAlert by mutableStateOf(false)
    var alertDismissedTime by mutableStateOf(0L)

    // Selection screen states (photo picker candidates)
    var isUploadingPhoto by mutableStateOf(false)

    // Tournament configuration & gameplay states
    var bracketSizeSelected by mutableStateOf(4) // 4, 8, 16

    // Live Game variables
    var currentMatchIndex by mutableStateOf(0)
    var currentRoundName by mutableStateOf("준결승전 (4강)")
    var leftCandidate by mutableStateOf<BabyPhoto?>(null)
    var rightCandidate by mutableStateOf<BabyPhoto?>(null)
    private var tournamentContestants = mutableListOf<BabyPhoto>()
    private var nextRoundWinners = mutableListOf<BabyPhoto>()

    // Stats and print screen simulator states
    var isSendingToZzixx by mutableStateOf(false)
    var showZzixxSuccessDialog by mutableStateOf(false)

    // --- AI curation preferences (persisted across launches) ---
    private val prefs = application.getSharedPreferences("bebecup_prefs", Context.MODE_PRIVATE)

    var aiScanRangeDays by mutableStateOf(prefs.getInt(PREF_SCAN_RANGE, 7)) // 1 / 7 / 30
        private set
    var aiSleepingModeEnabled by mutableStateOf(prefs.getBoolean(PREF_SLEEPING, false))
        private set
    var aiSimilarOnly by mutableStateOf(prefs.getBoolean(PREF_SIMILAR_ONLY, true))
        private set
    var aiExcludeBlurry by mutableStateOf(prefs.getBoolean(PREF_EXCLUDE_BLURRY, true))
        private set

    fun updateScanRangeDays(days: Int) {
        aiScanRangeDays = days
        prefs.edit().putInt(PREF_SCAN_RANGE, days).apply()
    }

    fun updateSleepingMode(enabled: Boolean) {
        aiSleepingModeEnabled = enabled
        prefs.edit().putBoolean(PREF_SLEEPING, enabled).apply()
    }

    fun updateSimilarOnly(enabled: Boolean) {
        aiSimilarOnly = enabled
        prefs.edit().putBoolean(PREF_SIMILAR_ONLY, enabled).apply()
    }

    fun updateExcludeBlurry(enabled: Boolean) {
        aiExcludeBlurry = enabled
        prefs.edit().putBoolean(PREF_EXCLUDE_BLURRY, enabled).apply()
    }

    // --- AI curation transient state ---
    var aiIsScanning by mutableStateOf(false)
    var aiProgressStep by mutableStateOf("")
    var aiAnalyzedCount by mutableStateOf(0)
    var aiTotalCount by mutableStateOf(0)
    var aiRejectedCount by mutableStateOf(0)
    var aiClusterCount by mutableStateOf(0)
    var aiShortlist by mutableStateOf<List<ShortlistItem>>(emptyList())
    var aiRejectedItems by mutableStateOf<List<ShortlistItem>>(emptyList())
    val aiApprovedIds = mutableStateListOf<Int>()

    // AI #1 pick for the current tournament (null when tournament isn't AI-sourced).
    var aiTopPickId by mutableStateOf<Int?>(null)
        private set
    var aiTopPickTitle by mutableStateOf<String?>(null)
        private set
    private val comparePicks = CompareParentPickWithAiPickUseCase()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = BabyPhotoRepository(database.babyPhotoDao())

        aiRepository = RoomAiCurationRepository(
            analysisDao = database.photoAnalysisDao(),
            clusterDao = database.photoClusterDao(),
            sessionDao = database.aiCurationSessionDao()
        )
        val mediaSource = MediaStorePhotoSource(application)
        val metadataReader = PhotoMetadataReader(application)
        val photoQualityAnalyzer = PhotoQualityAnalyzer(metadataReader, FaceQualityAnalyzer())
        scanRecentPhotos = ScanRecentBabyPhotosUseCase(repository, mediaSource)
        analyzePhotoQuality = AnalyzePhotoQualityUseCase(aiRepository, photoQualityAnalyzer)
        clusterDuplicates = ClusterDuplicatesUseCase(aiRepository)
        explainTopPhotos = ExplainTopPhotosUseCase(
            repository, aiRepository, AiModelAvailability().explainer()
        )
        buildAiShortlist = BuildAiShortlistUseCase(repository, aiRepository)
        buildRejectedList = BuildRejectedListUseCase(repository, aiRepository)

        allPhotos = repository.allPhotos.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        bestPhotos = repository.bestPhotos.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        printCartPhotos = repository.printCartPhotos.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        tournaments = repository.allTournaments.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Clean up legacy mock database records on startup
        viewModelScope.launch {
            try {
                repository.pruneMockPhotos()
            } catch (e: Exception) {
                // Ignore any DB checks on startup
            }
        }
    }

    // Navigation trigger methods
    fun navigateTo(screen: UiScreen) {
        currentScreen = screen
    }

    fun dismissNotificationAlert() {
        showNotificationAlert = false
        alertDismissedTime = System.currentTimeMillis()
    }

    fun triggerBestShotReminder() {
        showNotificationAlert = true
    }

    // Photo Management actions
    fun addNewPhoto(uriString: String, title: String, description: String, date: String) {
        viewModelScope.launch {
            val newPhoto = BabyPhoto(
                uriString = uriString,
                title = title.ifBlank { "아기 순간 포착" },
                description = description,
                takenDate = date,
                isSelectedAsBest = false
            )
            repository.insertPhoto(newPhoto)
        }
    }

    fun deletePhoto(photo: BabyPhoto) {
        viewModelScope.launch {
            repository.deletePhoto(photo)
        }
    }

    fun toggleBestShotSelectionInDb(photo: BabyPhoto) {
        viewModelScope.launch {
            val updated = photo.copy(
                isSelectedAsBest = !photo.isSelectedAsBest,
                bestSelectedTimestamp = if (!photo.isSelectedAsBest) System.currentTimeMillis() else 0L
            )
            repository.updatePhoto(updated)
        }
    }

    // Submitting batch from Best Shots Selective Picker
    fun saveBatchBestShots(selectedPhotoIds: Set<Int>) {
        viewModelScope.launch {
            // Fetch all photos currently in DB
            allPhotos.value.forEach { photo ->
                if (photo.id in selectedPhotoIds) {
                    val updated = photo.copy(
                        isSelectedAsBest = true,
                        bestSelectedTimestamp = System.currentTimeMillis()
                    )
                    repository.updatePhoto(updated)
                }
            }
            dismissNotificationAlert()
            navigateTo(UiScreen.Dashboard)
        }
    }

    // ===================== AI CURATION =====================

    /**
     * Manual, foreground AI curation (spec §10.1): scan the recent gallery,
     * analyze on-device, build a shortlist, then show the result. The caller
     * must have already obtained photo-read permission.
     */
    fun startAiCuration() {
        if (aiIsScanning) return
        viewModelScope.launch {
            aiIsScanning = true
            aiAnalyzedCount = 0
            aiTotalCount = 0
            aiRejectedCount = 0
            aiClusterCount = 0
            // AI state must not outlive its session (avoids a stale AI #1 pick
            // leaking into a later winner-match comparison).
            aiTopPickId = null
            aiTopPickTitle = null
            aiProgressStep = STEP_SCAN
            navigateTo(UiScreen.AiScanProgress)

            val endMillis = System.currentTimeMillis()
            val startMillis = endMillis - aiScanRangeDays.toLong() * 24L * 60L * 60L * 1000L

            var sessionId = 0
            try {
                sessionId = aiRepository.startSession(
                    AiCurationSessionEntity(
                        scanRangeStartMillis = startMillis,
                        scanRangeEndMillis = endMillis,
                        status = CurationStatus.SCANNING,
                        createdAtMillis = endMillis
                    )
                ).toInt()

                val photos = scanRecentPhotos(startMillis, endMillis, includeScreenshots = false)
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

                // Near-duplicate grouping (spec §8.8) — demotes redundant burst shots
                // before the shortlist is built. Honors the "비슷한 사진은 한 장만 추천" toggle.
                if (aiSimilarOnly) {
                    aiProgressStep = STEP_CLUSTER
                    aiClusterCount = clusterDuplicates(System.currentTimeMillis())
                }

                // Natural-language explanation for the top photos only (spec §8.10).
                explainTopPhotos(ExplainTopPhotosUseCase.DEFAULT_LIMIT)

                aiProgressStep = STEP_SHORTLIST
                // Scope results + counts to THIS scan's photos. Analysis rows
                // accumulate across scans/ranges, so an unscoped count would be
                // incoherent (e.g. "제외 0장" on a re-scan, or counts spanning
                // ranges that don't match aiTotalCount shown above).
                val scannedIds = photos.map { it.id }.toSet()
                val analyzedInScan = aiRepository.getAnalyzedPhotoIds().count { it in scannedIds }

                val shortlist = buildAiShortlist(BuildAiShortlistUseCase.DEFAULT_LIMIT, scannedIds)
                aiShortlist = shortlist
                aiRejectedItems = buildRejectedList(
                    shortlistLimit = BuildAiShortlistUseCase.DEFAULT_LIMIT,
                    photoIds = scannedIds
                )
                aiApprovedIds.clear()
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
                navigateTo(UiScreen.AiCurationResult)
            } catch (e: Exception) {
                if (sessionId != 0) {
                    aiRepository.getSession(sessionId)?.let { s ->
                        aiRepository.updateSession(s.copy(status = CurationStatus.FAILED))
                    }
                }
                navigateTo(UiScreen.Dashboard)
            } finally {
                aiIsScanning = false
            }
        }
    }

    fun toggleShortlistApproval(photoId: Int) {
        if (aiApprovedIds.contains(photoId)) aiApprovedIds.remove(photoId)
        else aiApprovedIds.add(photoId)
    }

    /** Start a tournament from the parent-approved AI shortlist (spec §11.6). */
    fun startWorldCupFromShortlist(userSelectedSize: Int) {
        val approvedItems = aiShortlist.filter { aiApprovedIds.contains(it.photo.id) }
        if (approvedItems.isEmpty()) return
        // AI #1 = the highest-scoring photo AMONG the approved set (shortlist is
        // score-desc). Using the approved top means the "did AI agree?" result is
        // meaningful even when the parent excludes the overall #1.
        approvedItems.first().let {
            aiTopPickId = it.photo.id
            aiTopPickTitle = it.photo.title
        }
        bracketSizeSelected = userSelectedSize
        launchTournament(primary = approvedItems.map { it.photo }, fallback = allPhotos.value, userSelectedSize)
    }

    /**
     * Whether the parent's [winnerId] matches the AI's #1 pick (spec §11.7).
     * Null when the tournament wasn't started from the AI shortlist.
     */
    fun aiMatchForWinner(winnerId: Int): Boolean? =
        comparePicks(winnerId, aiTopPickId)?.matched

    /** Privacy (spec §9.3): wipe all on-device analysis/clusters/sessions. */
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

    // World Cup Game Setup & Logic
    fun startWorldCup(photosPool: List<BabyPhoto>, userSelectedSize: Int) {
        // Manual tournament — no AI pick to compare against.
        aiTopPickId = null
        aiTopPickTitle = null
        bracketSizeSelected = userSelectedSize
        val best = photosPool.filter { it.isSelectedAsBest }
        val others = photosPool.filter { !it.isSelectedAsBest }
        launchTournament(primary = best, fallback = others, userSelectedSize)
    }

    /**
     * Build a [userSelectedSize]-entry bracket from [primary] first, padding
     * with [fallback] and finally looping if still short, then kick off play.
     */
    private fun launchTournament(primary: List<BabyPhoto>, fallback: List<BabyPhoto>, userSelectedSize: Int) {
        // Guard: with no photos at all there is nothing to play — bail before
        // setupMatchPair() would hit `.last()` on an empty contestant list.
        if (primary.isEmpty() && fallback.isEmpty()) return

        val finalSelection = mutableListOf<BabyPhoto>()
        val primaryShuffled = primary.shuffled()

        if (primaryShuffled.size >= userSelectedSize) {
            finalSelection.addAll(primaryShuffled.take(userSelectedSize))
        } else {
            finalSelection.addAll(primaryShuffled)
            val remainingNeeded = userSelectedSize - finalSelection.size
            finalSelection.addAll(fallback.shuffled().take(remainingNeeded))

            if (finalSelection.size < userSelectedSize) {
                val allAvail = (primary + fallback).shuffled()
                var padIndex = 0
                while (finalSelection.size < userSelectedSize && allAvail.isNotEmpty()) {
                    finalSelection.add(allAvail[padIndex % allAvail.size])
                    padIndex++
                }
            }
        }

        tournamentContestants = finalSelection.shuffled().toMutableList()
        nextRoundWinners = mutableListOf()
        currentMatchIndex = 0
        updateRoundName(tournamentContestants.size)

        setupMatchPair()
        navigateTo(UiScreen.WorldCupPlay)
    }

    private fun setupMatchPair() {
        if (tournamentContestants.size >= 2 * (currentMatchIndex + 1)) {
            leftCandidate = tournamentContestants[2 * currentMatchIndex]
            rightCandidate = tournamentContestants[2 * currentMatchIndex + 1]
        } else if (tournamentContestants.size == 1) {
            // Ultimate single winner
            declareChampionship(tournamentContestants.first())
        } else {
            // Odd number of contestants? Direct pass for the last odd one
            val byeEntrant = tournamentContestants.last()
            nextRoundWinners.add(byeEntrant)
            progressToNextRound()
        }
    }

    fun submitMatchVote(chosenCandidate: BabyPhoto) {
        viewModelScope.launch {
            // Update stats in DB (increment match selections won)
            val updated = chosenCandidate.copy(matchesCount = chosenCandidate.matchesCount + 1)
            repository.updatePhoto(updated)

            nextRoundWinners.add(updated)
            currentMatchIndex++

            if (2 * currentMatchIndex >= tournamentContestants.size) {
                progressToNextRound()
            } else {
                setupMatchPair()
            }
        }
    }

    private fun progressToNextRound() {
        if (nextRoundWinners.size == 1) {
            // We have a winner!
            declareChampionship(nextRoundWinners.first())
        } else {
            // Move winners of this round to the next round pool
            tournamentContestants = nextRoundWinners.toMutableList()
            nextRoundWinners = mutableListOf()
            currentMatchIndex = 0
            updateRoundName(tournamentContestants.size)
            setupMatchPair()
        }
    }

    private fun updateRoundName(poolSize: Int) {
        currentRoundName = when (poolSize) {
            16 -> "16강전"
            8 -> "8강전"
            4 -> "준결승전 (4강)"
            2 -> "★ 대망의 결승전 ★"
            else -> "${poolSize}강전"
        }
    }

    private fun declareChampionship(winner: BabyPhoto) {
        viewModelScope.launch {
            // Increment overall trophies/wins Count for winner baby photo
            val championPhotoObj = winner.copy(winsCount = winner.winsCount + 1)
            repository.updatePhoto(championPhotoObj)

            // Save past tournament history record
            val record = TournamentRecord(
                winnerPhotoId = championPhotoObj.id,
                winnerTitle = championPhotoObj.title,
                winnerUriString = championPhotoObj.uriString,
                totalParticipants = bracketSizeSelected,
                tournamentTitle = "베스트 아기사진 이상형 월드컵"
            )
            repository.insertTournament(record)

            navigateTo(UiScreen.WorldCupWinner(championPhotoObj, bracketSizeSelected))
        }
    }

    // Print Cart actions
    fun addWinnerToPrintCart(winnerPhoto: BabyPhoto, size: String = "4x6", qty: Int = 1) {
        viewModelScope.launch {
            val updateObj = winnerPhoto.copy(
                isInPrintCart = true,
                printSize = size,
                printQuantity = qty
            )
            repository.updatePhoto(updateObj)
        }
    }

    fun updatePrintCartItem(photo: BabyPhoto, size: String, qty: Int) {
        viewModelScope.launch {
            val updateObj = photo.copy(
                printSize = size,
                printQuantity = qty
            )
            repository.updatePhoto(updateObj)
        }
    }

    fun removePhotoFromPrintCart(photo: BabyPhoto) {
        viewModelScope.launch {
            val updateObj = photo.copy(
                isInPrintCart = false
            )
            repository.updatePhoto(updateObj)
        }
    }

    fun clearPrintCart() {
        viewModelScope.launch {
            printCartPhotos.value.forEach { photo ->
                val updated = photo.copy(isInPrintCart = false)
                repository.updatePhoto(updated)
            }
        }
    }

    // Prepare a handoff to the ZZIXX (찍스) order page.
    fun simulateTransferToZzixx() {
        viewModelScope.launch {
            isSendingToZzixx = true
            kotlinx.coroutines.delay(900)
            isSendingToZzixx = false
            showZzixxSuccessDialog = true
        }
    }

    companion object {
        private const val PREF_SCAN_RANGE = "ai_scan_range_days"
        private const val PREF_SLEEPING = "ai_sleeping_mode"
        private const val PREF_SIMILAR_ONLY = "ai_similar_only"
        private const val PREF_EXCLUDE_BLURRY = "ai_exclude_blurry"

        const val STEP_SCAN = "최근 사진을 찾는 중이에요"
        const val STEP_ANALYZE = "흔들림·얼굴·눈 상태를 확인하는 중이에요"
        const val STEP_CLUSTER = "비슷한 사진을 묶는 중이에요"
        const val STEP_SHORTLIST = "베스트 후보를 정리하는 중이에요"
    }
}
