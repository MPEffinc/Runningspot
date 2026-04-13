package com.example.runningspot

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.runningspot.data.repository.RunRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class GoalReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.success()
        val uid = user.uid
        val db = FirebaseFirestore.getInstance()

        return runCatching {
            val userDoc = db.collection("users").document(uid).get().await()

            val goalKm = userDoc.getDouble("dailyGoalKm") ?: 0.0
            val reminderEnabled = userDoc.getBoolean("goalReminderEnabled") ?: true

            if (!reminderEnabled || goalKm <= 0.0) return Result.success()

            val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(Calendar.getInstance().time)

            val prefs = applicationContext.getSharedPreferences("goal_reminder", Context.MODE_PRIVATE)
            val lastNotifiedDate = prefs.getString("last_notified_date", null)

            if (lastNotifiedDate == todayKey) {
                return Result.success()
            }

            val calendar = Calendar.getInstance()

            val endOfToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis

            val startOfToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val runRepository = RunRepository()
            val myRuns = runRepository.getMyRuns()

            val todayDistanceKm = myRuns
                .filter { it.ended_at in startOfToday..endOfToday }
                .sumOf { it.distance_m } / 1000.0

            if (todayDistanceKm < goalKm) {
                showNotification(goalKm, todayDistanceKm)

                prefs.edit()
                    .putString("last_notified_date", todayKey)
                    .apply()
            }

            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    private fun showNotification(goalKm: Double, todayKm: Double) {
        createChannelIfNeeded()

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("targetType", "goal_reminder")
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            2001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val remain = (goalKm - todayKm).coerceAtLeast(0.0)

        val notification = NotificationCompat.Builder(applicationContext, "goal_reminder_channel")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("오늘 목표가 아직 남았어요")
            .setContentText("현재 ${"%.1f".format(todayKm)}km / 목표 ${"%.1f".format(goalKm)}km · ${"%.1f".format(remain)}km 더 달리면 돼요!")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "현재 ${"%.1f".format(todayKm)}km / 목표 ${"%.1f".format(goalKm)}km\n${"%.1f".format(remain)}km 더 달리면 오늘 목표를 달성할 수 있어요."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(2001, notification)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                "goal_reminder_channel",
                "목표 리마인드",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }
    }
}