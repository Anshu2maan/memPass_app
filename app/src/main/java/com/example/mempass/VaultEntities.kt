package com.example.mempass

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

interface VaultEntity {
    val id: Int
    val remoteId: String
    fun withId(id: Int): VaultEntity
}

@Entity(
    tableName = "passwords",
    indices = [Index(value = ["remoteId"], unique = true)]
)
data class PasswordEntry(
    @PrimaryKey(autoGenerate = true) override val id: Int = 0,
    override val remoteId: String = UUID.randomUUID().toString(),
    val serviceName: String,
    val encryptedUsername: ByteArray,
    val encryptedPassword: ByteArray,
    val encryptedNotes: ByteArray,
    val encryptedTotpSecret: ByteArray? = null,
    val associatedPackageName: String? = null,
    val associatedDomain: String? = null,
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
) : VaultEntity {
    override fun withId(id: Int) = copy(id = id)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PasswordEntry
        if (id != other.id) return false
        if (remoteId != other.remoteId) return false
        if (serviceName != other.serviceName) return false
        if (!encryptedUsername.contentEquals(other.encryptedUsername)) return false
        if (!encryptedPassword.contentEquals(other.encryptedPassword)) return false
        if (!encryptedNotes.contentEquals(other.encryptedNotes)) return false
        if (associatedPackageName != other.associatedPackageName) return false
        if (associatedDomain != other.associatedDomain) return false
        if (encryptedTotpSecret != null) {
            if (other.encryptedTotpSecret == null) return false
            if (!encryptedTotpSecret.contentEquals(other.encryptedTotpSecret)) return false
        } else if (other.encryptedTotpSecret != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + remoteId.hashCode()
        result = 31 * result + serviceName.hashCode()
        result = 31 * result + encryptedUsername.contentHashCode()
        result = 31 * result + encryptedPassword.contentHashCode()
        result = 31 * result + encryptedNotes.contentHashCode()
        result = 31 * result + (associatedPackageName?.hashCode() ?: 0)
        result = 31 * result + (associatedDomain?.hashCode() ?: 0)
        result = 31 * result + (encryptedTotpSecret?.contentHashCode() ?: 0)
        return result
    }
}

@Entity(
    tableName = "documents",
    indices = [Index(value = ["remoteId"], unique = true)]
)
data class DocumentEntry(
    @PrimaryKey(autoGenerate = true) override val id: Int = 0,
    override val remoteId: String = UUID.randomUUID().toString(),
    val title: String,
    val documentType: String,
    val encryptedFields: ByteArray,
    val encryptedNotes: ByteArray,
    val filePaths: String,
    val thumbnailPath: String? = null,
    val expiryDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
) : VaultEntity {
    override fun withId(id: Int) = copy(id = id)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DocumentEntry
        if (id != other.id) return false
        if (!encryptedFields.contentEquals(other.encryptedFields)) return false
        if (!encryptedNotes.contentEquals(other.encryptedNotes)) return false
        if (isFavorite != other.isFavorite) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + encryptedFields.contentHashCode()
        result = 31 * result + encryptedNotes.contentHashCode()
        result = 31 * result + isFavorite.hashCode()
        return result
    }
}

@Entity(
    tableName = "notes",
    indices = [Index(value = ["remoteId"], unique = true)]
)
data class NoteEntry(
    @PrimaryKey(autoGenerate = true) override val id: Int = 0,
    override val remoteId: String = UUID.randomUUID().toString(),
    val title: String,
    val encryptedContent: ByteArray,
    val category: String = "General",
    val colorHex: String = "#FFFFFF",
    val isChecklist: Boolean = false,
    val tags: String = "",
    val snippetFilePaths: String = "",
    val selfDestructAt: Long? = null,
    val isLocked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val fontFamily: String = "Default",
    val fontSize: Float = 16.0f,
    val letterSpacing: Float = 0.0f
) : VaultEntity {
    override fun withId(id: Int) = copy(id = id)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as NoteEntry
        if (id != other.id) return false
        if (!encryptedContent.contentEquals(other.encryptedContent)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + encryptedContent.contentHashCode()
        return result
    }
}
