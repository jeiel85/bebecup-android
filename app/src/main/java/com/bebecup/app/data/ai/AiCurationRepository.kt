package com.bebecup.app.data.ai

import kotlinx.coroutines.flow.Flow

/**
 * Local-only access to AI curation data (analysis cache, near-duplicate
 * clusters, scan sessions). No method here ever uploads or transmits photo
 * data — all results stay on-device. UI/domain layers depend on this interface
 * so the storage backing can evolve without rippling outward.
 */
interface AiCurationRepository {

    // --- Analysis cache ---
    suspend fun saveAnalysis(analysis: PhotoAnalysisEntity): Long
    suspend fun saveAnalyses(analyses: List<PhotoAnalysisEntity>)
    suspend fun getAnalysis(photoId: Int, version: Int = PhotoAnalysisEntity.CURRENT_ANALYSIS_VERSION): PhotoAnalysisEntity?
    suspend fun getAnalyzedPhotoIds(version: Int = PhotoAnalysisEntity.CURRENT_ANALYSIS_VERSION): List<Int>
    suspend fun getAllAnalyses(version: Int = PhotoAnalysisEntity.CURRENT_ANALYSIS_VERSION): List<PhotoAnalysisEntity>
    suspend fun getTopByScore(limit: Int, version: Int = PhotoAnalysisEntity.CURRENT_ANALYSIS_VERSION): List<PhotoAnalysisEntity>
    fun observeTopByScore(limit: Int, version: Int = PhotoAnalysisEntity.CURRENT_ANALYSIS_VERSION): Flow<List<PhotoAnalysisEntity>>

    // --- Clusters ---
    suspend fun saveClusters(clusters: List<PhotoClusterEntity>)
    fun observeClusters(): Flow<List<PhotoClusterEntity>>

    // --- Sessions ---
    suspend fun startSession(session: AiCurationSessionEntity): Long
    suspend fun updateSession(session: AiCurationSessionEntity)
    suspend fun getSession(id: Int): AiCurationSessionEntity?
    fun observeLatestSession(): Flow<AiCurationSessionEntity?>

    /** Privacy: wipe every local AI artifact (analysis, clusters, sessions). */
    suspend fun deleteAllLocalAnalysisData()
}

/**
 * Room-backed [AiCurationRepository]. Purely local — backed by the on-device
 * [AppDatabase] DAOs.
 */
class RoomAiCurationRepository(
    private val analysisDao: PhotoAnalysisDao,
    private val clusterDao: PhotoClusterDao,
    private val sessionDao: AiCurationSessionDao
) : AiCurationRepository {

    override suspend fun saveAnalysis(analysis: PhotoAnalysisEntity): Long =
        analysisDao.insert(analysis)

    override suspend fun saveAnalyses(analyses: List<PhotoAnalysisEntity>) =
        analysisDao.insertAll(analyses)

    override suspend fun getAnalysis(photoId: Int, version: Int): PhotoAnalysisEntity? =
        analysisDao.getForPhoto(photoId, version)

    override suspend fun getAnalyzedPhotoIds(version: Int): List<Int> =
        analysisDao.getAnalyzedPhotoIds(version)

    override suspend fun getAllAnalyses(version: Int): List<PhotoAnalysisEntity> =
        analysisDao.getAllForVersion(version)

    override suspend fun getTopByScore(limit: Int, version: Int): List<PhotoAnalysisEntity> =
        analysisDao.getTopByScore(version, limit)

    override fun observeTopByScore(limit: Int, version: Int): Flow<List<PhotoAnalysisEntity>> =
        analysisDao.observeTopByScore(version, limit)

    override suspend fun saveClusters(clusters: List<PhotoClusterEntity>) =
        clusterDao.insertAll(clusters)

    override fun observeClusters(): Flow<List<PhotoClusterEntity>> =
        clusterDao.observeAll()

    override suspend fun startSession(session: AiCurationSessionEntity): Long =
        sessionDao.insert(session)

    override suspend fun updateSession(session: AiCurationSessionEntity) =
        sessionDao.update(session)

    override suspend fun getSession(id: Int): AiCurationSessionEntity? =
        sessionDao.getById(id)

    override fun observeLatestSession(): Flow<AiCurationSessionEntity?> =
        sessionDao.observeLatest()

    override suspend fun deleteAllLocalAnalysisData() {
        analysisDao.deleteAll()
        clusterDao.deleteAll()
        sessionDao.deleteAll()
    }
}
