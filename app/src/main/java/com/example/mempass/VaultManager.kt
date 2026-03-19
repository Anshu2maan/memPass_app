package com.example.mempass

import android.os.SystemClock
import com.example.mempass.common.Constants
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Manages the in-memory state of the Master Key.
 * Handles automatic locking based on app lifecycle and grace periods.
 * 
 * SECURITY NOTE: The grace period (Constants.GRACE_PERIOD_MS) must be 
 * kept to a minimum (recommended < 60s) to reduce the window of vulnerability
 * while the key remains in memory after backgrounding.
 */
@Singleton
class VaultManager @Inject constructor() {
    
    private val lock = ReentrantReadWriteLock()
    
    private var vaultKey: SecretKeySpec? = null
    private var lastBackgroundedAt: Long = 0
    private var isInBackground: Boolean = false

    /**
     * Sets the master key and resets background tracking state.
     */
    fun setKey(key: SecretKeySpec) = lock.write {
        vaultKey = key
        isInBackground = false
        lastBackgroundedAt = 0
    }

    /**
     * Safely retrieves the key if the vault is currently unlocked and
     * within any active grace periods.
     */
    fun getKey(): SecretKeySpec? {
        // First check with a read lock to see if it's already locked or needs locking
        lock.read {
            if (vaultKey == null) return null
            
            if (isInBackground) {
                val elapsed = SystemClock.elapsedRealtime() - lastBackgroundedAt
                if (elapsed <= Constants.GRACE_PERIOD_MS) {
                    return vaultKey
                }
            } else {
                return vaultKey
            }
        }

        // If we're here, we might need to clear the key (transition from grace period to locked)
        lock.write {
            if (isInBackground && vaultKey != null) {
                val elapsed = SystemClock.elapsedRealtime() - lastBackgroundedAt
                if (elapsed > Constants.GRACE_PERIOD_MS) {
                    clearKeyInternal()
                }
            }
            return vaultKey
        }
    }

    /**
     * Checks if the vault is unlocked without returning the key.
     */
    fun isUnlocked(): Boolean = lock.read {
        val key = vaultKey ?: return false

        if (isInBackground) {
            val elapsed = SystemClock.elapsedRealtime() - lastBackgroundedAt
            return elapsed <= Constants.GRACE_PERIOD_MS
        }
        return true
    }

    /**
     * Manually locks the vault and wipes the key reference.
     */
    fun clearKey() = lock.write {
        clearKeyInternal()
    }

    private fun clearKeyInternal() {
        vaultKey = null
        lastBackgroundedAt = 0
        isInBackground = false
    }

    /**
     * Called when the app moves to background. Starts the grace period timer.
     */
    fun onAppBackgrounded() = lock.write {
        if (vaultKey != null) {
            isInBackground = true
            lastBackgroundedAt = SystemClock.elapsedRealtime()
        }
    }

    /**
     * Called when the app returns to foreground. 
     * Validates if the grace period has expired.
     */
    fun onAppForegrounded() = lock.write {
        if (isInBackground) {
            val elapsed = SystemClock.elapsedRealtime() - lastBackgroundedAt
            if (elapsed > Constants.GRACE_PERIOD_MS) {
                clearKeyInternal()
            } else {
                isInBackground = false
                lastBackgroundedAt = 0
            }
        }
    }
}
