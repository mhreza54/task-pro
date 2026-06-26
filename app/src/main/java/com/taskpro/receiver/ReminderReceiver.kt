package com.taskpro.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.taskpro.ui.AlarmActivity

class ReminderReceiver : BroadcastReceiver() {
  companion object {
    private const val TAG = "ReminderReceiver"
    private const val CHANNEL_ID = "task_channel_alarms"
    private const val CHANNEL_NAME = "Urgent Task Alarms"
  }

  override fun onReceive(context: Context, intent: Intent) {
    val taskId = intent.getLongExtra("task_id", -1L)
    val taskTitle = intent.getStringExtra("task_title") ?: "Urgent Task Reminder"
    val taskDueDate = intent.getStringExtra("task_due_date") ?: ""

    Log.d(TAG, "OnReceive triggered for taskId: $taskId, title: $taskTitle")

    if (taskId == -1L) return

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

    // Create a high-priority Notification Channel for Android O (API 26+) specifically for alarms
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        CHANNEL_NAME,
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Urgent full-screen alarms and reminders"
        enableLights(true)
        enableVibration(true)
        setBypassDnd(true) // Attempt to bypass Do Not Disturb so urgent alarms come through
      }
      notificationManager.createNotificationChannel(channel)
    }

    // 1. Create the Intent to launch the full-screen Alarm Screen
    val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra("task_id", taskId)
      putExtra("task_title", taskTitle)
      putExtra("task_due_date", taskDueDate)
    }

    val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else {
      PendingIntent.FLAG_UPDATE_CURRENT
    }

    // PendingIntent designed specifically as a FullScreen Intent
    val fullScreenPendingIntent = PendingIntent.getActivity(
      context,
      taskId.toInt(),
      alarmIntent,
      flag
    )

    // 2. Build the backup notification containing the full-screen trigger
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
      .setContentTitle("Urgent Task Reminder!")
      .setContentText("$taskTitle (Due: $taskDueDate)")
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setFullScreenIntent(fullScreenPendingIntent, true) // Required for lock screen overlay launch
      .setAutoCancel(false)
      .setOngoing(true) // Alarm keeps going until user explicitly dismisses
      .setColor(0xFFEF4444.toInt()) // Red color matching the alarm profile
      .build()

    // Post backup notification
    notificationManager.notify(taskId.toInt(), notification)

    // 3. Proactively attempt to directly launch AlarmActivity as well (useful for unlocked screen and instant coverage)
    try {
      context.startActivity(alarmIntent)
    } catch (e: Exception) {
      Log.e(TAG, "Direct activity launch failed or restricted; relying on full-screen notification intent", e)
    }
  }
}
