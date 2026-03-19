package com.example.mempass

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.mempass.common.Constants
import com.google.android.gms.auth.api.signin.GoogleSignIn
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Arrays
import java.util.concurrent.TimeUnit

@HiltWorker
class DriveBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val vaultManager: VaultManager,
    private val backupHelper: VaultBackupHelper
) : CoroutineWorker(context, workerParams) {

    private val TAG = "DriveBackupWorker"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs: SharedPreferences = applicationContext.getSharedPreferences(
            Constants.PREFS_SECURITY,
            Context.MODE_PRIVATE
        )

        val isAutoBackupEnabled = prefs.getBoolean(Constants.KEY_AUTO_BACKUP_ENABLED, false)
        if (!isAutoBackupEnabled) {
            Log.d(TAG, "Auto-backup is disabled. Skipping.")
            return@withContext Result.success()
        }

        val key = vaultManager.getKey()
        if (key == null) {
            Log.d(TAG, "Vault locked, cannot access data for auto-backup. Retrying later.")
            return@withContext Result.retry()
        }

        return@withContext try {
            val account = GoogleSignIn.getLastSignedInAccount(applicationContext)
            if (account != null) {
                Log.d(TAG, "Starting incremental auto-backup for account: ${account.email}")
                
                // SECURITY FIX (#7): Use the stored Recovery Key instead of a weak PIN hash.
                // The recovery key is stored as a wrapped Keystore blob for extra protection.
                val wrappedRKey = prefs.getString(Constants.KEY_RECOVERY_KEY_STORED, null)
                if (wrappedRKey == null) {
                    Log.e(TAG, "Stored recovery key missing, cannot derive sync password")
                    return@withContext Result.failure()
                }
                
                val rKeyBytes = KeystoreHelper.unwrapToBytes(wrappedRKey)
                if (rKeyBytes == null) {
                    Log.e(TAG, "Failed to unwrap recovery key from Keystore")
                    return@withContext Result.failure()
                }

                val syncPassChars = CryptoUtils.bytesToChars(rKeyBytes)
                
                var successResult = false
                try {
                    backupHelper.performDriveSync(
                        applicationContext, account, key, syncPassChars
                    ) { success, _ ->
                        successResult = success
                    }
                } finally {
                    // Critical: Wipe sensitive data immediately after use
                    Arrays.fill(rKeyBytes, 0.toByte())
                    Arrays.fill(syncPassChars, ' ')
                }
                
                if (successResult) {
                    Log.i(TAG, "Incremental auto-backup successful")
                    Result.success()
                } else {
                    Log.w(TAG, "Incremental auto-backup failed or deferred")
                    Result.retry()
                }
            } else {
                Log.e(TAG, "No Google account signed in for auto-backup")
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during auto-backup", e)
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "IncrementalDriveBackup"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(
                Constants.DEFAULT_BACKUP_INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.i("DriveBackupWorker", "Auto-backup scheduled every ${Constants.DEFAULT_BACKUP_INTERVAL_HOURS} hours")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.i("DriveBackupWorker", "Auto-backup cancelled")
        }
    }
}
