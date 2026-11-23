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
 *  - v5: + tournament_records.monthKey (이달의 월드컵).
 *  - v6: + children, photo_faces, face_references, baby_photos.faceIdVersion (아이 얼굴 구분).
 *  - v7: + tournament_records.childId (아이별 월드컵).
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

/** v4 → v5: tournament_records.monthKey marks a 이달의 월드컵; existing cups stay null. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tournament_records` ADD COLUMN `monthKey` TEXT")
    }
}

/**
 * v5 → v6: the family's children and the faces that tell them apart. Existing
 * photos start with faceIdVersion 0 (not looked at yet). The one-child profile
 * kept in prefs before v6 becomes the first child on the next launch
 * (BabyCupViewModel), since prefs are not reachable from here.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `baby_photos` ADD COLUMN `faceIdVersion` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `children` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`birthDate` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `photo_faces` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `photoId` INTEGER NOT NULL, " +
                "`boxLeft` REAL NOT NULL, `boxTop` REAL NOT NULL, `boxRight` REAL NOT NULL, `boxBottom` REAL NOT NULL, " +
                "`embedding` BLOB NOT NULL, `childId` INTEGER, `state` TEXT NOT NULL DEFAULT 'unknown', " +
                "`modelVersion` INTEGER NOT NULL, " +
                "FOREIGN KEY(`photoId`) REFERENCES `baby_photos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`childId`) REFERENCES `children`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_faces_photoId` ON `photo_faces` (`photoId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_faces_childId` ON `photo_faces` (`childId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `face_references` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `childId` INTEGER, `embedding` BLOB NOT NULL, " +
                "`sourceFaceId` INTEGER, `createdAtMillis` INTEGER NOT NULL, `modelVersion` INTEGER NOT NULL, " +
                "FOREIGN KEY(`childId`) REFERENCES `children`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_face_references_childId` ON `face_references` (`childId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_face_references_sourceFaceId` ON `face_references` (`sourceFaceId`)")
    }
}

/**
 * v6 → v7: tournament_records.childId, whose cup it was. v0.12.0 kept that
 * only in the title ("하린 이상형 월드컵", "2026년 10월의 하린 월드컵"), so
 * those cups get their child back when exactly one child's name fits the
 * title; every other cup stays a family cup (null).
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tournament_records` ADD COLUMN `childId` INTEGER")
        // No table alias: UPDATE ... AS needs a newer SQLite than old Android ships.
        val title = "tournament_records.tournamentTitle"
        val fits =
            "(tournament_records.monthKey IS NULL AND $title = c.name || ' 이상형 월드컵') OR " +
                "(tournament_records.monthKey IS NOT NULL AND " +
                "substr($title, -length('의 ' || c.name || ' 월드컵')) = '의 ' || c.name || ' 월드컵')"
        db.execSQL(
            "UPDATE tournament_records SET childId = " +
                "(SELECT c.id FROM children c WHERE $fits) " +
                "WHERE (SELECT COUNT(*) FROM children c WHERE $fits) = 1"
        )
    }
}

/** All migrations, in order, for registration on the database builder. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
