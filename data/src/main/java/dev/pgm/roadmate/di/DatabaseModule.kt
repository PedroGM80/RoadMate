package dev.pgm.roadmate.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.pgm.roadmate.data.db.MemoryDao
import dev.pgm.roadmate.data.db.RoadMateDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideRoadMateDatabase(@ApplicationContext context: Context): RoadMateDatabase =
        Room.databaseBuilder(context, RoadMateDatabase::class.java, "roadmate.db").build()

    @Provides
    fun provideMemoryDao(database: RoadMateDatabase): MemoryDao = database.memoryDao()
}
