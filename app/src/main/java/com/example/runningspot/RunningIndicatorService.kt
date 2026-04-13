package com.example.runningspot
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class RunningIndicatorService : Service() {

    companion object {
        const val CHANNEL_ID = "running_indicator_channel"
        const val NOTIFICATION_ID = 3001

        const val ACTION_START = "action_start_running_indicator"
        const val ACTION_UPDATE = "action_update_running_indicator"
        const val ACTION_STOP = "action_stop_running_indicator"

        const val EXTRA_TIME = "extra_time"
        const val EXTRA_DISTANCE_KM = "extra_distance_km"
        const val EXTRA_PACE = "extra_pace"
        const val EXTRA_IS_PAUSED = "extra_is_paused"
    }

    private var isForegroundStarted = false
    private var currentTime = "00:00"
    private var currentDistance = "0.0 km"
    private var currentPace = "-'--\""
    private var currentPaused = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentTime = intent.getStringExtra(EXTRA_TIME) ?: "00:00"
                currentDistance = intent.getStringExtra(EXTRA_DISTANCE_KM) ?: "0.0 km"
                currentPace = intent.getStringExtra(EXTRA_PACE) ?: "-'--\""
                currentPaused = intent.getBooleanExtra(EXTRA_IS_PAUSED, false)

                startForeground(
                    NOTIFICATION_ID,
                    buildNotification()
                )
                isForegroundStarted = true
            }

            ACTION_UPDATE -> {
                currentTime = intent.getStringExtra(EXTRA_TIME) ?: currentTime
                currentDistance = intent.getStringExtra(EXTRA_DISTANCE_KM) ?: currentDistance
                currentPace = intent.getStringExtra(EXTRA_PACE) ?: currentPace
                currentPaused = intent.getBooleanExtra(EXTRA_IS_PAUSED, currentPaused)

                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, buildNotification())
            }

            ACTION_STOP -> {
                isForegroundStarted = false
                val nm = getSystemService(NotificationManager::class.java)
                nm.cancel(NOTIFICATION_ID)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    stopForeground(true)
                }
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {

        val openIntent = Intent(this, RunningActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val openPendingIntent = PendingIntent.getActivity(
            this, 3001, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = Intent(this, RunningIndicatorActionReceiver::class.java).apply {
            action = RunningIndicatorActionReceiver.ACTION_PAUSE_RESUME
        }

        val pauseResumePendingIntent = PendingIntent.getBroadcast(
            this, 3002, pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RunningIndicatorActionReceiver::class.java).apply {
            action = RunningIndicatorActionReceiver.ACTION_STOP
        }

        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 3003, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)

            .setContentTitle(if (currentPaused) "⏸ $currentDistance" else "🏃 $currentDistance")
            .setContentText("$currentTime · $currentPace")

            .setContentIntent(openPendingIntent)

            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1) // 접힌 상태에서도 버튼 보이게
            )

            .addAction(
                android.R.drawable.ic_media_pause,
                if (currentPaused) "▶" else "⏸",
                pauseResumePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "■",
                stopPendingIntent
            )

            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "러닝 인디케이터",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "러닝 중 실시간 현황 표시"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}