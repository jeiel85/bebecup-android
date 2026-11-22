package com.bebecup.app.data

import kotlinx.coroutines.flow.Flow

class BabyPhotoRepository(private val babyPhotoDao: BabyPhotoDao) {

    val allPhotos: Flow<List<BabyPhoto>> = babyPhotoDao.getAllPhotos()
    val bestPhotos: Flow<List<BabyPhoto>> = babyPhotoDao.getBestPhotos()
    val printCartPhotos: Flow<List<BabyPhoto>> = babyPhotoDao.getPrintCartPhotos()
    val allTournaments: Flow<List<TournamentRecord>> = babyPhotoDao.getAllTournaments()

    suspend fun insertPhoto(photo: BabyPhoto): Long = babyPhotoDao.insertPhoto(photo)

    suspend fun updatePhoto(photo: BabyPhoto) = babyPhotoDao.updatePhoto(photo)

    suspend fun deletePhoto(photo: BabyPhoto) = babyPhotoDao.deletePhoto(photo)

    suspend fun getPhotoById(id: Int): BabyPhoto? = babyPhotoDao.getPhotoById(id)

    suspend fun getPhotoByUri(uriString: String): BabyPhoto? = babyPhotoDao.getPhotoByUri(uriString)

    suspend fun clearSelectedBest() = babyPhotoDao.clearSelectedBest()

    suspend fun markBest(ids: List<Int>, timestamp: Long) {
        if (ids.isNotEmpty()) babyPhotoDao.markBest(ids, timestamp)
    }

    suspend fun unmarkBest(id: Int) = babyPhotoDao.unmarkBest(id)

    suspend fun incrementMatchWins(id: Int) = babyPhotoDao.incrementMatchWins(id)

    suspend fun incrementChampionships(id: Int) = babyPhotoDao.incrementChampionships(id)

    suspend fun setInPrintCart(id: Int, inCart: Boolean) = babyPhotoDao.setInPrintCart(id, inCart)

    suspend fun updatePrintOptions(id: Int, size: String, quantity: Int) =
        babyPhotoDao.updatePrintOptions(id, size, quantity)

    suspend fun clearPrintCart() = babyPhotoDao.clearPrintCart()

    suspend fun updateTakenDate(id: Int, takenDate: String) = babyPhotoDao.updateTakenDate(id, takenDate)

    suspend fun getKeptPhotos(): List<BabyPhoto> = babyPhotoDao.getKeptPhotos()

    suspend fun pruneUnkeptPhotos(): Int = babyPhotoDao.pruneUnkeptPhotos()

    suspend fun deletePhotos(ids: List<Int>) {
        if (ids.isNotEmpty()) babyPhotoDao.deletePhotos(ids)
    }

    suspend fun pruneMockPhotos() = babyPhotoDao.pruneMockPhotos()

    suspend fun insertTournament(record: TournamentRecord): Long = babyPhotoDao.insertTournament(record)
}
