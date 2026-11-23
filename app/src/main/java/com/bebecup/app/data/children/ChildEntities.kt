package com.bebecup.app.data.children

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bebecup.app.data.BabyPhoto

/** One of the family's children. [birthDate] is "yyyy-MM-dd" or "" when not given. Stays on this device. */
@Entity(tableName = "children")
data class ChildEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val birthDate: String = "",
    val createdAtMillis: Long
)

/** How a face's owner was decided. */
object FaceState {
    /** Nobody has said whose face it is, and it did not match anyone clearly. */
    const val UNKNOWN = "unknown"
    /** Matched to a named face by the app; changes when the references change. */
    const val AUTO = "auto"
    /** The parent said whose face it is; the app never changes it. */
    const val MANUAL = "manual"
}

/**
 * A face found in a kept or scanned photo, with its feature vector (local only).
 * [childId] is the child it belongs to; null with [FaceState.AUTO] or
 * [FaceState.MANUAL] means "not one of the children" (a parent, a friend).
 * The box is 0..1 of the upright photo, for showing the face.
 */
@Entity(
    tableName = "photo_faces",
    foreignKeys = [
        ForeignKey(entity = BabyPhoto::class, parentColumns = ["id"], childColumns = ["photoId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ChildEntity::class, parentColumns = ["id"], childColumns = ["childId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("photoId"), Index("childId")]
)
class PhotoFaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val photoId: Int,
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
    val embedding: ByteArray,
    val childId: Long? = null,
    @ColumnInfo(defaultValue = FaceState.UNKNOWN) val state: String = FaceState.UNKNOWN,
    val modelVersion: Int
)

/**
 * A face the parent named, kept as an example of that person even after its
 * photo leaves the app. [childId] null = not one of the children. The newest
 * ones are kept per person, so the examples follow the children as they grow.
 */
@Entity(
    tableName = "face_references",
    foreignKeys = [
        ForeignKey(entity = ChildEntity::class, parentColumns = ["id"], childColumns = ["childId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("childId"), Index("sourceFaceId")]
)
class FaceReferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long?,
    val embedding: ByteArray,
    /** The [PhotoFaceEntity] it came from, so a correction replaces it. */
    val sourceFaceId: Long?,
    val createdAtMillis: Long,
    val modelVersion: Int
)

/** A face with the photo it is in. */
class FaceInPhoto(
    @Embedded val face: PhotoFaceEntity,
    @ColumnInfo(name = "uriString") val uriString: String
)

/** Which child is in which photo. */
data class PhotoChildRow(val photoId: Int, val childId: Long)
