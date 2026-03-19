package com.example.mempass.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.example.mempass.SafeKeyStore
import com.example.mempass.VaultDao
import com.example.mempass.VaultDatabase
import com.example.mempass.VaultRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        safeKeyStore: SafeKeyStore
    ): VaultDatabase {
        val passphrase = safeKeyStore.getDatabasePassphrase()
        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(
            context,
            VaultDatabase::class.java,
            "vault_database"
        )
        .openHelperFactory(factory)
        .build()
    }

    @Provides
    fun provideVaultDao(database: VaultDatabase): VaultDao {
        return database.vaultDao()
    }

    @Provides
    @Singleton
    fun provideVaultRepository(
        dao: VaultDao, 
        @Named("SecurityPrefs") prefs: SharedPreferences,
        @ApplicationContext context: Context
    ): VaultRepository {
        return VaultRepository(dao, prefs, context)
    }
}
