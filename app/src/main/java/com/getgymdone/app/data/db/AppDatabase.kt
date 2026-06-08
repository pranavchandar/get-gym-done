package com.getgymdone.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.getgymdone.app.data.db.dao.BodyMetricDao
import com.getgymdone.app.data.db.dao.DayExerciseDao
import com.getgymdone.app.data.db.dao.ExerciseDao
import com.getgymdone.app.data.db.dao.SessionDao
import com.getgymdone.app.data.db.dao.SetLogDao
import com.getgymdone.app.data.db.dao.SplitDao
import com.getgymdone.app.data.db.dao.UserPrefsDao
import com.getgymdone.app.data.db.dao.WorkoutDayDao
import com.getgymdone.app.data.db.entities.BodyMetric
import com.getgymdone.app.data.db.entities.DayExercise
import com.getgymdone.app.data.db.entities.Exercise
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
    ],
    version = 3,
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
    }
}
