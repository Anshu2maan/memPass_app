package com.example.mempass

import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * A simple in-memory cache to store sensitive autofill data temporarily.
 * This prevents passing plaintext credentials through Android Intents.
 */
object AutofillCache {
    private val cache = ConcurrentHashMap<String, AutofillData>()

    /**
     * Stores the autofill data and returns a unique session ID.
     */
    fun put(data: AutofillData): String {
        val sessionId = UUID.randomUUID().toString()
        cache[sessionId] = data
        return sessionId
    }

    /**
     * Retrieves and immediately removes the data associated with the session ID.
     * This ensures the data is only used once and doesn't linger in memory.
     */
    fun getAndRemove(sessionId: String): AutofillData? {
        return cache.remove(sessionId)
    }
}

/**
 * Data class to hold sensitive credentials during the autofill save flow.
 */
data class AutofillData(
    val username: CharArray,
    val password: CharArray,
    val serviceName: String,
    val packageName: String,
    val webDomain: String?
)
