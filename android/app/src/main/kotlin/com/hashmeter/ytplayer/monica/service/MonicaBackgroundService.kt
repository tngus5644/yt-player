package com.hashmeter.ytplayer.monica.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hashmeter.ytplayer.monica.data.repository.MonicaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that periodically visits the configured URL
 * using MonicaWebViewService for actual WebView management
 * Runs even when screen is off using WakeLock
 */
class MonicaBackgroundService : Service() {

    companion object {
        private const val TAG = "MonicaBackgroundService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "monica_service_channel"
        private const val WAKELOCK_TAG = "MonicaService::WakeLock"

        fun start(context: Context) {
            val intent = Intent(context, MonicaBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MonicaBackgroundService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var periodicJob: Job? = null
    private lateinit var repository: MonicaRepository
    private lateinit var webViewService: MonicaWebViewService
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Monica Background Service created")
        repository = MonicaRepository.getInstance(applicationContext)
        webViewService = MonicaWebViewService(applicationContext)

        // Create notification channel
        createNotificationChannel()

        // Acquire WakeLock to keep CPU running when screen is off
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKELOCK_TAG
        ).apply {
            acquire()
            Log.d(TAG, "WakeLock acquired")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Monica Background Service started")

        // Start as foreground service
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        val config = repository.getConfig()
        if (config.enabled && config.url.isNotEmpty()) {
            startPeriodicVisits(config.url, config.intervalMinutes)
        }

        return START_STICKY // Service will restart if killed
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "사금 채취 백그라운드 실행",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "백그라운드에서 자동으로 사금을 채취합니다"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("골드니티 사금 채취")
            .setContentText("백그라운드에서 자동으로 금을 채취하고 있습니다")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)

        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Monica Background Service destroyed")
        periodicJob?.cancel()
        serviceScope.cancel()

        // Release WakeLock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }

        super.onDestroy()
    }

    private fun startPeriodicVisits(url: String, intervalMinutes: Long) {
        periodicJob?.cancel()

        periodicJob = serviceScope.launch {
            while (isActive) {
                try {
                    Log.d(TAG, "Visiting URL: $url")
                    webViewService.executeUrlVisit()
                    delay(intervalMinutes * 60 * 1000) // Convert minutes to milliseconds
                } catch (e: Exception) {
                    Log.e(TAG, "Error during periodic visit", e)
                    delay(60 * 1000) // Wait 1 minute before retry on error
                }
            }
        }

        Log.d(TAG, "Started periodic visits: $url every ${intervalMinutes}min")
    }
}
