package com.bebecup.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit Room migrations (commercial release quality — no data loss on
 * update). SQL mirrors exactly what Room generates for each version, taken
 * from the exported schema JSON under `app/schemas`. See spec §7.5.
 *
 * History:
 *  - v2: baby_photos + tournament_records (original tournament app).
 *  - v3: + AI curation tables (photo_analysis, photo_clusters, ai_curation_sessions).
 *  - v4: + photo_analysis.dHash (near-duplicate clustering).
 */

/** v2 → v3: add the AI curation tables. Existing photos/tournaments are preserved. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `photo_analysis` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`photoId` INTEGER NOT NULL, `analysisVersion` INTEGER NOT NULL, " +
                "`blurScore` REAL NOT NULL, `faceDetected` INTEGER NOT NULL, `faceCount` INTEGER NOT NULL, " +
                "`faceCenterScore` REAL NOT NULL, `eyeOpenScore` REAL NOT NULL, `expressionScore` REAL NOT NULL, " +
                "`exposureScore` REAL NOT NULL, `compositionScore` REAL NOT NULL, `duplicatePenalty` REAL NOT NULL, " +
                "`overallScore` REAL NOT NULL, `qualityGrade` TEXT NOT NULL, " +
                "`rejectReasonsJson` TEXT NOT NULL, `positiveReasonsJson` TEXT NOT NULL, `aiReasonKo` TEXT, " +
                "`analyzedAtMillis` INTEGER NOT NULL, " +
                "FOREIGN KEY(`photoId`) REFERENCES `baby_photos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_analysis_photoId` ON `photo_analysis` (`photoId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_analysis_overallScore` ON `photo_analysis` (`overallScore`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_analysis_analysisVersion` ON `photo_analysis` (`analysisVersion`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `photo_clusters` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `clusterKey` TEXT NOT NULL, " +
                "`representativePhotoId` INTEGER NOT NULL, `photoIdsJson` TEXT NOT NULL, " +
                "`createdAtMillis` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_clusters_clusterKey` ON `photo_clusters` (`clusterKey`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ai_curation_sessions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`scanRangeStartMillis` INTEGER NOT NULL, `scanRangeEndMillis` INTEGER NOT NULL, " +
                "`totalFoundCount` INTEGER NOT NULL, `analyzedCount` INTEGER NOT NULL, " +
                "`rejectedCount` INTEGER NOT NULL, `shortlistedCount` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, `completedAtMillis` INTEGER)"
        )
    }
}

/** v3 → v4: add photo_analysis.dHash for near-duplicate clustering. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `photo_analysis` ADD COLUMN `dHash` INTEGER NOT NULL DEFAULT 0")
    }
}

/** All migrations, in order, for registration on the database builder. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_2_3, MIGRATION_3_4)
