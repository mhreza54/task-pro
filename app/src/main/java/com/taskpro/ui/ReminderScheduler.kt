package com.taskpro.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.taskpro.model.Task
import com.taskpro.receiver.ReminderReceiver
import java.text.SimpleDateFormat
import java.util.*

object ReminderScheduler {
  private const val TAG = "ReminderScheduler"

  fun scheduleReminder(context: Context, task: Task) {
    if (task.reminderMinutes == -1 || task.isCompleted) {
      cancelReminder(context, task)
      return
    }

    val triggerMs = calculateTriggerTime(task.dueDate, task.reminderMinutes)
    if (triggerMs <= System.currentTimeMillis()) {
      Log.d(TAG, "Trigger time is in the past, skipping reminder.")
      cancelReminder(context, task)
      return
    }

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val intent = Intent(context, ReminderReceiver::class.java).apply {
      putExtra("task_id", task.id)
      putExtra("task_title", task.title)
      putExtra("task_due_date", task.dueDate)
    }

    val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else {
      PendingIntent.FLAG_UPDATE_CURRENT
    }

    val pendingIntent = PendingIntent.getBroadcast(
      context,
      task.id.toInt(),
      intent,
      flag
    )

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (alarmManager.canScheduleExactAlarms()) {
          alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerMs,
            pendingIntent
          )
          Log.d(TAG, "Exact reminder scheduled for task: ${task.title} at $triggerMs")
        } else {
          // Try setAlarmClock which is exact and wakes up the device
          try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerMs, pendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d(TAG, "Exact Alarm permission missing; scheduled via setAlarmClock for: ${task.title}")
          } catch (e2: Exception) {
            // Fallback to non-exact but Doze-bypassing alarm
            alarmManager.setAndAllowWhileIdle(
              AlarmManager.RTC_WAKEUP,
              triggerMs,
              pendingIntent
            )
            Log.d(TAG, "setAlarmClock failed; scheduled via setAndAllowWhileIdle for: ${task.title}")
          }
        }
      } else {
        alarmManager.setExactAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          triggerMs,
          pendingIntent
        )
        Log.d(TAG, "Reminder scheduled exact (<S) for task: ${task.title}")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error scheduling alarm, using standby fallback setAndAllowWhileIdle()", e)
      try {
        alarmManager.setAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          triggerMs,
          pendingIntent
        )
      } catch (ex: Exception) {
        Log.e(TAG, "Fallback alarm scheduling also failed", ex)
      }
    }
  }

  fun cancelReminder(context: Context, task: Task) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val intent = Intent(context, ReminderReceiver::class.java)
    val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    } else {
      PendingIntent.FLAG_NO_CREATE
    }

    val pendingIntent = PendingIntent.getBroadcast(
      context,
      task.id.toInt(),
      intent,
      flag
    )

    if (pendingIntent != null) {
      alarmManager.cancel(pendingIntent)
      pendingIntent.cancel()
      Log.d(TAG, "Successfully cancelled reminder for task: ${task.title}")
    }
  }

  private fun calculateTriggerTime(dueDate: String, reminderMinutes: Int): Long {
    return try {
      val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
      val date = formatter.parse(dueDate) ?: return 0L
      date.time - (reminderMinutes * 60 * 1000L)
    } catch (e: Exception) {
      Log.e(TAG, "Error parsing date: $dueDate", e)
      0L
    }
  }
}
