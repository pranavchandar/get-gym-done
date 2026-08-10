package com.getgymdone.app.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.getgymdone.app.data.db.AppDatabase
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
import com.getgymdone.app.data.seed.SeedLoader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase {
        // Seed must run after the database instance exists, so we capture a lazy reference and
        // dispatch the seed work on the IO scope when Room first opens the file.
        lateinit var dbRef: AppDatabase
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dbRef = Room.databaseBuilder(ctx, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
            )
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    scope.launch {
                        SeedLoader(ctx, dbRef).seedIfEmpty()
                    }
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    // Catch-up seed in case onCreate was missed (e.g. partial install).
                    scope.launch {
                        SeedLoader(ctx, dbRef).seedIfEmpty()
                    }
                }
            })
            .build()
        return dbRef
    }

    @Provides fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()
    @Provides fun provideSplitDao(db: AppDatabase): SplitDao = db.splitDao()
    @Provides fun provideWorkoutDayDao(db: AppDatabase): WorkoutDayDao = db.workoutDayDao()
    @Provides fun provideDayExerciseDao(db: AppDatabase): DayExerciseDao = db.dayExerciseDao()
    @Provides fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()
    @Provides fun provideSetLogDao(db: AppDatabase): SetLogDao = db.setLogDao()
    @Provides fun provideBodyMetricDao(db: AppDatabase): BodyMetricDao = db.bodyMetricDao()
    @Provides fun provideUserPrefsDao(db: AppDatabase): UserPrefsDao = db.userPrefsDao()
    @Provides fun provideExerciseMediaDao(db: AppDatabase): ExerciseMediaDao = db.exerciseMediaDao()
    @Provides fun provideExerciseNoteDao(db: AppDatabase): ExerciseNoteDao = db.exerciseNoteDao()
    @Provides fun provideFriendDao(db: AppDatabase): FriendDao = db.friendDao()
}
