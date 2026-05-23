package com.bebecup.app.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bebecup.app.data.AppDatabase
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.data.BabyPhotoRepository
import com.bebecup.app.data.TournamentRecord
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
            // Not enough: pad with other available photos already added by the user.
            finalSelection.addAll(bestPool)
            val remainingNeeded = userSelectedSize - finalSelection.size
            
            val otherPhotos = photosPool.filter { !it.isSelectedAsBest }.shuffled()
            finalSelection.addAll(otherPhotos.take(remainingNeeded))
            
            if (finalSelection.size < userSelectedSize) {
                // If still not enough, loop existing photos to fill the bracket.
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

    // Prepare a handoff to the ZZIXX (찍스) order page.
    fun simulateTransferToZzixx() {
        viewModelScope.launch {
            isSendingToZzixx = true
            kotlinx.coroutines.delay(900)
            isSendingToZzixx = false
            showZzixxSuccessDialog = true
        }
    }
}
