package com.tutordiary.app.di

import android.content.Context
import com.tutordiary.app.data.cloud.AuthService
import com.tutordiary.app.data.cloud.CloudStore
import com.tutordiary.app.data.cloud.FirebaseAuthService
import com.tutordiary.app.data.cloud.FirestoreCloud
import com.tutordiary.app.data.cloud.OfflineAuthService
import com.tutordiary.app.data.cloud.OfflineCloud
import com.tutordiary.app.data.cloud.isFirebaseConfigured
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Uses Firebase when this build has a project file; otherwise everything online is switched off. */
@Module
@InstallIn(SingletonComponent::class)
object CloudModule {

    @Provides
    @Singleton
    fun provideAuth(@ApplicationContext context: Context): AuthService =
        if (isFirebaseConfigured(context)) FirebaseAuthService(context) else OfflineAuthService()

    @Provides
    @Singleton
    fun provideCloud(@ApplicationContext context: Context): CloudStore =
        if (isFirebaseConfigured(context)) FirestoreCloud() else OfflineCloud()
}
