package com.example.mempass

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class DocumentExpiryWorker @AssistedInject constructor(
    @Assisted private val ctx: Context,
    @Assisted params: WorkerParameters,
    private val repository: VaultRepository
) : CoroutineWorker(ctx, params) {

    companion object {
        const val CHANNEL_ID = "document_reminders"
        const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            val thirtyDaysMillis = TimeUnit.DAYS.toMillis(30)
            val sevenDaysMillis = TimeUnit.DAYS.toMillis(7)

            val documents = repository.allDocuments.first()
            val expiringDocs = documents.filter { doc ->
                doc.expiryDate != null && doc.expiryDate!! > 0 && 
                (doc.expiryDate!! - now) <= thirtyDaysMillis && doc.expiryDate!! > now
            }

            if (expiringDocs.isNotEmpty()) {
                expiringDocs.forEach { doc ->
                    val daysLeft = TimeUnit.MILLISECONDS.toDays(doc.expiryDate!! - now)
                    if (daysLeft == 30L || daysLeft == 7L || daysLeft <= 3L) {
                        showNotification(doc.title, daysLeft)
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("DocumentExpiryWorker", "Error checking document expiry", e)
            Result.retry()
        }
    }

    private fun showNotification(docTitle: String, daysLeft: Long) {
        val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Document Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for document expiry dates"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val message = if (daysLeft <= 0) {
            "Your document '$docTitle' has expired!"
        } else {
            "Your document '$docTitle' expires in $daysLeft days."
        }

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure this icon exists
            .setContentTitle("Document Expiry Alert")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(docTitle.hashCode(), notification)
    }
}
