package com.taskpro.ui

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taskpro.ui.theme.MyApplicationTheme

class AlarmActivity : ComponentActivity() {

  companion object {
    private const val TAG = "AlarmActivity"
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    fun stopAlarm() {
      try {
        ringtone?.let {
          if (it.isPlaying) {
            it.stop()
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to stop ringtone", e)
      }
      ringtone = null

      try {
        vibrator?.cancel()
      } catch (e: Exception) {
        Log.e(TAG, "Failed to cancel vibration", e)
      }
      vibrator = null
    }
  }

  private val taskIdState = mutableStateOf(-1L)
  private val taskTitleState = mutableStateOf("Urgent Task Reminder")
  private val taskDueDateState = mutableStateOf("Scheduled Time reached")

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Enable high-contrast lock screen and wake up configuration
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
      val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
      keyguardManager?.requestDismissKeyguard(this, null)
    } else {
      @Suppress("DEPRECATION")
      window.addFlags(
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
      )
    }

    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    val themePrefs = getSharedPreferences("theme_config", Context.MODE_PRIVATE)
    com.taskpro.ui.theme.AppThemeState.isDark = themePrefs.getBoolean("is_dark_mode", true)

    handleIntent(intent)

    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        AlarmScreen(
          title = taskTitleState.value,
          dueDate = taskDueDateState.value,
          onDismiss = {
            stopAlarm()
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(taskIdState.value.toInt())
            finish()
          }
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent) {
    val taskId = intent.getLongExtra("task_id", -1L)
    val taskTitle = intent.getStringExtra("task_title") ?: "Urgent Task Reminder"
    val taskDueDate = intent.getStringExtra("task_due_date") ?: "Scheduled Time reached"

    taskIdState.value = taskId
    taskTitleState.value = taskTitle
    taskDueDateState.value = taskDueDate

    startAlarmAndVibration()
  }

  private fun startAlarmAndVibration() {
    stopAlarm() // Ensure no overlapping alarm is active

    // 1. Play high priority alarm audio (with fallback options)
    val uris = listOf(
      RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
      RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
      RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    )

    var tone: Ringtone? = null
    for (uri in uris) {
      if (uri == null) continue
      try {
        val possibleTone = RingtoneManager.getRingtone(applicationContext, uri)
        if (possibleTone != null) {
          tone = possibleTone
          break
        }
      } catch (e: Exception) {
        Log.e(TAG, "Ringtone failed for URI: $uri", e)
      }
    }

    if (tone != null) {
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          tone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          tone.isLooping = true
        }
        tone.play()
        ringtone = tone
        Log.d(TAG, "Successfully playing alarm audio")
      } catch (e: Exception) {
        Log.e(TAG, "Failed to play configured ringtone", e)
      }
    } else {
      Log.e(TAG, "No valid ringtone source found during fallbacks")
    }

    // 2. Play continuous vibration pattern
    try {
      val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      if (vib != null && vib.hasVibrator()) {
        val pattern = longArrayOf(0, 800, 800) // Vibrate 800ms, pause 800ms
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          vib.vibrate(VibrationEffect.createWaveform(pattern, 0)) // Loop index 0
        } else {
          vib.vibrate(pattern, 0)
        }
        vibrator = vib
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start vibrator", e)
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    // It's safe to keep the alarm running until explicitly dismissed via button, 
    // but on standard Android closing the activity should clear sound to avoid ghost alarms.
    stopAlarm()
  }
}

@Composable
fun AlarmScreen(
  title: String,
  dueDate: String,
  onDismiss: () -> Unit
) {
  val pulseBg = rememberInfiniteTransition(label = "pulse_background")
  
  val scaleMultiplier by pulseBg.animateFloat(
    initialValue = 1.0f,
    targetValue = 1.25f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "scale"
  )

  val alphaMultiplier by pulseBg.animateFloat(
    initialValue = 0.5f,
    targetValue = 0.05f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "alpha"
  )

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF0F0D13))
      .padding(horizontal = 24.dp, vertical = 48.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    // Header spacer / title
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(top = 40.dp)
    ) {
      Text(
        text = "URGENT TASK ALARM",
        color = Color(0xFFEF4444),
        letterSpacing = 2.sp,
        fontSize = 14.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.testTag("alarm_header")
      )
      
      Spacer(modifier = Modifier.height(10.dp))
      
      Text(
        text = "Your scheduled reminder is ringing now!",
        color = Color(0xFF94A3B8),
        fontSize = 14.sp,
        textAlign = TextAlign.Center
      )
    }

    // Animated Pulsing Icon Area
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .size(240.dp)
        .testTag("alarm_icon_wrapper")
    ) {
      // Glow Ring 1
      Box(
        modifier = Modifier
          .fillMaxSize()
          .scale(scaleMultiplier)
          .clip(CircleShape)
          .background(Color(0xFFEF4444).copy(alpha = alphaMultiplier))
      )
      
      // Glow Ring 2
      Box(
        modifier = Modifier
          .size(160.dp)
          .scale(scaleMultiplier * 0.9f)
          .clip(CircleShape)
          .background(Color(0xFFD0BCFF).copy(alpha = alphaMultiplier * 0.7f))
      )

      // Primary Center Ring
      Box(
        modifier = Modifier
          .size(120.dp)
          .clip(CircleShape)
          .background(Color(0xFF1C1B21))
          .border(2.dp, Color(0xFFEF4444), CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.NotificationsActive,
          contentDescription = "Ringing Reminder",
          tint = Color(0xFFEF4444),
          modifier = Modifier.size(54.dp)
        )
      }
    }

    // Task Details Container Card
    Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1B21)),
      border = BorderStroke(1.2.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp)
        .testTag("alarm_task_details_card")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = title,
          color = Color(0xFFF1F5F9),
          fontWeight = FontWeight.Black,
          fontSize = 22.sp,
          textAlign = TextAlign.Center,
          lineHeight = 28.sp,
          modifier = Modifier.testTag("alarm_task_title")
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Alarm,
            contentDescription = "Due Date icon",
            tint = Color(0xFFEF4444),
            modifier = Modifier.size(16.dp)
          )
          Text(
            text = "Due: $dueDate",
            color = Color(0xFF94A3B8),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("alarm_task_due_date")
          )
        }
      }
    }

    // Large high-contrast "Dismiss Alarm" Button
    Button(
      onClick = onDismiss,
      shape = RoundedCornerShape(20.dp),
      colors = ButtonDefaults.buttonColors(
        containerColor = Color(0xFFEF4444),
        contentColor = Color.White
      ),
      elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(64.dp)
        .testTag("dismiss_alarm_button")
    ) {
      Text(
        text = "DISMISS ALARM",
        fontSize = 16.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.2.sp
      )
    }
  }
}
