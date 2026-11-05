package com.tutordiary.app.di

import android.content.Context
import androidx.room.Room
import com.tutordiary.app.data.db.AppDatabase
import com.tutordiary.app.domain.AppClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    @Singleton
    fun provideClock(): AppClock = AppClock { LocalDate.now() }
}
