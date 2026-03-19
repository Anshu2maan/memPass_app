package com.example.mempass

import android.content.Context
import android.util.Base64
import android.util.JsonReader
import android.util.JsonWriter
import android.util.Log
import java.io.*
import java.util.*
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor() {
    private val TAG = "BackupManager"

    fun exportToBackup(
        passwords: List<PasswordEntry>,
        documents: List<DocumentEntry>,
        notes: List<NoteEntry>,
        backupPassword: CharArray,
        outputStream: OutputStream
    ) {
        try {
            val salt = KeyManager.generateSalt()
            val backupKey = KeyManager.deriveKeyArgon2(backupPassword, salt) ?: KeyManager.deriveKeySha256(backupPassword)
            
            val out = ByteArrayOutputStream()
            val writer = JsonWriter(OutputStreamWriter(out, "UTF-8"))
            writer.setIndent("  ")
            writer.beginObject()

            writer.name("passwords").beginArray()
            passwords.forEach { p ->
                writer.beginObject()
                writer.name("remoteId").value(p.remoteId)
                writer.name("serviceName").value(p.serviceName)
                writer.name("u").value(Base64.encodeToString(p.encryptedUsername, Base64.DEFAULT))
                writer.name("p").value(Base64.encodeToString(p.encryptedPassword, Base64.DEFAULT))
                writer.name("n").value(Base64.encodeToString(p.encryptedNotes, Base64.DEFAULT))
                p.encryptedTotpSecret?.let { writer.name("t").value(Base64.encodeToString(it, Base64.DEFAULT)) }
                p.associatedPackageName?.let { writer.name("ap").value(it) }
                p.associatedDomain?.let { writer.name("ad").value(it) }
                writer.name("v").value(p.version.toLong())
                writer.name("c").value(p.createdAt)
                writer.name("f").value(p.isFavorite)
                writer.endObject()
            }
            writer.endArray()

            writer.name("documents").beginArray()
            documents.forEach { d ->
                writer.beginObject()
                writer.name("remoteId").value(d.remoteId)
                writer.name("title").value(d.title)
                writer.name("type").value(d.documentType)
                writer.name("ef").value(Base64.encodeToString(d.encryptedFields, Base64.DEFAULT))
                writer.name("en").value(Base64.encodeToString(d.encryptedNotes, Base64.DEFAULT))
                writer.name("fp").value(d.filePaths)
                d.thumbnailPath?.let { writer.name("tp").value(it) }
                d.expiryDate?.let { writer.name("ed").value(it) }
                writer.name("c").value(d.createdAt)
                writer.endObject()
            }
            writer.endArray()

            writer.name("notes").beginArray()
            notes.forEach { n ->
                writer.beginObject()
                writer.name("remoteId").value(n.remoteId)
                writer.name("title").value(n.title)
                writer.name("ec").value(Base64.encodeToString(n.encryptedContent, Base64.DEFAULT))
                writer.name("cat").value(n.category)
                writer.name("col").value(n.colorHex)
                writer.name("chk").value(n.isChecklist)
                writer.name("tag").value(n.tags)
                writer.name("sfp").value(n.snippetFilePaths)
                n.selfDestructAt?.let { writer.name("sd").value(it) }
                writer.name("lck").value(n.isLocked)
                writer.name("c").value(n.createdAt)
                writer.endObject()
            }
            writer.endArray()

            writer.endObject()
            writer.close()

            val plaintext = out.toByteArray()
            val encrypted = CryptoUtils.encryptRaw(plaintext, backupKey)
            
            outputStream.write(salt)
            outputStream.write(encrypted)
            outputStream.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            throw e
        }
    }

    fun importFromBackup(
        inputStream: InputStream,
        backupPassword: CharArray
    ): Triple<List<PasswordEntry>, List<DocumentEntry>, List<NoteEntry>> {
        try {
            val salt = ByteArray(16)
            if (inputStream.read(salt) != 16) throw Exception("Invalid backup file: salt missing")
            
            val encrypted = inputStream.readBytes()
            val backupKey = KeyManager.deriveKeyArgon2(backupPassword, salt) ?: KeyManager.deriveKeySha256(backupPassword)
            
            val plaintext = CryptoUtils.decryptRaw(encrypted, backupKey)
            val reader = JsonReader(InputStreamReader(ByteArrayInputStream(plaintext), "UTF-8"))
            
            val passwords = mutableListOf<PasswordEntry>()
            val documents = mutableListOf<DocumentEntry>()
            val notes = mutableListOf<NoteEntry>()

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "passwords" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            reader.beginObject()
                            var remoteId = ""
                            var serviceName = ""
                            var u = ByteArray(0)
                            var p = ByteArray(0)
                            var n = ByteArray(0)
                            var t: ByteArray? = null
                            var ap: String? = null
                            var ad: String? = null
                            var v = 1
                            var c = 0L
                            var f = false
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "remoteId" -> remoteId = reader.nextString()
                                    "serviceName" -> serviceName = reader.nextString()
                                    "u" -> u = Base64.decode(reader.nextString(), Base64.DEFAULT)
                                    "p" -> p = Base64.decode(reader.nextString(), Base64.DEFAULT)
                                    "n" -> n = Base64.decode(reader.nextString(), Base64.DEFAULT)
                                    "t" -> t = Base64.decode(reader.nextString(), Base64.DEFAULT)
                                    "ap" -> ap = reader.nextString()
                                    "ad" -> ad = reader.nextString()
                                    "v" -> v = reader.nextInt()
                                    "c" -> c = reader.nextLong()
                                    "f" -> f = reader.nextBoolean()
                                    else -> reader.skipValue()
                                }
                            }
                            passwords.add(PasswordEntry(0, remoteId, serviceName, u, p, n, t, ap, ad, v, c, f))
                            reader.endObject()
                        }
                        reader.endArray()
                    }
                    "documents" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            reader.beginObject()
                            var rId = ""
                            var title = ""
                            var type = ""
                            var ef = ByteArray(0)
                            var en = ByteArray(0)
                            var fp = ""
                            var tp: String? = null
                            var ed: Long? = null
                            var c = 0L
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "remoteId" -> rId = reader.nextString()
                                    "title" -> title = reader.nextString()
                                    "type" -> type = reader.nextString()
                                    "ef" -> ef = Base64.decode(reader.nextString(), Base64.DEFAULT)
                                    "en" -> en = Base64.decode(reader.nextString(), Base64.DEFAULT)
                                    "fp" -> fp = reader.nextString()
                                    "tp" -> tp = reader.nextString()
                                    "ed" -> ed = reader.nextLong()
                                    "c" -> c = reader.nextLong()
                                    else -> reader.skipValue()
                                }
                            }
                            documents.add(DocumentEntry(0, rId, title, type, ef, en, fp, tp, ed, c))
                            reader.endObject()
                        }
                        reader.endArray()
                    }
                    "notes" -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            reader.beginObject()
                            var rId = ""
                            var title = ""
                            var ec = ByteArray(0)
                            var cat = "General"
                            var col = "#FFFFFF"
                            var chk = false
                            var tag = ""
                            var sfp = ""
                            var sd: Long? = null
                            var lck = false
                            var c = 0L
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "remoteId" -> rId = reader.nextString()
                                    "title" -> title = reader.nextString()
                                    "ec" -> ec = Base64.decode(reader.nextString(), Base64.DEFAULT)
                                    "cat" -> cat = reader.nextString()
                                    "col" -> col = reader.nextString()
                                    "chk" -> chk = reader.nextBoolean()
                                    "tag" -> tag = reader.nextString()
                                    "sfp" -> sfp = reader.nextString()
                                    "sd" -> sd = reader.nextLong()
                                    "lck" -> lck = reader.nextBoolean()
                                    "c" -> c = reader.nextLong()
                                    else -> reader.skipValue()
                                }
                            }
                            notes.add(NoteEntry(0, rId, title, ec, cat, col, chk, tag, sfp, sd, lck, c))
                            reader.endObject()
                        }
                        reader.endArray()
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            reader.close()
            return Triple(passwords, documents, notes)
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            throw e
        }
    }
}
