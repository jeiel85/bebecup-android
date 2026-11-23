package com.bebecup.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bebecup.app.data.ai.AiCurationSessionDao
import com.bebecup.app.data.ai.AiCurationSessionEntity
import com.bebecup.app.data.ai.PhotoAnalysisDao
import com.bebecup.app.data.ai.PhotoAnalysisEntity
import com.bebecup.app.data.ai.PhotoClusterDao
import com.bebecup.app.data.ai.PhotoClusterEntity
import com.bebecup.app.data.children.ChildEntity
import com.bebecup.app.data.children.ChildrenDao
import com.bebecup.app.data.children.FaceReferenceEntity
import com.bebecup.app.data.children.PhotoFaceEntity

@Database(
    entities = [
        BabyPhoto::class,
        TournamentRecord::class,
        PhotoAnalysisEntity::class,
        PhotoClusterEntity::class,
        AiCurationSessionEntity::class,
        ChildEntity::class,
        PhotoFaceEntity::class,
        FaceReferenceEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun babyPhotoDao(): BabyPhotoDao
    abstract fun photoAnalysisDao(): PhotoAnalysisDao
    abstract fun photoClusterDao(): PhotoClusterDao
    abstract fun aiCurationSessionDao(): AiCurationSessionDao
    abstract fun childrenDao(): ChildrenDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "baby_photo_worldcup_database"
                )
                    // Commercial release: explicit migrations preserve user data
                    // across updates (v2 tournament app → v7). See
                    // [ALL_MIGRATIONS] / Migrations.kt.
                    .addMigrations(*ALL_MIGRATIONS)
                    // Only wipe on an (unsupported) downgrade, never on upgrade.
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
