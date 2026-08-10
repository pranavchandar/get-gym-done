package com.getgymdone.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.getgymdone.app.data.db.dao.BodyMetricDao
import com.getgymdone.app.data.db.dao.DayExerciseDao
import com.getgymdone.app.data.db.dao.ExerciseDao
import com.getgymdone.app.data.db.dao.ExerciseMediaDao
import com.getgymdone.app.data.db.dao.ExerciseNoteDao
import com.getgymdone.app.data.db.dao.FriendDao
import com.getgymdone.app.data.db.dao.SessionDao
import com.getgymdone.app.data.db.dao.SetLogDao
import com.getgymdone.app.data.db.dao.SplitDao
import com.getgymdone.app.data.db.dao.UserPrefsDao
import com.getgymdone.app.data.db.dao.WorkoutDayDao
import com.getgymdone.app.data.db.entities.BodyMetric
import com.getgymdone.app.data.db.entities.DayExercise
import com.getgymdone.app.data.db.entities.Exercise
import com.getgymdone.app.data.db.entities.ExerciseMedia
import com.getgymdone.app.data.db.entities.ExerciseNote
import com.getgymdone.app.data.db.entities.Friend
import com.getgymdone.app.data.db.entities.Session
import com.getgymdone.app.data.db.entities.SetLog
import com.getgymdone.app.data.db.entities.Split
import com.getgymdone.app.data.db.entities.UserPrefs
import com.getgymdone.app.data.db.entities.WorkoutDay

@Database(
    entities = [
        Exercise::class,
        Split::class,
        WorkoutDay::class,
        DayExercise::class,
        Session::class,
        SetLog::class,
        BodyMetric::class,
        UserPrefs::class,
        ExerciseMedia::class,
        ExerciseNote::class,
        Friend::class,
    ],
    version = 10,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun splitDao(): SplitDao
    abstract fun workoutDayDao(): WorkoutDayDao
    abstract fun dayExerciseDao(): DayExerciseDao
    abstract fun sessionDao(): SessionDao
    abstract fun setLogDao(): SetLogDao
    abstract fun bodyMetricDao(): BodyMetricDao
    abstract fun userPrefsDao(): UserPrefsDao
    abstract fun exerciseMediaDao(): ExerciseMediaDao
    abstract fun exerciseNoteDao(): ExerciseNoteDao
    abstract fun friendDao(): FriendDao

    companion object {
        const val NAME = "gymdone.db"

        /** Adds WorkoutDay.isRestDay. Rest-day rows themselves are backfilled by SeedLoader. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_day ADD COLUMN isRestDay INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Adds UserPrefs.restSeconds (rest-timer duration, default 90s). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_prefs ADD COLUMN restSeconds INTEGER NOT NULL DEFAULT 90")
            }
        }

        /** Adds UserPrefs.accent (accent palette key, default Electric lime). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_prefs ADD COLUMN accent TEXT NOT NULL DEFAULT 'lime'")
            }
        }

        /** Adds the exercise_media table for user-uploaded images/GIFs per exercise. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exercise_media` (" +
                        "`id` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, `filePath` TEXT NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`exerciseId`) REFERENCES `exercise`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_exercise_media_exerciseId` " +
                        "ON `exercise_media` (`exerciseId`)",
                )
            }
        }

        /** Adds the Friends social layer: UserPrefs identity columns + the on-device friend table. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_prefs ADD COLUMN socialEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE user_prefs ADD COLUMN socialUserId TEXT")
                db.execSQL("ALTER TABLE user_prefs ADD COLUMN socialHandle TEXT")
                db.execSQL("ALTER TABLE user_prefs ADD COLUMN socialColor TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `friend` (" +
                        "`userId` TEXT NOT NULL, `handle` TEXT NOT NULL, `color` TEXT NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, `currentStreak` INTEGER NOT NULL, " +
                        "`longestStreak` INTEGER NOT NULL, `weekSessions` INTEGER NOT NULL, " +
                        "`weekTarget` INTEGER NOT NULL, `totalSessions` INTEGER NOT NULL, " +
                        "`lastActiveAt` INTEGER, `statsUpdatedAt` INTEGER, PRIMARY KEY(`userId`))",
                )
            }
        }

        /** Adds Friend.streakAtRisk — a friend's published "streak in danger today" flag, for nudges. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE friend ADD COLUMN streakAtRisk INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Adds the display-picture columns: UserPrefs.avatarPhoto + Friend.photo (base64 thumbnails). */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_prefs ADD COLUMN avatarPhoto TEXT")
                db.execSQL("ALTER TABLE friend ADD COLUMN photo TEXT")
            }
        }

        /**
         * Makes Session.workoutDayId nullable and adds activityType/durationMin, so non-gym
         * activities (running, sports) can be logged as completed sessions with no workout day.
         * Requires recreating the table since SQLite can't drop a NOT NULL constraint in place.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `session_new` (`id` TEXT NOT NULL, " +
                        "`workoutDayId` TEXT, `startedAt` INTEGER NOT NULL, `completedAt` INTEGER, " +
                        "`notes` TEXT, `activityType` TEXT, `durationMin` INTEGER, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`workoutDayId`) REFERENCES `workout_day`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE NO ACTION )",
                )
                db.execSQL(
                    "INSERT INTO `session_new` (`id`,`workoutDayId`,`startedAt`,`completedAt`,`notes`) " +
                        "SELECT `id`,`workoutDayId`,`startedAt`,`completedAt`,`notes` FROM `session`",
                )
                db.execSQL("DROP TABLE `session`")
                db.execSQL("ALTER TABLE `session_new` RENAME TO `session`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_session_workoutDayId` " +
                        "ON `session` (`workoutDayId`)",
                )
            }
        }

        /**
         * Adds the exercise_note table: one free-text note per exercise. Kept out of the `exercise`
         * table on purpose — SeedLoader updates exercise rows in place, which would wipe user notes.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exercise_note` (" +
                        "`exerciseId` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`exerciseId`), " +
                        "FOREIGN KEY(`exerciseId`) REFERENCES `exercise`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_exercise_note_exerciseId` " +
                        "ON `exercise_note` (`exerciseId`)",
                )
            }
        }
    }
}
