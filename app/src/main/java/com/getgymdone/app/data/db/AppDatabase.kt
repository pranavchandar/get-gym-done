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
import com.getgymdone.app.data.db.dao.SessionDao
import com.getgymdone.app.data.db.dao.SetLogDao
import com.getgymdone.app.data.db.dao.SplitDao
import com.getgymdone.app.data.db.dao.UserPrefsDao
import com.getgymdone.app.data.db.dao.WorkoutDayDao
import com.getgymdone.app.data.db.entities.BodyMetric
import com.getgymdone.app.data.db.entities.DayExercise
import com.getgymdone.app.data.db.entities.Exercise
import com.getgymdone.app.data.db.entities.ExerciseMedia
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
    ],
    version = 5,
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
    }
}
