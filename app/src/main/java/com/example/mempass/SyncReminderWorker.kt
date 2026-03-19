package com.example.mempass

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.mempass.common.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import javax.inject.Named

@HiltWorker
class SyncReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    @Named("SecurityPrefs") private val prefs: SharedPreferences
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val isDirty = prefs.getBoolean(Constants.KEY_IS_VAULT_DIRTY, false)
        val lastBackupTime = prefs.getLong(Constants.KEY_LAST_BACKUP_TIME, 0L)
        val currentTime = System.currentTimeMillis()
        
        // Check if data is modified and last backup was more than 24 hours ago
        val oneDayMillis = 24 * 60 * 60 * 1000L
        if (isDirty && (currentTime - lastBackupTime) > oneDayMillis) {
            showNotification()
        }

        return Result.success()
    }

    private fun showNotification() {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "sync_reminder_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Vault Sync Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminds you to backup your vault when changes are detected"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_shield_indigo)
            .setContentTitle(applicationContext.getString(R.string.sync_reminder_title))
            .setContentText(applicationContext.getString(R.string.sync_reminder_msg))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }

    companion object {
        private const val WORK_NAME = "SyncReminderPeriodic"

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<SyncReminderWorker>(24, TimeUnit.HOURS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }
    }
}
