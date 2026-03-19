package com.example.mempass

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class NoteSelfDestructWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repository: VaultRepository
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            
            val expiredNotes = repository.allNotes.first().filter { 
                it.selfDestructAt != null && it.selfDestructAt!! <= now 
            }
            
            if (expiredNotes.isNotEmpty()) {
                Log.d("NoteSelfDestructWorker", "Deleting ${expiredNotes.size} expired notes")
                expiredNotes.forEach { repository.deleteNote(it) }
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e("NoteSelfDestructWorker", "Error processing self-destruct notes", e)
            Result.retry()
        }
    }
}
