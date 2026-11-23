package com.bebecup.app.data.children

import android.net.Uri
import android.util.Log
import com.bebecup.app.ai.faceid.FaceIdModelStore
import com.bebecup.app.ai.faceid.FaceIdUnavailableException
import com.bebecup.app.ai.faceid.FaceVectors
import com.bebecup.app.ai.faceid.PhotoFaceReader
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.domain.children.FaceMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 아이 얼굴 구분: finds the faces in photos, matches them to the children the
 * parent has named, and groups the rest so the parent can name them. All local.
 *
 * Only faces the parent named become examples ([FaceReferenceEntity]); faces the
 * app matched itself never do, so one wrong guess cannot teach it more wrong
 * guesses. Matches are recomputed whenever the examples change, except for faces
 * the parent decided ([FaceState.MANUAL]).
 */
class FamilyFaces(
    private val dao: ChildrenDao,
    private val reader: () -> PhotoFaceReader?,
    private val now: () -> Long = System::currentTimeMillis
) {
    /** Faces that look like the same person and nobody has named yet, newest first. */
    class Group(val faces: List<FaceInPhoto>) {
        val faceIds: List<Long> get() = faces.map { it.face.id }
        val photoCount: Int get() = faces.map { it.face.photoId }.distinct().size
    }

    /**
     * Look at [photos] face-ID has not seen with the current model, store their
     * faces and match everything again. Returns how many photos were looked at.
     * Throws [FaceIdUnavailableException] when the model or runtime is missing.
     */
    suspend fun identify(photos: List<BabyPhoto>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Int {
        val pending = photos.filter { it.faceIdVersion < VERSION }.distinctBy { it.id }
        if (pending.isEmpty()) return 0
        val faceReader = reader() ?: throw FaceIdUnavailableException("face-ID model is not downloaded")
        var done = 0
        onProgress(0, pending.size)
        for (photo in pending) {
            val samples = try {
                faceReader.read(Uri.parse(photo.uriString))
            } catch (e: CancellationException) {
                throw e
            } catch (e: FaceIdUnavailableException) {
                throw e
            } catch (e: Exception) {
                // One locked or broken photo must not stop the rest; it is tried again next time.
                Log.w(TAG, "could not read faces in photo ${photo.id}", e)
                null
            }
            if (samples != null) {
                dao.replaceFaces(
                    photoId = photo.id,
                    faces = samples.map {
                        PhotoFaceEntity(
                            photoId = photo.id,
                            boxLeft = it.left,
                            boxTop = it.top,
                            boxRight = it.right,
                            boxBottom = it.bottom,
                            embedding = FaceVectors.toBytes(it.embedding),
                            modelVersion = VERSION
                        )
                    },
                    version = VERSION
                )
            }
            done++
            onProgress(done, pending.size)
        }
        rematch()
        return done
    }

    /** Match every face the parent has not decided against the current examples. */
    suspend fun rematch() {
        val references = references()
        val faces = dao.getUndecidedFaces(VERSION)
        if (faces.isEmpty()) return
        val manualByPhoto = dao.getManualFacesIn(faces.map { it.photoId }.distinct()).groupBy { it.photoId }
        val updates = withContext(Dispatchers.Default) {
            faces.groupBy { it.photoId }.flatMap { (photoId, photoFaces) ->
                // A child the parent already put in this photo is not matched again in it.
                val taken = manualByPhoto[photoId].orEmpty().mapNotNull { it.childId }.toSet()
                val refs = references.filterKeys { it !in taken }
                val usable = photoFaces.mapNotNull { face -> FaceVectors.fromBytes(face.embedding)?.let { face to it } }
                val matches = FaceMatcher.assignPhoto(usable.map { it.second }, refs)
                usable.mapIndexedNotNull { i, (face, _) ->
                    val match = matches[i]
                    val (childId, state) = when {
                        match == null -> null to FaceState.UNKNOWN
                        match.person == FaceMatcher.NOT_CHILD -> null to FaceState.AUTO
                        else -> match.person to FaceState.AUTO
                    }
                    if (childId == face.childId && state == face.state) null else Triple(face.id, childId, state)
                }
            }
        }
        updates.groupBy({ it.second to it.third }, { it.first }).forEach { (owner, ids) ->
            dao.setFaces(ids, owner.first, owner.second)
        }
    }

    /** Unknown faces grouped by likeness; groups of fewer than [minFaces] faces are left out. */
    suspend fun unknownGroups(minFaces: Int = 2): List<Group> {
        val unknown = dao.getUnknownFaces(VERSION, FaceMatcher.MAX_CLUSTER_FACES)
        val usable = unknown.mapNotNull { f -> FaceVectors.fromBytes(f.face.embedding)?.let { f to it } }
        val clusters = withContext(Dispatchers.Default) { FaceMatcher.cluster(usable.map { it.second }) }
        return clusters.filter { it.size >= minFaces }.map { group -> Group(group.map { usable[it].first }) }
    }

    /**
     * The parent says [faceIds] are [childId] (null = none of the children). They
     * become examples of that person, and everything else is matched again.
     */
    suspend fun name(faceIds: List<Long>, childId: Long?) {
        if (faceIds.isEmpty()) return
        dao.setFaces(faceIds, childId, FaceState.MANUAL)
        dao.deleteReferencesFrom(faceIds)
        val stamp = now()
        dao.insertReferences(
            dao.getFaces(faceIds).map {
                FaceReferenceEntity(
                    childId = childId,
                    embedding = it.embedding,
                    sourceFaceId = it.id,
                    createdAtMillis = stamp,
                    modelVersion = it.modelVersion
                )
            }
        )
        if (childId != null) dao.trimReferencesOf(childId, REFERENCES_PER_PERSON)
        else dao.trimNotChildReferences(REFERENCES_PER_PERSON)
        rematch()
    }

    suspend fun facesIn(photoId: Int): List<FaceInPhoto> = dao.getFacesInPhoto(photoId)

    /** A face to show for [childId]: one the parent named if there is one. */
    suspend fun faceOf(childId: Long): FaceInPhoto? = dao.getLatestFaceOf(childId)

    suspend fun keptPhotosToLookAt(): List<BabyPhoto> = dao.getKeptPhotosWithoutFaces(VERSION)

    suspend fun deleteAll() = dao.deleteAllFaceData()

    private suspend fun references(): Map<Long, List<FloatArray>> {
        dao.deleteOtherVersionReferences(VERSION)
        return dao.getReferences(VERSION)
            .mapNotNull { ref -> FaceVectors.fromBytes(ref.embedding)?.let { (ref.childId ?: FaceMatcher.NOT_CHILD) to it } }
            .groupBy({ it.first }, { it.second })
    }

    companion object {
        private const val TAG = "FamilyFaces"
        const val VERSION = FaceIdModelStore.MODEL_VERSION

        /** Newest examples kept per child (and for "not a child"), so they follow the children as they grow. */
        const val REFERENCES_PER_PERSON = 40
    }
}
