package com.taskpro

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.taskpro.ui.TaskApp
import com.taskpro.ui.TaskViewModel
import com.taskpro.ui.theme.AppThemeState
import com.taskpro.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  private val viewModel: TaskViewModel by viewModels {
    TaskViewModel.Factory(application)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Handle deep links when starting-up
    handleIntentDeepLink(intent)

    val themePrefs = getSharedPreferences("theme_config", Context.MODE_PRIVATE)
    AppThemeState.isDark = themePrefs.getBoolean("is_dark_mode", true)

    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        TaskApp(viewModel = viewModel)
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntentDeepLink(intent)
  }

  private fun handleIntentDeepLink(intent: Intent?) {
    val data: Uri? = intent?.data
    if (data != null) {
      viewModel.handleDeepLink(data)
    }
  }
}

