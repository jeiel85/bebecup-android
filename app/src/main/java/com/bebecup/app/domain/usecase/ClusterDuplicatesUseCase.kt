package com.bebecup.app.domain.usecase

import com.bebecup.app.ai.DuplicateClusterer
import com.bebecup.app.ai.DuplicatePenaltyPolicy
import com.bebecup.app.data.ai.AiCurationRepository
import com.bebecup.app.data.ai.PhotoAnalysisEntity
import com.bebecup.app.data.ai.PhotoClusterEntity
import org.json.JSONArray

/**
 * Phase 5: groups near-duplicate photos (spec §8.8), persists the clusters,
 * and demotes every non-representative duplicate (lower score + a gentle reject
 * reason) so the shortlist surfaces one strong shot per burst. Local-only.
 *
 * @return number of clusters that contain more than one photo (the "비슷한 사진 묶음" count).
 */
class ClusterDuplicatesUseCase(
    private val aiRepository: AiCurationRepository
) {
    suspend operator fun invoke(
        nowMillis: Long,
        version: Int = PhotoAnalysisEntity.CURRENT_ANALYSIS_VERSION
    ): Int {
        // Clusters are recomputed each scan; clear the previous run so the
        // photo_clusters table doesn't accumulate stale rows.
        aiRepository.clearClusters()

        val analyses = aiRepository.getAllAnalyses(version)
        if (analyses.size < 2) return 0

        val byId = analyses.associateBy { it.photoId }
        val items = analyses.map { DuplicateClusterer.Item(it.photoId, it.dHash, it.overallScore) }
        val clusters = DuplicateClusterer.cluster(items)

        val clusterEntities = ArrayList<PhotoClusterEntity>()
        var duplicateGroups = 0

        for (cluster in clusters) {
            if (!cluster.isDuplicateGroup) continue
            duplicateGroups++

            for (memberId in cluster.memberIds) {
                if (memberId == cluster.representativeId) continue
                val entity = byId[memberId] ?: continue
                val demoted = DuplicatePenaltyPolicy.demote(entity)
                aiRepository.saveAnalysis(withDuplicateReason(demoted))
            }

            clusterEntities += PhotoClusterEntity(
                clusterKey = "dhash-${cluster.representativeId}",
                representativePhotoId = cluster.representativeId,
                photoIdsJson = JSONArray(cluster.memberIds).toString(),
                createdAtMillis = nowMillis
            )
        }

        if (clusterEntities.isNotEmpty()) aiRepository.saveClusters(clusterEntities)
        return duplicateGroups
    }

    /** Append the duplicate reject reason if not already present. */
    private fun withDuplicateReason(entity: PhotoAnalysisEntity): PhotoAnalysisEntity {
        val reasons = try {
            val arr = JSONArray(entity.rejectReasonsJson)
            MutableList(arr.length()) { arr.getString(it) }
        } catch (e: Exception) {
            mutableListOf()
        }
        if (!reasons.contains(DuplicatePenaltyPolicy.DUPLICATE_REASON)) {
            reasons.add(DuplicatePenaltyPolicy.DUPLICATE_REASON)
        }
        return entity.copy(rejectReasonsJson = JSONArray(reasons).toString())
    }
}
