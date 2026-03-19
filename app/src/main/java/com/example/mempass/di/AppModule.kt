package com.example.mempass.di

import android.content.Context
import android.content.SharedPreferences
import androidx.work.WorkManager
import com.example.mempass.BiometricHelper
import com.example.mempass.CameraHelper
import com.example.mempass.DriveHelper
import com.example.mempass.FileUtils
import com.example.mempass.OcrHelper
import com.example.mempass.SharingUtils
import com.example.mempass.VaultManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideVaultManager(): VaultManager {
        return VaultManager()
    }

    @Provides
    @Singleton
    fun provideBiometricHelper(
        @ApplicationContext context: Context,
        @Named("SecurityPrefs") encryptedPrefs: SharedPreferences
    ): BiometricHelper {
        return BiometricHelper(context, encryptedPrefs)
    }

    @Provides
    @Singleton
    fun provideOcrHelper(@ApplicationContext context: Context): OcrHelper {
        return OcrHelper(context)
    }

    @Provides
    @Singleton
    fun provideCameraHelper(
        @ApplicationContext context: Context,
        @Named("IntruderPrefs") intruderPrefs: SharedPreferences
    ): CameraHelper {
        return CameraHelper(context, intruderPrefs)
    }

    @Provides
    @Singleton
    fun provideDriveHelper(@ApplicationContext context: Context): DriveHelper {
        return DriveHelper(context)
    }

    @Provides
    @Singleton
    fun provideFileUtils(@ApplicationContext context: Context): FileUtils {
        return FileUtils(context)
    }

    @Provides
    @Singleton
    fun provideSharingUtils(@ApplicationContext context: Context): SharingUtils {
        return SharingUtils(context)
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }
}
