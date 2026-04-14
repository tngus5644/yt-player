package com.hashmeter.ytplayer.monica.worker

import android.content.Context
import android.os.PowerManager
import android.util.Log
import androidx.work.*
import com.hashmeter.ytplayer.monica.data.repository.MonicaRepository
import com.hashmeter.ytplayer.monica.service.MonicaWebViewService
import com.hashmeter.ytplayer.monica.util.LiveCountFetcher
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class MonicaWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    companion object {
        private const val TAG = "MonicaWorker"
        private const val WORK_NAME = "monica_periodic_work"
        private const val WAKELOCK_TAG = "MonicaWorker::WakeLock"

        /**
         * Schedule periodic Monica work with WakeLock support
         * Minimum interval: 15 minutes (WorkManager limitation)
         */
        fun schedule(context: Context, intervalMinutes: Long = 15) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false) // Allow execution even with low battery
                .setRequiresDeviceIdle(false) // Don't wait for device to be idle
                .build()

            val workRequest = PeriodicWorkRequest.Builder(
                MonicaWorker::class.java,
                intervalMinutes, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .setInitialDelay(0, TimeUnit.MINUTES) // Start immediately
                .addTag("monica_background") // Tag for tracking
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // Keep existing work to maintain schedule
                workRequest
            )

            Log.d(TAG, "Monica work scheduled with interval: $intervalMinutes minutes")
        }

        /**
         * Cancel Monica work
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Monica work cancelled")
        }

        /**
         * Execute one-time Monica work immediately
         */
        fun executeNow(context: Context) {
            val workRequest = OneTimeWorkRequest.Builder(MonicaWorker::class.java)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "One-time Monica work scheduled")
        }
    }

    override fun doWork(): Result {
        Log.d(TAG, "========== Monica Worker Started ==========")

        // Check execution conditions
        val (shouldExecute, reason) = WorkerCondition.checkConditions(applicationContext)

        if (!shouldExecute) {
            Log.d(TAG, "⏭️ Skipping Monica execution: $reason")
            Log.d(TAG, "   Status: ${WorkerCondition.getDetailedStatus(applicationContext)}")
            return Result.success()
        }

        Log.d(TAG, "✅ Execution conditions met: $reason")
        Log.d(TAG, "   Status: ${WorkerCondition.getDetailedStatus(applicationContext)}")

        // Acquire WakeLock to keep CPU running during execution
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKELOCK_TAG
        )

        return try {

            // Acquire WakeLock with 10 minute timeout (maximum expected execution time)
            wakeLock.acquire(10 * 60 * 1000L)
            Log.d(TAG, "WakeLock acquired")

            // STEP 1: Fetch latest URL from LiveCount API
            Log.d(TAG, "📡 Fetching latest URL from LiveCount API...")
            val urlFetchSuccess = runBlocking {
                LiveCountFetcher.fetchAndUpdateUrl(applicationContext)
            }

            if (!urlFetchSuccess) {
                Log.w(TAG, "⚠️ LiveCount URL fetch failed (empty array or error), skipping execution")
                return Result.success()
            }

            Log.d(TAG, "✅ LiveCount URLs updated successfully")

            val repository = MonicaRepository.getInstance(applicationContext)
            val config = repository.getConfig()

            if (!config.enabled) {
                Log.d(TAG, "Monica is disabled, skipping execution")
                return Result.success()
            }

            // Get all URLs to visit
            val urls = repository.getUrls()
            if (urls.isEmpty()) {
                Log.w(TAG, "No URLs configured, skipping execution")
                return Result.success()
            }

            // STEP 2: Execute Monica URL visits sequentially
            Log.d(TAG, "🌐 Executing Monica URL visits: ${urls.size} URLs")

            val service = MonicaWebViewService(applicationContext)
            urls.forEachIndexed { index, url ->
                Log.d(TAG, "[$index/${urls.size}] Visiting: $url")
                service.executeUrlVisit(url)
            }

            Log.d(TAG, "✅ Monica execution initiated successfully (${urls.size} URLs)")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Monica worker failed", e)
            Result.retry()
        } finally {
            // Release WakeLock
            if (wakeLock.isHeld) {
                wakeLock.release()
                Log.d(TAG, "WakeLock released")
            }
            Log.d(TAG, "========== Monica Worker Finished ==========")
        }
    }
}
