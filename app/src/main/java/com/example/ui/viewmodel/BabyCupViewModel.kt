package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.BabyPhoto
import com.example.data.BabyPhotoRepository
import com.example.data.TournamentRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface UiScreen {
    object Dashboard : UiScreen
    object PhotoManager : UiScreen
    object BestShotSelector : UiScreen      // Activated by simulation of baby push notification alert
    object WorldCupSetup : UiScreen
    object WorldCupPlay : UiScreen
    data class WorldCupWinner(val winner: BabyPhoto, val bracketSize: Int) : UiScreen
    object PrintCart : UiScreen
    object TournamentHistoryList : UiScreen
}

class BabyCupViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BabyPhotoRepository
    
    // Database flows
    val allPhotos: StateFlow<List<BabyPhoto>>
    val bestPhotos: StateFlow<List<BabyPhoto>>
    val printCartPhotos: StateFlow<List<BabyPhoto>>
    val tournaments: StateFlow<List<TournamentRecord>>

    // Navigation and screen states
    var currentScreen by mutableStateOf<UiScreen>(UiScreen.Dashboard)
        private set

    // Alert / Pull Notification Simulation State
    var showNotificationAlert by mutableStateOf(true) // Start with an alert to nudge parents!
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

    init {
        val database = AppDatabase.getDatabase(application)
        repository = BabyPhotoRepository(database.babyPhotoDao())

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

        // Populate database with predefined adorable moments on first launching
        viewModelScope.launch {
            if (repository.getPresetCount() == 0) {
                initializePresets()
            }
        }
    }

    private suspend fun initializePresets() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val presets = listOf(
            BabyPhoto(
                uriString = "preset:smile",
                title = "방긋방긋 백만불짜리 미소",
                description = "아침에 눈 떠서 제일 먼저 보여준 기적 같은 살인 미소. 방안이 다 환해지네!",
                takenDate = today,
                isPreset = true,
                isSelectedAsBest = true, // Place several presets in initial Best Shots pool so they can play immediately!
                bestSelectedTimestamp = System.currentTimeMillis() - 1000
            ),
            BabyPhoto(
                uriString = "preset:sleep",
                title = "천사 같은 우리 집 코잠",
                description = "새근새근 솜처럼 보들보들한 뺨을 베고 단잠 여행 꿈나라 속으로.",
                takenDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(System.currentTimeMillis() - 86400000 * 1)),
                isPreset = true,
                isSelectedAsBest = true,
                bestSelectedTimestamp = System.currentTimeMillis() - 2000
            ),
            BabyPhoto(
                uriString = "preset:cry",
                title = "응애! 분유 늦어져서 서러움 폭발",
                description = "우유병 타오는 0.1초가 서럽다며 굵은 도라지 눈물방울 뚝뚝 흘리던 순간.",
                takenDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(System.currentTimeMillis() - 86400000 * 2)),
                isPreset = true,
                isSelectedAsBest = true,
                bestSelectedTimestamp = System.currentTimeMillis() - 3000
            ),
            BabyPhoto(
                uriString = "preset:eat",
                title = "냠냠 프리미엄 호박미음 먹방",
                description = "입가 주변은 물론 이마까지 노랗게 호박 범벅을 하고도 싱글벙글 한 그릇 완전 비움!",
                takenDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(System.currentTimeMillis() - 86400000 * 3)),
                isPreset = true,
                isSelectedAsBest = true,
                bestSelectedTimestamp = System.currentTimeMillis() - 4000
            ),
            BabyPhoto(
                uriString = "preset:crawl",
                title = "거실 매트 대장정! 첫 기어가기",
                description = "비실비실 엉덩이를 흔들며 혼자 힘으로 거실 매트 가로지르기 당당히 성공!",
                takenDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(System.currentTimeMillis() - 86400000 * 4)),
                isPreset = true
            ),
            BabyPhoto(
                uriString = "preset:peek",
                title = "손수건 치우며 까꿍 극장",
                description = "엄마가 수건으로 얼굴을 가렸다 짠 하면 숨 넘어가게 꺄르르 깔깔 뒤집어져요.",
                takenDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(System.currentTimeMillis() - 86400000 * 5)),
                isPreset = true
            ),
            BabyPhoto(
                uriString = "preset:bath",
                title = "노란 오리와 뜨끈한 반신욕",
                description = "목욕 물에 들어가면 기분 만점! 러버덕 잡으려다 파닥파닥 물장구 엄청 쳤던 저녁.",
                takenDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(System.currentTimeMillis() - 86400000 * 6)),
                isPreset = true
            ),
            BabyPhoto(
                uriString = "preset:toy",
                title = "알록달록 블록 탑 무너뜨리기",
                description = "아빠가 힘들게 쌓아 올린 7단 블록 타워를 고사리손으로 한 방에 박살 내고 위풍당당.",
                takenDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(System.currentTimeMillis() - 86400000 * 7)),
                isPreset = true
            )
        )
        repository.insertPhotos(presets)
    }

    // Navigation trigger methods
    fun navigateTo(screen: UiScreen) {
        currentScreen = screen
    }

    fun dismissNotificationAlert() {
        showNotificationAlert = false
        alertDismissedTime = System.currentTimeMillis()
    }

    fun triggerFakeNotification() {
        showNotificationAlert = true
    }

    // Photo Management actions
    fun addNewPhoto(title: String, description: String, date: String, customPresetId: String? = null) {
        viewModelScope.launch {
            val randomPresets = listOf("preset:smile", "preset:sleep", "preset:cry", "preset:eat", "preset:crawl", "preset:peek", "preset:bath", "preset:toy")
            val selectedUri = customPresetId ?: randomPresets.random()
            
            val newPhoto = BabyPhoto(
                uriString = selectedUri,
                title = title.ifBlank { "아기 순간 포착" },
                description = description,
                takenDate = date,
                isPreset = false,
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

    // World Cup Game Setup & Logic
    fun startWorldCup(photosPool: List<BabyPhoto>, userSelectedSize: Int) {
        bracketSizeSelected = userSelectedSize
        
        // Prepare tournament pool
        // 1. Gather all best shots
        val bestPool = photosPool.filter { it.isSelectedAsBest }.shuffled()
        val finalSelection = mutableListOf<BabyPhoto>()
        
        if (bestPool.size >= userSelectedSize) {
            finalSelection.addAll(bestPool.take(userSelectedSize))
        } else {
            // Not enough: pad with other available photos, then presets
            finalSelection.addAll(bestPool)
            val remainingNeeded = userSelectedSize - finalSelection.size
            
            val otherPhotos = photosPool.filter { !it.isSelectedAsBest }.shuffled()
            finalSelection.addAll(otherPhotos.take(remainingNeeded))
            
            if (finalSelection.size < userSelectedSize) {
                // If still not enough, list all duplicates of presets or loop to fill
                val allAvail = photosPool.shuffled()
                var padIndex = 0
                while (finalSelection.size < userSelectedSize && allAvail.isNotEmpty()) {
                    finalSelection.add(allAvail[padIndex % allAvail.size])
                    padIndex++
                }
            }
        }

        // Shuffle the selected tournament entrants
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

    // Simulate sending upload package to ZZIXX (찍스)
    fun simulateTransferToZzixx() {
        viewModelScope.launch {
            isSendingToZzixx = true
            // Fake beautiful network delay representing package compression, uploading original photos safely!
            kotlinx.coroutines.delay(2800)
            isSendingToZzixx = false
            showZzixxSuccessDialog = true
        }
    }
}
