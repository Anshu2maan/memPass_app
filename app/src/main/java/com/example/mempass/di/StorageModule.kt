package com.example.mempass.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.mempass.common.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    private fun createEncryptedPrefs(context: Context, fileName: String): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    @Provides
    @Singleton
    @Named("SecurityPrefs")
    fun provideSecurityPreferences(@ApplicationContext context: Context): SharedPreferences {
        return createEncryptedPrefs(context, Constants.PREFS_SECURITY)
    }

    @Provides
    @Singleton
    @Named("HistoryPrefs")
    fun provideHistoryPreferences(@ApplicationContext context: Context): SharedPreferences {
        return createEncryptedPrefs(context, Constants.PREFS_HISTORY)
    }

    @Provides
    @Singleton
    @Named("IntruderPrefs")
    fun provideIntruderPreferences(@ApplicationContext context: Context): SharedPreferences {
        return createEncryptedPrefs(context, "mempass_intruder_prefs_v2")
    }
}
