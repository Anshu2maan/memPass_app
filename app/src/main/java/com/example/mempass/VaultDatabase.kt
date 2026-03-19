package com.example.mempass

import androidx.room.*

@Database(
    entities = [PasswordEntry::class, DocumentEntry::class, NoteEntry::class], 
    version = 1,
    exportSchema = true
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
}
