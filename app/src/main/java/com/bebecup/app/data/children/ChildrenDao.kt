package com.bebecup.app.data.children

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.bebecup.app.data.BabyPhoto
import kotlinx.coroutines.flow.Flow

@Dao
interface ChildrenDao {

    // --- Children ---

    @Query("SELECT * FROM children ORDER BY createdAtMillis, id")
    fun observeChildren(): Flow<List<ChildEntity>>

    @Query("SELECT * FROM children ORDER BY createdAtMillis, id")
    suspend fun getChildren(): List<ChildEntity>

    @Insert
    suspend fun insertChild(child: ChildEntity): Long

    @Query("UPDATE children SET name = :name, birthDate = :birthDate WHERE id = :id")
    suspend fun updateChild(id: Long, name: String, birthDate: String)

    @Query("UPDATE photo_faces SET childId = NULL, state = '${FaceState.UNKNOWN}' WHERE childId = :childId")
    suspend fun forgetFacesOf(childId: Long)

    @Query("DELETE FROM children WHERE id = :id")
    suspend fun deleteChildRow(id: Long)

    /** The child's faces become unknown again (not "not a child"); their named examples go. */
    @Transaction
    suspend fun deleteChild(id: Long) {
        forgetFacesOf(id)
        deleteChildRow(id)
    }

    // --- Faces in photos ---

    @Insert
    suspend fun insertFaces(faces: List<PhotoFaceEntity>): List<Long>

    @Query("DELETE FROM photo_faces WHERE photoId = :photoId")
    suspend fun deleteFacesOf(photoId: Int)

    @Query("UPDATE baby_photos SET faceIdVersion = :version WHERE id = :photoId")
    suspend fun markFaceIdVersion(photoId: Int, version: Int)

    /** Store what face-ID found in a photo (possibly nothing) and mark it as done. */
    @Transaction
    suspend fun replaceFaces(photoId: Int, faces: List<PhotoFaceEntity>, version: Int) {
        deleteFacesOf(photoId)
        if (faces.isNotEmpty()) insertFaces(faces)
        markFaceIdVersion(photoId, version)
    }

    /** Faces the app may (re)assign: everything the parent has not decided. */
    @Query("SELECT * FROM photo_faces WHERE modelVersion = :version AND state != '${FaceState.MANUAL}'")
    suspend fun getUndecidedFaces(version: Int): List<PhotoFaceEntity>

    @Query("SELECT * FROM photo_faces WHERE modelVersion = :version AND state = '${FaceState.MANUAL}'")
    suspend fun getManualFaces(version: Int): List<PhotoFaceEntity>

    @Query(
        "SELECT photo_faces.*, baby_photos.uriString FROM photo_faces " +
            "JOIN baby_photos ON baby_photos.id = photo_faces.photoId " +
            "WHERE photo_faces.photoId = :photoId ORDER BY (photo_faces.boxRight - photo_faces.boxLeft) DESC"
    )
    suspend fun getFacesInPhoto(photoId: Int): List<FaceInPhoto>

    @Query(
        "SELECT photo_faces.*, baby_photos.uriString FROM photo_faces " +
            "JOIN baby_photos ON baby_photos.id = photo_faces.photoId " +
            "WHERE photo_faces.state = '${FaceState.UNKNOWN}' AND photo_faces.modelVersion = :version " +
            "ORDER BY photo_faces.id DESC LIMIT :limit"
    )
    suspend fun getUnknownFaces(version: Int, limit: Int): List<FaceInPhoto>

    @Query(
        "SELECT photo_faces.*, baby_photos.uriString FROM photo_faces " +
            "JOIN baby_photos ON baby_photos.id = photo_faces.photoId " +
            "WHERE photo_faces.childId = :childId ORDER BY photo_faces.state = '${FaceState.MANUAL}' DESC, photo_faces.id DESC LIMIT 1"
    )
    suspend fun getLatestFaceOf(childId: Long): FaceInPhoto?

    @Query("SELECT * FROM photo_faces WHERE id IN (:ids)")
    suspend fun getFaces(ids: List<Long>): List<PhotoFaceEntity>

    @Query("UPDATE photo_faces SET childId = :childId, state = :state WHERE id IN (:ids)")
    suspend fun setFaces(ids: List<Long>, childId: Long?, state: String)

    /** The app's own match: skipped for faces the parent decided meanwhile. */
    @Query("UPDATE photo_faces SET childId = :childId, state = :state WHERE id IN (:ids) AND state != '${FaceState.MANUAL}'")
    suspend fun setUndecidedFaces(ids: List<Long>, childId: Long?, state: String)

    @Query("SELECT DISTINCT photoId, childId FROM photo_faces WHERE childId IS NOT NULL")
    fun observePhotoChildren(): Flow<List<PhotoChildRow>>

    @Query("SELECT DISTINCT photoId, childId FROM photo_faces WHERE childId IS NOT NULL")
    suspend fun getPhotoChildren(): List<PhotoChildRow>

    /** The newest unknown face id (0 = none), for the home card that asks for names. */
    @Query("SELECT COALESCE(MAX(id), 0) FROM photo_faces WHERE state = '${FaceState.UNKNOWN}' AND modelVersion = :version")
    fun observeNewestUnknownFaceId(version: Int): Flow<Long>

    // --- Named examples ---

    @Insert
    suspend fun insertReferences(references: List<FaceReferenceEntity>)

    @Query("SELECT * FROM face_references WHERE modelVersion = :version")
    suspend fun getReferences(version: Int): List<FaceReferenceEntity>

    @Query("DELETE FROM face_references WHERE sourceFaceId IN (:faceIds)")
    suspend fun deleteReferencesFrom(faceIds: List<Long>)

    @Query(
        "DELETE FROM face_references WHERE childId = :childId AND id NOT IN " +
            "(SELECT id FROM face_references WHERE childId = :childId ORDER BY createdAtMillis DESC, id DESC LIMIT :keep)"
    )
    suspend fun trimReferencesOf(childId: Long, keep: Int)

    @Query(
        "DELETE FROM face_references WHERE childId IS NULL AND id NOT IN " +
            "(SELECT id FROM face_references WHERE childId IS NULL ORDER BY createdAtMillis DESC, id DESC LIMIT :keep)"
    )
    suspend fun trimNotChildReferences(keep: Int)

    @Query("DELETE FROM face_references WHERE modelVersion != :version")
    suspend fun deleteOtherVersionReferences(version: Int)

    // --- Photos still to look at ---

    /** Kept photos (보관함·인화함) face-ID has not looked at with [version] yet. */
    @Query("SELECT * FROM baby_photos WHERE (isSelectedAsBest = 1 OR isInPrintCart = 1) AND faceIdVersion < :version")
    suspend fun getKeptPhotosWithoutFaces(version: Int): List<BabyPhoto>

    // --- Privacy ---

    @Query("DELETE FROM photo_faces")
    suspend fun deleteAllFaces()

    @Query("DELETE FROM face_references")
    suspend fun deleteAllReferences()

    @Query("UPDATE baby_photos SET faceIdVersion = 0")
    suspend fun resetFaceIdVersions()

    /** Every face vector and named example; children's names and birthdays stay. */
    @Transaction
    suspend fun deleteAllFaceData() {
        deleteAllFaces()
        deleteAllReferences()
        resetFaceIdVersions()
    }
}
