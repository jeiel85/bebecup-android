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

    suspend fun pruneMockPhotos() = babyPhotoDao.pruneMockPhotos()

    suspend fun insertTournament(record: TournamentRecord): Long = babyPhotoDao.insertTournament(record)
}
