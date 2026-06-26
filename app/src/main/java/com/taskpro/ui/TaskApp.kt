package com.taskpro.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taskpro.model.Task
import java.text.SimpleDateFormat
import java.util.*

import com.taskpro.ui.theme.AppThemeState

// Design Palette - "Sleek Interface" Dark slate-purple & Lavenders
val DarkBackground: Color
  get() = if (AppThemeState.isDark) Color(0xFF0F0D13) else Color(0xFFF3F4F6) // Sleek rich dark slate-black / Warm light gray

val CardBackground: Color
  get() = if (AppThemeState.isDark) Color(0xFF1C1B21) else Color(0xFFFFFFFF) // M3 deep slate-purple card / Pure white

val PureWhite: Color
  get() = if (AppThemeState.isDark) Color(0xFFF1F5F9) else Color(0xFF0F0D13) // Slate-100 high emphasis / Slate-950 dark text

val MutedText: Color
  get() = if (AppThemeState.isDark) Color(0xFF94A3B8) else Color(0xFF475569) // Slate-400 / Slate-600

val AccordPurple: Color
  get() = if (AppThemeState.isDark) Color(0xFFD0BCFF) else Color(0xFF5D3FBE) // Prime lavender / Deeper indigo violet

val AccordWhite: Color
  get() = if (AppThemeState.isDark) Color(0xFFF1F5F9) else Color(0xFF0F0D13)

val GlowAccentPurple: Color
  get() = if (AppThemeState.isDark) Color(0xFFF1F5F9) else Color(0xFF6750A4)

// Priority accents matching the theme gradient profile
val LowPriorityColor: Color
  get() = if (AppThemeState.isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

val MediumPriorityColor: Color
  get() = if (AppThemeState.isDark) Color(0xFFB5A9D4) else Color(0xFF6750A4)

val HighPriorityColor: Color
  get() = if (AppThemeState.isDark) Color(0xFFD0BCFF) else Color(0xFFB3261E)

// Category specific visual palettes and icons
fun getCategoryColor(category: String): Color {
  return when (category.trim().lowercase()) {
    "job application" -> Color(0xFF60A5FA) // Sweet light blue
    "movies" -> Color(0xFFF472B6) // Sweet pink/magenta
    "shopping" -> Color(0xFFFBBF24) // Golden Amber
    "health" -> Color(0xFF34D399) // Mint green
    else -> Color(0xFF94A3B8) // Slate gray for 'Other' / fallback
  }
}

fun getCategoryIcon(category: String): androidx.compose.ui.graphics.vector.ImageVector {
  return when (category.trim().lowercase()) {
    "job application" -> Icons.Default.Work
    "movies" -> Icons.Default.Movie
    "shopping" -> Icons.Default.ShoppingCart
    "health" -> Icons.Default.Favorite
    else -> Icons.Default.Tag
  }
}

fun getTimeOfDayGreeting(displayName: String, currentHour: Int): String {
  val intro = when (currentHour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..20 -> "Good evening"
    else -> "Good night"
  }
  return "$intro, $displayName 👋"
}

fun isTaskOverdue(task: Task, currentMillis: Long = System.currentTimeMillis()): Boolean {
  if (task.isCompleted) return false
  return try {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val date = formatter.parse(task.dueDate)
    date != null && date.time < currentMillis
  } catch (e: Exception) {
    false
  }
}

fun isTaskDueToday(task: Task, currentMillis: Long = System.currentTimeMillis()): Boolean {
  return try {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val taskDate = formatter.parse(task.dueDate) ?: return false
    
    val calTask = Calendar.getInstance().apply { time = taskDate }
    val calToday = Calendar.getInstance().apply { timeInMillis = currentMillis }
    
    calTask.get(Calendar.YEAR) == calToday.get(Calendar.YEAR) &&
      calTask.get(Calendar.DAY_OF_YEAR) == calToday.get(Calendar.DAY_OF_YEAR)
  } catch (e: Exception) {
    false
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskApp(viewModel: TaskViewModel) {
  val context = LocalContext.current
  val tasks by viewModel.tasks.collectAsStateWithLifecycle()
  val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()
  val filter by viewModel.filter.collectAsStateWithLifecycle()
  val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
  val sortBy by viewModel.sortBy.collectAsStateWithLifecycle()
  val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
  val userDisplayName by viewModel.userDisplayName.collectAsStateWithLifecycle()

  val isConfigured by viewModel.isConfigured.collectAsStateWithLifecycle()
  val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
  val syncStatus by viewModel.syncStatusString.collectAsStateWithLifecycle()
  val error by viewModel.errorMessage.collectAsStateWithLifecycle()
  val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsStateWithLifecycle()
  val sessionChecked by viewModel.sessionChecked.collectAsStateWithLifecycle()

  val totalCount = allTasks.count { !it.isCompleted && !isTaskOverdue(it) }
  val completedCount = allTasks.count { it.isCompleted }
  val overdueCount = allTasks.count { !it.isCompleted && isTaskOverdue(it) }
  val dueTodayCount = allTasks.count { isTaskDueToday(it) }

  var isSettingsOpen by remember { mutableStateOf(false) }
  var showLogoutConfirmation by remember { mutableStateOf(false) }
  var isAddTaskOpen by remember { mutableStateOf(false) }
  var editingTask by remember { mutableStateOf<Task?>(null) }
  var taskToDelete by remember { mutableStateOf<Task?>(null) }
  var activeDetailTask by remember { mutableStateOf<Task?>(null) }
  var activeFilterPage by remember { mutableStateOf<TaskFilter?>(null) }

  // Back navigation handlers for overlays, dialogs, and subpages
  BackHandler(enabled = taskToDelete != null) {
    taskToDelete = null
  }

  BackHandler(enabled = showLogoutConfirmation) {
    showLogoutConfirmation = false
  }

  BackHandler(enabled = isSettingsOpen) {
    isSettingsOpen = false
  }

  BackHandler(enabled = activeDetailTask != null) {
    activeDetailTask = null
  }

  BackHandler(enabled = editingTask != null) {
    editingTask = null
  }

  BackHandler(enabled = isAddTaskOpen) {
    isAddTaskOpen = false
  }

  BackHandler(enabled = activeFilterPage != null) {
    activeFilterPage = null
    viewModel.setFilter(TaskFilter.DUE_TODAY)
    viewModel.setCategoryFilter("All")
  }

  val currentFilterState by viewModel.filter.collectAsStateWithLifecycle()
  val categoryFilterState by viewModel.categoryFilter.collectAsStateWithLifecycle()
  val noDialogsOrSubpagesOpen = taskToDelete == null && !showLogoutConfirmation && !isSettingsOpen && activeDetailTask == null && editingTask == null && !isAddTaskOpen && activeFilterPage == null

  BackHandler(enabled = noDialogsOrSubpagesOpen && (currentFilterState != TaskFilter.DUE_TODAY || categoryFilterState != "All")) {
    viewModel.setFilter(TaskFilter.DUE_TODAY)
    viewModel.setCategoryFilter("All")
  }

  // Handle dynamic system notification permission checks (for Android 13+)
  val hasNotificationPermission = remember {
    mutableStateOf(
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        androidx.core.content.ContextCompat.checkSelfPermission(
          context,
          android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
      } else {
        true
      }
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    hasNotificationPermission.value = isGranted
    if (!isGranted) {
      Toast.makeText(
        context,
        "Notification permission denied. Task Reminders won't show alerts.",
        Toast.LENGTH_LONG
      ).show()
    }
  }

  LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (androidx.core.content.ContextCompat.checkSelfPermission(
          context,
          android.Manifest.permission.POST_NOTIFICATIONS
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
      ) {
        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  // Handle toast notifications for sync/error statuses
  LaunchedEffect(error) {
    error?.let {
      Toast.makeText(context, it, Toast.LENGTH_LONG).show()
      viewModel.clearError()
    }
  }

  if (!sessionChecked) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(DarkBackground),
      contentAlignment = Alignment.Center
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        CircularProgressIndicator(color = AccordPurple)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = "Re-establishing connection...",
          color = PureWhite,
          fontSize = 14.sp,
          fontWeight = FontWeight.Medium
        )
      }
    }
  } else if (!isUserLoggedIn) {
    LoginScreen(viewModel = viewModel)
  } else {
    Scaffold(
      modifier = Modifier
        .fillMaxSize()
        .background(DarkBackground),
    topBar = {
      if (activeFilterPage == null) {
        TopAppBar(
          title = {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.TaskAlt,
                contentDescription = "App Logo",
                tint = AccordPurple,
                modifier = Modifier.size(28.dp)
              )
              Column {
                Text(
                  text = "TASK PRO",
                  color = PureWhite,
                  fontWeight = FontWeight.Bold,
                  fontSize = 18.sp,
                  fontFamily = FontFamily.SansSerif
                )
                val displayNameToShow = remember(userDisplayName, userEmail, isConfigured) {
                  if (!userDisplayName.isNullOrEmpty()) {
                    userDisplayName!!.trim()
                  } else if (!userEmail.isNullOrEmpty()) {
                    userEmail!!.trim()
                  } else if (isConfigured) {
                    "Connected Offline"
                  } else {
                    "User"
                  }
                }
                Text(
                  text = displayNameToShow,
                  color = if (!userDisplayName.isNullOrEmpty() || !userEmail.isNullOrEmpty() || isConfigured) AccordPurple else GlowAccentPurple.copy(alpha = 0.5f),
                  fontSize = 11.sp,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.widthIn(max = 160.dp)
                )
              }
            }
          },
          actions = {
            // Dynamic Dark/Light Mode Theme Toggle
            IconButton(
              onClick = {
                AppThemeState.isDark = !AppThemeState.isDark
                val themePrefs = context.getSharedPreferences("theme_config", Context.MODE_PRIVATE)
                themePrefs.edit().putBoolean("is_dark_mode", AppThemeState.isDark).apply()
              },
              modifier = Modifier.testTag("theme_mode_toggle_button")
            ) {
              Icon(
                imageVector = if (AppThemeState.isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                contentDescription = "Toggle Light/Dark Mode",
                tint = AccordPurple
              )
            }

            // Settings button
            IconButton(
              onClick = { isSettingsOpen = true },
              modifier = Modifier.testTag("settings_button")
            ) {
              Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = AccordPurple
              )
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = DarkBackground,
            titleContentColor = PureWhite
          )
        )
      } else {
        TopAppBar(
          title = {
            Column {
              val titleText = when (activeFilterPage) {
                TaskFilter.ALL -> "Total Tasks"
                TaskFilter.COMPLETED -> "Completed Tasks"
                TaskFilter.OVERDUE -> "Overdue Tasks"
                TaskFilter.DUE_TODAY -> "Due Today Tasks"
                else -> "Tasks"
              }
              Text(
                text = titleText,
                color = PureWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                fontFamily = FontFamily.SansSerif
              )
              val count = when (activeFilterPage) {
                TaskFilter.ALL -> totalCount
                TaskFilter.COMPLETED -> completedCount
                TaskFilter.OVERDUE -> overdueCount
                TaskFilter.DUE_TODAY -> dueTodayCount
                else -> tasks.size
              }
              Text(
                text = "$count tasks found",
                color = MutedText,
                fontSize = 11.sp
              )
            }
          },
          navigationIcon = {
            IconButton(
              onClick = {
                activeFilterPage = null
                viewModel.setFilter(TaskFilter.DUE_TODAY)
                viewModel.setCategoryFilter("All")
              },
              modifier = Modifier.testTag("subpage_back_button")
            ) {
              Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back to Dashboard",
                tint = AccordPurple
              )
            }
          },
          actions = {
            // Dynamic Dark/Light Mode Theme Toggle
            IconButton(
              onClick = {
                AppThemeState.isDark = !AppThemeState.isDark
                val themePrefs = context.getSharedPreferences("theme_config", Context.MODE_PRIVATE)
                themePrefs.edit().putBoolean("is_dark_mode", AppThemeState.isDark).apply()
              },
              modifier = Modifier.testTag("theme_mode_toggle_button")
            ) {
              Icon(
                imageVector = if (AppThemeState.isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                contentDescription = "Toggle Light/Dark Mode",
                tint = AccordPurple
              )
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = DarkBackground,
            titleContentColor = PureWhite
          )
        )
      }
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { isAddTaskOpen = true },
        containerColor = AccordPurple,
        contentColor = Color(0xFF381E72),
        shape = RoundedCornerShape(16.dp),
        elevation = FloatingActionButtonDefaults.elevation(8.dp),
        modifier = Modifier
          .padding(bottom = 16.dp, end = 8.dp)
          .testTag("add_task_fab")
      ) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = "Add New Task",
          modifier = Modifier.size(28.dp)
        )
      }
    },
    containerColor = DarkBackground
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(horizontal = 16.dp)
    ) {
      if (activeFilterPage == null) {
        // --- HOME SCREEN LAYOUT ---
        if (filter == TaskFilter.DUE_TODAY) {
          val displayName = if (!userDisplayName.isNullOrEmpty()) {
            userDisplayName
          } else if (!userEmail.isNullOrEmpty()) {
            userEmail!!.substringBefore("@")
          } else {
            "User"
          }

          val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
          val greeting = getTimeOfDayGreeting(displayName ?: "User", currentHour)

          val dateFormatter = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
          val dateString = dateFormatter.format(Date())

          val motivationalText = when {
            dueTodayCount == 0 -> "You're all clear for today! 🎉"
            dueTodayCount in 1..3 -> "You've got a light day ahead. Stay focused!"
            else -> "Busy day ahead — take it one task at a time."
          }

          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 8.dp)
              .testTag("today_greeting_section")
          ) {
            Text(
              text = greeting,
              color = PureWhite,
              fontWeight = FontWeight.Bold,
              fontSize = 22.sp,
              lineHeight = 28.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = dateString,
              color = MutedText,
              fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = motivationalText,
              color = AccordPurple,
              fontSize = 13.sp,
              fontWeight = FontWeight.Medium,
              lineHeight = 18.sp
            )
          }
          
          Spacer(modifier = Modifier.height(8.dp))
        }

        // Supabase connection strip info if not configured
        if (!isConfigured) {
          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, AccordPurple.copy(alpha = 0.3f)),
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 8.dp)
              .clickable { isSettingsOpen = true }
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = "Configure",
                tint = AccordPurple,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Use Supabase Cloud Database",
                  color = PureWhite,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "Tap to configure Url & Key for secure cloud syncing.",
                  color = MutedText,
                  fontSize = 11.sp
                )
              }
              Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Go",
                tint = PureWhite.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
              )
            }
          }
        }

        // App Dashboard Section
        DashboardSection(
          totalCount = totalCount,
          completedCount = completedCount,
          overdueCount = overdueCount,
          dueTodayCount = dueTodayCount,
          currentFilter = filter,
          onFilterClicked = { clickedFilter ->
            activeFilterPage = clickedFilter
            viewModel.setFilter(clickedFilter)
          }
        )

        // Filter and Sorting sections
        FilterAndSortHeader(
          currentFilter = filter,
          onFilterSelected = { viewModel.setFilter(it) },
          currentSort = sortBy,
          onSortSelected = { viewModel.setSortBy(it) }
        )

        // Horizontal Scrollable Category Filter section
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          val categories = listOf("All", "Job Application", "Movies", "Shopping", "Health", "Other")
          categories.forEach { cat ->
            val isSelected = categoryFilter.trim().lowercase() == cat.trim().lowercase()
            val color = if (cat == "All") AccordPurple else getCategoryColor(cat)
            
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) color.copy(alpha = 0.25f) else CardBackground)
                .border(
                  width = 1.dp,
                  color = if (isSelected) color else Color.Gray.copy(alpha = 0.2f),
                  shape = RoundedCornerShape(12.dp)
                )
                .clickable { viewModel.setCategoryFilter(cat) }
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("category_filter_${cat.lowercase().replace(" ", "_")}"),
              contentAlignment = Alignment.Center
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                if (cat != "All") {
                  Icon(
                    imageVector = getCategoryIcon(cat),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(12.dp)
                  )
                }
                Text(
                  text = cat,
                  color = if (isSelected) PureWhite else MutedText,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Title of the current view / Page title or header of the list
        val viewTitle = when (filter) {
          TaskFilter.ALL -> "All Tasks"
          TaskFilter.ACTIVE -> "Active Tasks"
          TaskFilter.COMPLETED -> "Completed Tasks"
          TaskFilter.OVERDUE -> "Overdue Tasks"
          TaskFilter.DUE_TODAY -> "Today"
        }
        Text(
          text = viewTitle,
          color = PureWhite,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).testTag("list_section_title")
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (filter == TaskFilter.DUE_TODAY) {
          // Show today's tasks grouped into two sections
          val remainingTasks = tasks.filter { !it.isCompleted }.sortedWith(compareBy<Task> {
            when (it.priority.uppercase()) {
              "HIGH" -> 0
              "MEDIUM" -> 1
              "LOW" -> 2
              else -> 3
            }
          }.thenBy { it.dueDate })
          
          val doneTasks = tasks.filter { it.isCompleted }

          if (remainingTasks.isEmpty() && doneTasks.isEmpty()) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
              contentAlignment = Alignment.Center
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = "No Tasks Found",
                  tint = AccordPurple,
                  modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                  text = "Nothing due today — enjoy your day or add a new task!",
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  color = PureWhite,
                  textAlign = TextAlign.Center,
                  modifier = Modifier.fillMaxWidth().testTag("today_empty_message")
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                  onClick = { isAddTaskOpen = true },
                  colors = ButtonDefaults.buttonColors(containerColor = AccordPurple),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.testTag("today_empty_add_button")
                ) {
                  Text("Add a Task", color = PureWhite)
                }
              }
            }
          } else {
            LazyColumn(
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("tasks_list"),
              verticalArrangement = Arrangement.spacedBy(10.dp),
              contentPadding = PaddingValues(bottom = 80.dp)
            ) {
              if (remainingTasks.isNotEmpty()) {
                item {
                  Text(
                    text = "Remaining",
                    color = AccordPurple,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp).testTag("remaining_section_header")
                  )
                }
                items(remainingTasks, key = { "remaining_${it.id}" }) { task ->
                  TaskCard(
                    task = task,
                    onToggleComplete = { viewModel.toggleTaskCompletion(task) },
                    onDelete = { taskToDelete = task },
                    onEditClick = { editingTask = task },
                    onCardClick = { activeDetailTask = task },
                    isSyncing = isSyncing
                  )
                }
              }

              if (doneTasks.isNotEmpty()) {
                item {
                  Text(
                    text = "Done Today",
                    color = MutedText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp).testTag("done_section_header")
                  )
                }
                items(doneTasks, key = { "done_${it.id}" }) { task ->
                  TaskCard(
                    task = task,
                    onToggleComplete = { viewModel.toggleTaskCompletion(task) },
                    onDelete = { taskToDelete = task },
                    onEditClick = { editingTask = task },
                    onCardClick = { activeDetailTask = task },
                    isSyncing = isSyncing
                  )
                }
              }
            }
          }
        } else {
          // Standard empty states vs Task List for other filters
          if (tasks.isEmpty()) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
              contentAlignment = Alignment.Center
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
              ) {
                Icon(
                  imageVector = if (filter == TaskFilter.COMPLETED) Icons.Default.FactCheck else Icons.Outlined.Assignment,
                  contentDescription = "No Tasks Found",
                  tint = MutedText.copy(alpha = 0.4f),
                  modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                  text = when (filter) {
                    TaskFilter.ALL -> "No tasks listed"
                    TaskFilter.ACTIVE -> "No active tasks remaining!"
                    TaskFilter.COMPLETED -> "No completed tasks yet"
                    TaskFilter.OVERDUE -> "No overdue tasks!"
                    else -> "No tasks found"
                  },
                  fontWeight = FontWeight.Bold,
                  fontSize = 16.sp,
                  color = PureWhite
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = when (filter) {
                    TaskFilter.ALL -> "Tap the floating Purple + icon below to create your very first task card."
                    TaskFilter.ACTIVE -> "Everything is complete! Kick back or add another chore."
                    TaskFilter.COMPLETED -> "Tasks you check off will display in this workspace."
                    TaskFilter.OVERDUE -> "Great job keeping up! All pending chores are on schedule."
                    else -> "No deadlines. Take it easy or prepare ahead!"
                  },
                  color = MutedText,
                  fontSize = 13.sp,
                  modifier = Modifier.widthIn(max = 260.dp),
                  lineHeight = 18.sp,
                  textAlign = TextAlign.Center
                )
              }
            }
          } else {
            LazyColumn(
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("tasks_list"),
              verticalArrangement = Arrangement.spacedBy(10.dp),
              contentPadding = PaddingValues(bottom = 80.dp)
            ) {
              items(tasks, key = { it.id }) { task ->
                TaskCard(
                  task = task,
                  onToggleComplete = { viewModel.toggleTaskCompletion(task) },
                  onDelete = { taskToDelete = task },
                  onEditClick = { editingTask = task },
                  onCardClick = { activeDetailTask = task },
                  isSyncing = isSyncing
                )
              }
            }
          }
        }
      } else {
        // --- SEPARATE SUBPAGE LAYOUT ---
        Spacer(modifier = Modifier.height(8.dp))

        // Sort Header Options
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.FilterList,
              contentDescription = "Sort Icon",
              tint = MutedText,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "Sort by:",
              color = MutedText,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium
            )
          }

          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            TaskSortBy.values().forEach { sortVal ->
              val isSelected = sortBy == sortVal
              val label = when (sortVal) {
                TaskSortBy.DUE_DATE -> "Due Date"
                TaskSortBy.PRIORITY -> "Priority"
              }

              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(20.dp))
                  .background(if (isSelected) AccordPurple.copy(alpha = 0.2f) else Color.Transparent)
                  .border(
                    width = 1.dp,
                    color = if (isSelected) AccordPurple else Color.Gray.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(20.dp)
                  )
                  .clickable { viewModel.setSortBy(sortVal) }
                  .padding(horizontal = 12.dp, vertical = 4.dp)
                  .testTag("subpage_sort_${label.replace(" ", "_").lowercase()}_button"),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = label,
                  color = if (isSelected) AccordPurple else MutedText,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  fontSize = 12.sp
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Category selections row
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          val categories = listOf("All", "Job Application", "Movies", "Shopping", "Health", "Other")
          categories.forEach { cat ->
            val isSelected = categoryFilter.trim().lowercase() == cat.trim().lowercase()
            val color = if (cat == "All") AccordPurple else getCategoryColor(cat)
            
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) color.copy(alpha = 0.25f) else CardBackground)
                .border(
                  width = 1.dp,
                  color = if (isSelected) color else Color.Gray.copy(alpha = 0.2f),
                  shape = RoundedCornerShape(12.dp)
                )
                .clickable { viewModel.setCategoryFilter(cat) }
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("subpage_category_filter_${cat.lowercase().replace(" ", "_")}"),
              contentAlignment = Alignment.Center
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                if (cat != "All") {
                  Icon(
                    imageVector = getCategoryIcon(cat),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(12.dp)
                  )
                }
                Text(
                  text = cat,
                  color = if (isSelected) PureWhite else MutedText,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Standalone Task lists or corresponding empty states
        if (tasks.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier.padding(24.dp)
            ) {
              Icon(
                imageVector = if (activeFilterPage == TaskFilter.COMPLETED) Icons.Default.FactCheck else Icons.Outlined.Assignment,
                contentDescription = "No Tasks Found",
                tint = MutedText.copy(alpha = 0.4f),
                modifier = Modifier.size(72.dp)
              )
              Spacer(modifier = Modifier.height(16.dp))
              Text(
                text = when (activeFilterPage) {
                  TaskFilter.ALL -> "No tasks listed"
                  TaskFilter.COMPLETED -> "No completed tasks yet"
                  TaskFilter.OVERDUE -> "No overdue tasks!"
                  TaskFilter.DUE_TODAY -> "No tasks due today"
                  else -> "No tasks found"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = PureWhite
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = when (activeFilterPage) {
                  TaskFilter.ALL -> "Tap the floating Purple + icon below to create your very first task card."
                  TaskFilter.COMPLETED -> "Tasks you check off will display in this workspace."
                  TaskFilter.OVERDUE -> "Great job keeping up! All pending chores are on schedule."
                  TaskFilter.DUE_TODAY -> "No deadlines for today. Take it easy or prepare ahead!"
                  else -> "Try choosing another category filter."
                },
                color = MutedText,
                fontSize = 13.sp,
                modifier = Modifier.widthIn(max = 260.dp),
                lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
              )
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f)
              .testTag("subpage_tasks_list"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
          ) {
            items(tasks, key = { it.id }) { task ->
              TaskCard(
                task = task,
                onToggleComplete = { viewModel.toggleTaskCompletion(task) },
                onDelete = { taskToDelete = task },
                onEditClick = { editingTask = task },
                onCardClick = { activeDetailTask = task },
                isSyncing = isSyncing
              )
            }
          }
        }
      }
    }
  }

  // Modals / Dialogs Space
  if (isSettingsOpen) {
    SupabaseSettingsDialog(
      viewModel = viewModel,
      initialUrl = viewModel.supabaseUrl.value,
      initialKey = viewModel.supabaseKey.value,
      isConfigured = isConfigured,
      isSyncing = isSyncing,
      onSync = { viewModel.syncTasks() },
      onDismiss = { isSettingsOpen = false },
      onSave = { url, key ->
        viewModel.updateCredentials(url, key)
        isSettingsOpen = false
      },
      onClear = {
        viewModel.resetCredentials()
        isSettingsOpen = false
      },
      onLogout = {
        showLogoutConfirmation = true
      }
    )
  }

  if (showLogoutConfirmation) {
    AlertDialog(
      onDismissRequest = { showLogoutConfirmation = false },
      title = {
        Text(
          text = "Confirm Log Out",
          color = PureWhite,
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp
        )
      },
      text = {
        Text(
          text = "Are you sure want to logout?",
          color = MutedText,
          fontSize = 14.sp
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showLogoutConfirmation = false
            isSettingsOpen = false
            viewModel.logout()
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
          modifier = Modifier.testTag("confirm_logout_button")
        ) {
          Text("Logout", color = PureWhite)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showLogoutConfirmation = false },
          modifier = Modifier.testTag("cancel_logout_button")
        ) {
          Text("Cancel", color = AccordPurple)
        }
      },
      containerColor = DarkBackground,
      shape = RoundedCornerShape(16.dp)
    )
  }

  if (isAddTaskOpen) {
    AddTaskDialog(
      onDismiss = { isAddTaskOpen = false },
      onAdd = { title, desc, url, dateStr, priority, reminderMins, category ->
        viewModel.addTask(title, desc, url, dateStr, priority, reminderMins, category)
        isAddTaskOpen = false
      }
    )
  }

  if (editingTask != null) {
    EditTaskDialog(
      task = editingTask!!,
      onDismiss = { editingTask = null },
      onSave = { updatedTask ->
        viewModel.updateTask(updatedTask)
        editingTask = null
      }
    )
  }

  if (activeDetailTask != null) {
    TaskDetailDialog(
      task = activeDetailTask!!,
      onDismiss = { activeDetailTask = null },
      onEditClick = {
        editingTask = activeDetailTask
        activeDetailTask = null
      }
    )
  }

  if (taskToDelete != null) {
    AlertDialog(
      onDismissRequest = { taskToDelete = null },
      title = {
        Text(
          text = "Delete Task?",
          color = PureWhite,
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp
        )
      },
      text = {
        Text(
          text = "Are you sure you want to delete this task? This will remove it permanently from both your device and cloud database.",
          color = MutedText,
          fontSize = 14.sp
        )
      },
      confirmButton = {
        Button(
          onClick = {
            taskToDelete?.let {
              viewModel.deleteTask(it)
            }
            taskToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
          modifier = Modifier.testTag("confirm_delete_button")
        ) {
          Text("Delete", color = PureWhite, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        OutlinedButton(
          onClick = { taskToDelete = null },
          colors = ButtonDefaults.outlinedButtonColors(contentColor = MutedText),
          border = BorderStroke(1.dp, AccordPurple.copy(alpha = 0.4f)),
          modifier = Modifier.testTag("cancel_delete_button")
        ) {
          Text("Cancel", color = MutedText)
        }
      },
      containerColor = DarkBackground,
      shape = RoundedCornerShape(20.dp)
    )
  }
  }
}

@Composable
fun FilterAndSortHeader(
  currentFilter: TaskFilter,
  onFilterSelected: (TaskFilter) -> Unit,
  currentSort: TaskSortBy,
  onSortSelected: (TaskSortBy) -> Unit
) {
  Column(
    modifier = Modifier.fillMaxWidth()
  ) {
    // Row of filter choices
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(androidx.compose.foundation.rememberScrollState())
        .padding(vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      TaskFilter.values().forEach { filterVal ->
        val isSelected = currentFilter == filterVal
        val label = when (filterVal) {
          TaskFilter.ALL -> "All"
          TaskFilter.ACTIVE -> "Active"
          TaskFilter.COMPLETED -> "Completed"
          TaskFilter.OVERDUE -> "Overdue"
          TaskFilter.DUE_TODAY -> "Due Today"
        }

        Box(
          modifier = Modifier
            .clip(CircleShape)
            .background(if (isSelected) AccordPurple else CardBackground)
            .border(
              width = 1.dp,
              color = if (isSelected) AccordPurple else Color(0xFF334155),
              shape = CircleShape
            )
            .clickable { onFilterSelected(filterVal) }
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("filter_${label.lowercase().replace(" ", "_")}_button"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = label,
            color = if (isSelected) Color(0xFF381E72) else MutedText,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp
          )
        }
      }
    }

    // Row of Sort configuration chips
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Icon(
          imageVector = Icons.Default.FilterList,
          contentDescription = "Sort Icon",
          tint = MutedText,
          modifier = Modifier.size(16.dp)
        )
        Text(
          text = "Sort tasks by:",
          color = MutedText,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium
        )
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        TaskSortBy.values().forEach { sortVal ->
          val isSelected = currentSort == sortVal
          val label = when (sortVal) {
            TaskSortBy.DUE_DATE -> "Due Date"
            TaskSortBy.PRIORITY -> "Priority"
          }

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(20.dp))
              .background(if (isSelected) AccordPurple.copy(alpha = 0.2f) else Color.Transparent)
              .border(
                width = 1.dp,
                color = if (isSelected) AccordPurple else Color.Gray.copy(alpha = 0.4f),
                shape = RoundedCornerShape(20.dp)
              )
              .clickable { onSortSelected(sortVal) }
              .padding(horizontal = 12.dp, vertical = 4.dp)
              .testTag("sort_${label.replace(" ", "_").lowercase()}_button"),
            contentAlignment = Alignment.Center
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              if (isSelected) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = "Selected",
                  tint = AccordPurple,
                  modifier = Modifier.size(10.dp)
                )
              }
              Text(
                text = label,
                color = if (isSelected) PureWhite else MutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun TaskCard(
  task: Task,
  onToggleComplete: () -> Unit,
  onDelete: () -> Unit,
  onEditClick: () -> Unit,
  onCardClick: () -> Unit,
  isSyncing: Boolean = false
) {
  val context = LocalContext.current

  // Card background depends on priority and complete status
  val priorityColor = when (task.priority.uppercase()) {
    "HIGH" -> HighPriorityColor
    "MEDIUM" -> MediumPriorityColor
    "LOW" -> LowPriorityColor
    else -> LowPriorityColor
  }

  val isHighPriority = task.priority.uppercase() == "HIGH"
  val isOverdue = !task.isCompleted && isTaskOverdue(task)

  Card(
    shape = RoundedCornerShape(24.dp),
    colors = CardDefaults.cardColors(containerColor = CardBackground),
    border = BorderStroke(
      width = 1.dp,
      color = if (isOverdue) Color(0xFFEF4444) else if (task.isCompleted) Color(0xFF1E293B).copy(alpha = 0.4f) else Color(0xFF334155)
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("task_card_${task.id}")
      .clip(RoundedCornerShape(24.dp))
      .clickable { onCardClick() }
      .alpha(if (task.isCompleted) 0.6f else 1.0f)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(IntrinsicSize.Max)
    ) {
      if (isHighPriority && !task.isCompleted) {
        Box(
          modifier = Modifier
            .width(4.dp)
            .fillMaxHeight()
            .background(AccordPurple)
        )
      }
      Column(
        modifier = Modifier
          .weight(1f)
          .padding(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 16.dp
          )
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top
        ) {
          // Left portion: Title, Date, Label
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            // Standard customized Custom Checkbox with purple outline / check mark
            IconButton(
              onClick = onToggleComplete,
              modifier = Modifier
                .size(24.dp)
                .testTag("task_checkbox_${task.id}")
            ) {
              Icon(
                imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (task.isCompleted) "Mark Active" else "Mark Complete",
                tint = if (task.isCompleted) AccordPurple else priorityColor,
                modifier = Modifier.size(22.dp)
              )
            }

            Column(
              modifier = Modifier.weight(1f)
            ) {
              // Task Title
              Text(
                text = task.title,
                color = if (task.isCompleted) PureWhite.copy(alpha = 0.5f) else PureWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
              )

              // Category Badge Tag
              val catColor = getCategoryColor(task.category)
              Box(
                modifier = Modifier
                  .padding(top = 5.dp)
                  .clip(RoundedCornerShape(6.dp))
                  .background(catColor.copy(alpha = 0.15f))
                  .border(width = 0.5.dp, color = catColor.copy(alpha = 0.4f), shape = RoundedCornerShape(6.dp))
                  .padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Icon(
                    imageVector = getCategoryIcon(task.category),
                    contentDescription = null,
                    tint = catColor,
                    modifier = Modifier.size(10.dp)
                  )
                  Text(
                    text = task.category,
                    color = catColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
              }

              // Optional description
              if (!task.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = task.description,
                  color = if (task.isCompleted) MutedText.copy(alpha = 0.4f) else MutedText,
                  fontSize = 13.sp,
                  maxLines = 3,
                  overflow = TextOverflow.Ellipsis
                )
              }

              // Clickable URL if present - styled as the Sleek Link block inside the card
              if (!task.url.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF25232A))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                    .clickable {
                      val rawUrl = task.url.trim()
                      val fullUrl = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                        rawUrl
                      } else {
                        "https://$rawUrl"
                      }
                      try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl))
                        context.startActivity(browserIntent)
                      } catch (e: Exception) {
                        Toast
                          .makeText(context, "Invalid link or no browser found", Toast.LENGTH_SHORT)
                          .show()
                      }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = "Attachment",
                    tint = AccordPurple,
                    modifier = Modifier.size(14.dp)
                  )
                  Text(
                    text = task.url,
                    color = AccordPurple,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }
          }

          // Right side: priority label indicator & action buttons
          Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              // Edit task
              IconButton(
                onClick = onEditClick,
                modifier = Modifier
                  .size(28.dp)
                  .testTag("edit_task_${task.id}")
              ) {
                Icon(
                  imageVector = Icons.Default.Edit,
                  contentDescription = "Edit Task",
                  tint = Color.Gray.copy(alpha = 0.7f),
                  modifier = Modifier.size(18.dp)
                )
              }

              // Delete task
              IconButton(
                onClick = onDelete,
                modifier = Modifier
                  .size(28.dp)
                  .testTag("delete_task_${task.id}")
              ) {
                Icon(
                  imageVector = Icons.Default.DeleteOutline,
                  contentDescription = "Delete Task",
                  tint = Color.Red.copy(alpha = 0.7f),
                  modifier = Modifier.size(18.dp)
                )
              }
            }

            // Sleek Theme Priority badge style
            val isHigh = task.priority.uppercase() == "HIGH"
            val isMed = task.priority.uppercase() == "MEDIUM"
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isHigh) (if (AppThemeState.isDark) Color(0xFF381E72) else Color(0xFFEADDFF)) else (if (AppThemeState.isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)))
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(
                text = if (isHigh) "High" else if (isMed) "Med" else "Low",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isHigh) AccordPurple else MutedText
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        HorizontalDivider(color = PureWhite.copy(alpha = 0.1f), thickness = 1.dp)

        Spacer(modifier = Modifier.height(8.dp))

        // Bottom footer: Synchronized status and date
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            // Due Date
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Event,
                contentDescription = "Due Date",
                tint = if (isOverdue) Color(0xFFEF4444) else AccordPurple,
                modifier = Modifier.size(14.dp)
              )
              Text(
                text = task.dueDate,
                color = if (isOverdue) Color(0xFFEF4444) else MutedText,
                fontSize = 11.sp,
                fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Medium
              )
              if (isOverdue) {
                Icon(
                  imageVector = Icons.Default.ErrorOutline,
                  contentDescription = "Overdue Warning",
                  tint = Color(0xFFEF4444),
                  modifier = Modifier.size(12.dp)
                )
              }
            }

            // Reminders Bell Icon Indicator
            if (task.reminderMinutes != -1) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(AccordPurple.copy(alpha = 0.15f))
                  .padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.NotificationsActive,
                  contentDescription = "Reminder set",
                  tint = AccordPurple,
                  modifier = Modifier.size(11.dp)
                )
                val reminderText = when (task.reminderMinutes) {
                  0 -> "At due time"
                  30 -> "30m before"
                  60 -> "1h before"
                  1440 -> "1d before"
                  else -> "${task.reminderMinutes}m before"
                }
                Text(
                  text = reminderText,
                  color = AccordPurple,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }

          // Sync cloud status indicator
          Box(
            modifier = Modifier.size(16.dp),
            contentAlignment = Alignment.Center
          ) {
            if (task.isSynced) {
              Icon(
                imageVector = Icons.Default.CloudDone,
                contentDescription = "Synced with backend",
                tint = Color(0xFF10B981),
                modifier = Modifier.size(16.dp)
              )
            } else if (isSyncing) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(16.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.CloudQueue,
                  contentDescription = "Syncing in progress",
                  tint = AccordPurple,
                  modifier = Modifier.size(16.dp)
                )
                CircularProgressIndicator(
                  color = AccordPurple,
                  strokeWidth = 1.dp,
                  modifier = Modifier.size(8.dp)
                )
              }
            } else {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(16.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.CloudQueue,
                  contentDescription = "Sync failed / Local only",
                  tint = Color(0xFFEF4444),
                  modifier = Modifier.size(16.dp)
                )
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = null,
                  tint = Color(0xFFEF4444),
                  modifier = Modifier.size(7.dp)
                )
              }
            }
          }
        }
      }
    }
  }
}

// Dialog: Set Supabase values with schema and manual copy-paste
@Composable
fun SupabaseSettingsDialog(
  viewModel: TaskViewModel,
  initialUrl: String,
  initialKey: String,
  isConfigured: Boolean,
  isSyncing: Boolean,
  onSync: () -> Unit,
  onDismiss: () -> Unit,
  onSave: (String, String) -> Unit,
  onClear: () -> Unit,
  onLogout: () -> Unit
) {
  var url by remember { mutableStateOf(initialUrl) }
  var key by remember { mutableStateOf(initialKey) }
  var isAdvancedUnlocked by remember { mutableStateOf(false) }
  var isKeyVisible by remember { mutableStateOf(false) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Card(
      colors = CardDefaults.cardColors(containerColor = DarkBackground),
      shape = RoundedCornerShape(20.dp),
      border = BorderStroke(1.dp, AccordPurple.copy(alpha = 0.4f)),
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .padding(16.dp)
        .testTag("supabase_settings_dialog")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp)
          .verticalScroll(rememberScrollState())
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Settings",
            color = PureWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
          )
          IconButton(onClick = onDismiss) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = PureWhite)
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Cloud Status Button (Interactive)
        Card(
          colors = CardDefaults.cardColors(
            containerColor = AccordPurple.copy(alpha = 0.12f)
          ),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, AccordPurple.copy(alpha = 0.35f)),
          modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isSyncing) {
              if (isConfigured) {
                onSync()
              }
            }
            .testTag("sync_tasks_button_dialog")
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            if (isSyncing) {
              CircularProgressIndicator(
                color = AccordPurple,
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp
              )
            } else {
              Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = "Cloud Status Sync Icon",
                tint = if (isConfigured) AccordPurple else Color.Gray,
                modifier = Modifier.size(22.dp)
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Cloud Status",
                color = PureWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
              )
              Text(
                text = if (isSyncing) "Syncing database with Cloud..." else if (isConfigured) "Connected & Secure. Tap here to manual sync now." else "Supabase not connected. Tab advanced settings to link custom backend.",
                color = MutedText,
                fontSize = 11.sp,
                lineHeight = 15.sp
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (viewModel.isUserLoggedIn.value) {
          val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
          val userDisplayName by viewModel.userDisplayName.collectAsStateWithLifecycle()
          val profileUpdateSuccess by viewModel.profileUpdateSuccess.collectAsStateWithLifecycle()
          var displayNameInput by remember { mutableStateOf(userDisplayName ?: "") }

          LaunchedEffect(userDisplayName) {
            if (userDisplayName != null && displayNameInput != userDisplayName) {
              displayNameInput = userDisplayName!!
            }
          }

          Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AccordPurple.copy(alpha = 0.2f)),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("profile_section")
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Person,
                  contentDescription = "Profile Settings",
                  tint = AccordPurple,
                  modifier = Modifier.size(20.dp)
                )
                Text(
                  text = "Profile Details",
                  color = PureWhite,
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp
                )
              }

              OutlinedTextField(
                value = displayNameInput,
                onValueChange = { displayNameInput = it },
                label = { Text("Display Name", color = MutedText, fontSize = 11.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                  focusedTextColor = PureWhite,
                  unfocusedTextColor = PureWhite,
                  focusedBorderColor = AccordPurple,
                  unfocusedBorderColor = AccordPurple.copy(alpha = 0.3f)
                ),
                singleLine = true,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("settings_display_name_input")
              )

              OutlinedTextField(
                value = userEmail ?: "",
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text("Email Address", color = MutedText.copy(alpha = 0.5f), fontSize = 11.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                  focusedTextColor = MutedText,
                  unfocusedTextColor = MutedText,
                  disabledTextColor = MutedText,
                  disabledBorderColor = AccordPurple.copy(alpha = 0.15f),
                  unfocusedBorderColor = AccordPurple.copy(alpha = 0.15f)
                ),
                singleLine = true,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("settings_email_readonly")
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                if (profileUpdateSuccess) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.CheckCircle,
                      contentDescription = "Saved icon",
                      tint = Color(0xFF10B981),
                      modifier = Modifier.size(16.dp)
                    )
                    Text(
                      text = "Saved!",
                      color = Color(0xFF10B981),
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold
                    )
                  }
                } else {
                  Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                  onClick = {
                    viewModel.updateProfileDisplayName(displayNameInput)
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = AccordPurple),
                  shape = RoundedCornerShape(8.dp),
                  contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                  modifier = Modifier
                    .wrapContentSize()
                    .testTag("save_profile_button"),
                  enabled = displayNameInput.isNotBlank()
                ) {
                  Text("Save Change", color = PureWhite, fontSize = 12.sp)
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(12.dp))
        }

        // Privacy & Security Card
        Card(
          colors = CardDefaults.cardColors(containerColor = CardBackground.copy(alpha = 0.5f)),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, AccordPurple.copy(alpha = 0.2f)),
          modifier = Modifier.fillMaxWidth().testTag("privacy_security_section")
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Security,
              contentDescription = "Privacy & Security",
              tint = AccordPurple,
              modifier = Modifier.size(24.dp).padding(top = 2.dp)
            )
            Column {
              Text(
                text = "Privacy & Security",
                color = PureWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "All of your tasks are private to your account, protected and enforced by Supabase Row Level Security (RLS) policies at the database level.",
                color = MutedText,
                fontSize = 11.sp,
                lineHeight = 15.sp
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Even with direct database access, no other user can read, edit, or delete your tasks. The RLS security policies automatically verify your authenticated user identity on every request to ensure your data stays exclusively yours.",
                color = MutedText,
                fontSize = 11.sp,
                lineHeight = 15.sp
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dedicated Log Out Button Section
        Button(
          onClick = onLogout,
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFEF4444)
          ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("dialog_logout_button")
        ) {
          Icon(
            imageVector = Icons.Default.Logout,
            contentDescription = "Logout",
            tint = PureWhite,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Log Out Account",
            color = PureWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Developer Mode Row Toggle
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardBackground.copy(alpha = 0.3f))
            .clickable { isAdvancedUnlocked = !isAdvancedUnlocked }
            .padding(12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Custom Sync Settings (Advanced)",
              color = PureWhite,
              fontWeight = FontWeight.SemiBold,
              fontSize = 13.sp
            )
            Text(
              text = "Link your own custom Supabase database backend.",
              color = MutedText,
              fontSize = 11.sp
            )
          }
          Switch(
            checked = isAdvancedUnlocked,
            onCheckedChange = { isAdvancedUnlocked = it },
            colors = SwitchDefaults.colors(
              checkedThumbColor = AccordPurple,
              checkedTrackColor = AccordPurple.copy(alpha = 0.4f),
              uncheckedThumbColor = Color.Gray,
              uncheckedTrackColor = Color.Gray.copy(alpha = 0.2f)
            )
          )
        }

        if (isAdvancedUnlocked) {
          Spacer(modifier = Modifier.height(16.dp))

          OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Supabase URL", color = MutedText) },
            placeholder = { Text("https://xxx.supabase.co", color = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = PureWhite,
              unfocusedTextColor = PureWhite,
              focusedBorderColor = AccordPurple,
              unfocusedBorderColor = AccordPurple.copy(alpha = 0.3f)
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("supabase_url_input"),
            singleLine = true
          )

          Spacer(modifier = Modifier.height(10.dp))

          OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("Anon Api Key / TOKEN", color = MutedText) },
            placeholder = { Text("eyJhbG...", color = Color.Gray) },
            visualTransformation = if (isKeyVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            trailingIcon = {
              IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                Icon(
                  imageVector = if (isKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                  contentDescription = if (isKeyVisible) "Hide Key" else "Show Key",
                  tint = MutedText
                )
              }
            },
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = PureWhite,
              unfocusedTextColor = PureWhite,
              focusedBorderColor = AccordPurple,
              unfocusedBorderColor = AccordPurple.copy(alpha = 0.3f)
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("supabase_key_input"),
            singleLine = false,
            maxLines = 3
          )

          Spacer(modifier = Modifier.height(16.dp))

          // SQL table instruction card
          Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(
                text = "Required Table Schema SQL & RLS Policies:",
                color = AccordPurple,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "create table tasks (\n" +
                  "  id bigint primary key,\n" +
                  "  title text not null,\n" +
                  "  description text,\n" +
                  "  url text,\n" +
                  "  due_date text not null,\n" +
                  "  priority text not null,\n" +
                  "  is_completed boolean not null default false,\n" +
                  "  created_at timestamp with time zone default now(),\n" +
                  "  user_id uuid references auth.users not null default auth.uid()\n" +
                  ");\n\n" +
                  "-- Enable RLS\n" +
                  "alter table tasks enable row level security;\n\n" +
                  "-- User Access Policy\n" +
                  "create policy \"Users manage own tasks\"\n" +
                  "  on tasks for all\n" +
                  "  using (auth.uid() = user_id)\n" +
                  "  with check (auth.uid() = user_id);",
                color = PureWhite.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp
              )
            }
          }

          Spacer(modifier = Modifier.height(20.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedButton(
              onClick = onClear,
              colors = ButtonDefaults.outlinedButtonColors(contentColor = AccordPurple),
              border = BorderStroke(1.dp, AccordPurple.copy(alpha = 0.4f)),
              modifier = Modifier
                .weight(1f)
                .testTag("clear_settings_button")
            ) {
              Text("Disconnect", fontSize = 13.sp)
            }

            Button(
              onClick = { onSave(url.trim(), key.trim()) },
              colors = ButtonDefaults.buttonColors(containerColor = AccordPurple),
              modifier = Modifier
                .weight(1f)
                .testTag("apply_settings_button"),
              enabled = url.isNotBlank() && key.isNotBlank()
            ) {
              Text("Apply Connect", color = PureWhite, fontSize = 13.sp)
            }
          }
        }
      }
    }
  }
}

@Composable
fun DashboardSection(
  totalCount: Int,
  completedCount: Int,
  overdueCount: Int,
  dueTodayCount: Int,
  currentFilter: TaskFilter,
  onFilterClicked: (TaskFilter) -> Unit
) {
  BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
    val isWide = maxWidth >= 600.dp
    
    if (isWide) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        DashboardCard(
          title = "Total Tasks",
          count = totalCount,
          icon = Icons.Default.Assignment,
          color = AccordPurple,
          isSelected = currentFilter == TaskFilter.ALL,
          onClick = { onFilterClicked(TaskFilter.ALL) },
          modifier = Modifier.weight(1f)
        )
        DashboardCard(
          title = "Completed",
          count = completedCount,
          icon = Icons.Default.CheckCircle,
          color = Color(0xFF10B981),
          isSelected = currentFilter == TaskFilter.COMPLETED,
          onClick = { onFilterClicked(TaskFilter.COMPLETED) },
          modifier = Modifier.weight(1f)
        )
        DashboardCard(
          title = "Overdue",
          count = overdueCount,
          icon = Icons.Default.Warning,
          color = Color(0xFFEF4444),
          isSelected = currentFilter == TaskFilter.OVERDUE,
          onClick = { onFilterClicked(TaskFilter.OVERDUE) },
          modifier = Modifier.weight(1f)
        )
        DashboardCard(
          title = "Due Today",
          count = dueTodayCount,
          icon = Icons.Default.Today,
          color = Color(0xFF3B82F6),
          isSelected = currentFilter == TaskFilter.DUE_TODAY,
          onClick = { onFilterClicked(TaskFilter.DUE_TODAY) },
          modifier = Modifier.weight(1f)
        )
      }
    } else {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          DashboardCard(
            title = "Total Tasks",
            count = totalCount,
            icon = Icons.Default.Assignment,
            color = AccordPurple,
            isSelected = currentFilter == TaskFilter.ALL,
            onClick = { onFilterClicked(TaskFilter.ALL) },
            modifier = Modifier.weight(1f)
          )
          DashboardCard(
            title = "Completed",
            count = completedCount,
            icon = Icons.Default.CheckCircle,
            color = Color(0xFF10B981),
            isSelected = currentFilter == TaskFilter.COMPLETED,
            onClick = { onFilterClicked(TaskFilter.COMPLETED) },
            modifier = Modifier.weight(1f)
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          DashboardCard(
            title = "Overdue",
            count = overdueCount,
            icon = Icons.Default.Warning,
            color = Color(0xFFEF4444),
            isSelected = currentFilter == TaskFilter.OVERDUE,
            onClick = { onFilterClicked(TaskFilter.OVERDUE) },
            modifier = Modifier.weight(1f)
          )
          DashboardCard(
            title = "Due Today",
            count = dueTodayCount,
            icon = Icons.Default.Today,
            color = Color(0xFF3B82F6),
            isSelected = currentFilter == TaskFilter.DUE_TODAY,
            onClick = { onFilterClicked(TaskFilter.DUE_TODAY) },
            modifier = Modifier.weight(1f)
          )
        }
      }
    }
  }
}

@Composable
fun DashboardCard(
  title: String,
  count: Int,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  color: Color,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isSelected) color.copy(alpha = 0.12f) else CardBackground
    ),
    border = BorderStroke(
      width = if (isSelected) 2.dp else 1.dp,
      color = if (isSelected) color else color.copy(alpha = 0.25f)
    ),
    modifier = modifier
      .clip(RoundedCornerShape(16.dp))
      .clickable { onClick() }
      .testTag("dashboard_card_${title.lowercase().replace(" ", "_")}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = color,
          modifier = Modifier.size(18.dp)
        )
      }
      Column {
        Text(
          text = count.toString(),
          color = PureWhite,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.testTag("dashboard_count_${title.lowercase().replace(" ", "_")}")
        )
        Text(
          text = title,
          color = MutedText,
          fontSize = 10.sp,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
fun TaskDetailDialog(
  task: Task,
  onDismiss: () -> Unit,
  onEditClick: () -> Unit
) {
  val context = LocalContext.current
  val isOverdue = !task.isCompleted && isTaskOverdue(task)
  
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Card(
      colors = CardDefaults.cardColors(containerColor = DarkBackground),
      shape = RoundedCornerShape(24.dp),
      border = BorderStroke(1.2.dp, if (isOverdue) Color(0xFFEF4444) else AccordPurple.copy(alpha = 0.4f)),
      modifier = Modifier
        .fillMaxWidth(0.92f)
        .padding(16.dp)
        .testTag("task_detail_dialog")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp)
      ) {
        // Safe Header Space
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Task Details",
            color = MutedText,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 1.sp
          )
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("close_task_detail_btn")
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close details",
              tint = PureWhite
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Screen sizes scroll wrapper
        Column(
          modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
        ) {
          // Large Title
          Text(
            text = task.title,
            color = PureWhite,
            fontWeight = FontWeight.Black,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            modifier = Modifier.testTag("task_detail_title")
          )

          Spacer(modifier = Modifier.height(16.dp))

          // Row containing Badges: Category, Priority, status
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Category Badge
            val catColor = getCategoryColor(task.category)
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(catColor.copy(alpha = 0.15f))
                .border(0.5.dp, catColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("task_detail_category_badge")
            ) {
              Icon(
                imageVector = getCategoryIcon(task.category),
                contentDescription = null,
                tint = catColor,
                modifier = Modifier.size(12.dp)
              )
              Text(
                text = task.category,
                color = catColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }

            // Priority Badge
            val priorityColor = when (task.priority.uppercase()) {
              "HIGH" -> HighPriorityColor
              "MEDIUM" -> MediumPriorityColor
              else -> LowPriorityColor
            }
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(priorityColor.copy(alpha = 0.15f))
                .border(0.5.dp, priorityColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("task_detail_priority_badge")
            ) {
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .clip(CircleShape)
                  .background(priorityColor)
              )
              Text(
                text = task.priority.uppercase(),
                color = priorityColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }
            
            // Completion Status Tag
            val statusColor = if (task.isCompleted) Color(0xFF10B981) else if (isOverdue) Color(0xFFEF4444) else AccordPurple
            val statusText = if (task.isCompleted) "Completed" else if (isOverdue) "Overdue" else "Active"
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(statusColor.copy(alpha = 0.15f))
                .border(0.5.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("task_detail_status_badge")
            ) {
              Icon(
                imageVector = if (task.isCompleted) Icons.Default.CheckCircle else if (isOverdue) Icons.Default.Warning else Icons.Default.HourglassEmpty,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(12.dp)
              )
              Text(
                text = statusText,
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }

          Spacer(modifier = Modifier.height(20.dp))

          // Description Section
          Text(
            text = "DESCRIPTION",
            color = MutedText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
          )
          Spacer(modifier = Modifier.height(4.dp))
          val textDesc = if (!task.description.isNullOrBlank()) task.description else "No description provided."
          Text(
            text = textDesc,
            color = if (!task.description.isNullOrBlank()) PureWhite else MutedText.copy(alpha = 0.7f),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.testTag("task_detail_description")
          )

          Spacer(modifier = Modifier.height(20.dp))

          // Due Date Section
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Event,
              contentDescription = "Due Date",
              tint = if (isOverdue) Color(0xFFEF4444) else AccordPurple,
              modifier = Modifier.size(18.dp)
            )
            Column {
              Text(
                text = "DUE DATE",
                color = MutedText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
              )
              Text(
                text = if (isOverdue) "${task.dueDate} (Overdue)" else task.dueDate,
                color = if (isOverdue) Color(0xFFEF4444) else PureWhite,
                fontSize = 14.sp,
                fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier.testTag("task_detail_due_date")
              )
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Reminder Section
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            val hasReminder = task.reminderMinutes != -1
            Icon(
              imageVector = if (hasReminder) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
              contentDescription = "Reminder",
              tint = if (hasReminder) AccordPurple else MutedText,
              modifier = Modifier.size(18.dp)
            )
            Column {
              Text(
                text = "REMINDER",
                color = MutedText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
              )
              val reminderText = when (task.reminderMinutes) {
                -1 -> "None"
                0 -> "At due time"
                30 -> "30 minutes before"
                60 -> "1 hour before"
                1440 -> "1 day before"
                else -> "${task.reminderMinutes} minutes before"
              }
              Text(
                text = reminderText,
                color = if (hasReminder) PureWhite else MutedText.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("task_detail_reminder")
              )
            }
          }

          // URL Section
          if (!task.url.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
              text = "ATTACHED URL LINK",
              color = MutedText,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
              onClick = {
                try {
                  var formattedUrl = task.url.trim()
                  if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
                    formattedUrl = "https://$formattedUrl"
                  }
                  val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl))
                  context.startActivity(intent)
                } catch (e: Exception) {
                  Toast.makeText(context, "Invalid link or browser not available", Toast.LENGTH_SHORT).show()
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = CardBackground),
              border = BorderStroke(1.dp, AccordPurple.copy(alpha = 0.5f)),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("task_detail_url_button")
            ) {
              Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Launch,
                  contentDescription = "Open Link",
                  tint = AccordPurple,
                  modifier = Modifier.size(16.dp)
                )
                Text(
                  text = task.url,
                  color = AccordPurple,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons Row: Cancel/Back & Edit Task
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          OutlinedButton(
            onClick = onDismiss,
            border = BorderStroke(1.dp, AccordPurple.copy(alpha = 0.4f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MutedText),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("task_detail_back_btn")
          ) {
            Text(
              text = "Back",
              color = MutedText,
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp
            )
          }

          Button(
            onClick = onEditClick,
            colors = ButtonDefaults.buttonColors(containerColor = AccordPurple),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("task_detail_edit_btn")
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = PureWhite,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = "Edit",
                color = PureWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
              )
            }
          }
        }
      }
    }
  }
}

// Dialog: Add Task with fields
@Composable
fun AddTaskDialog(
  onDismiss: () -> Unit,
  onAdd: (title: String, desc: String?, url: String?, dueDate: String, priority: String, reminderMins: Int, category: String) -> Unit
) {
  var title by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var url by remember { mutableStateOf("") }

  // Category selection
  var categoryExpanded by remember { mutableStateOf(false) }
  var categorySelected by remember { mutableStateOf("Other") }

  // Reminder selection
  var reminderExpanded by remember { mutableStateOf(false) }
  var reminderMins by remember { mutableStateOf(-1) }
  val reminderOptions = listOf(
    -1 to "None",
    0 to "At due time",
    30 to "30 minutes before",
    60 to "1 hour before",
    1440 to "1 day before"
  )

  // Date and time defaults
  val calendar = Calendar.getInstance()
  val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
  var dueDateText by remember { mutableStateOf(dateFormatter.format(calendar.time)) }

  var prioritySelected by remember { mutableStateOf("MEDIUM") }

  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current

  Dialog(
    onDismissRequest = {
      keyboardController?.hide()
      focusManager.clearFocus()
      onDismiss()
    },
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Card(
      colors = CardDefaults.cardColors(containerColor = DarkBackground),
      shape = RoundedCornerShape(20.dp),
      border = BorderStroke(1.dp, AccordPurple.copy(alpha = 0.4f)),
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .padding(16.dp)
        .testTag("add_task_dialog")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp)
          .verticalScroll(rememberScrollState())
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Create New Chore Task",
            color = PureWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
          )
          IconButton(onClick = onDismiss) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = PureWhite)
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Title input
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Task Title *", color = MutedText) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = PureWhite,
            unfocusedTextColor = PureWhite,
            focusedBorderColor = AccordPurple,
            unfocusedBorderColor = AccordPurple.copy(alpha = 0.3f)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("task_title_input"),
          singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Description Input
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Description (Optional)", color = MutedText) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = PureWhite,
            unfocusedTextColor = PureWhite,
            focusedBorderColor = AccordPurple,
            unfocusedBorderColor = AccordPurple.copy(alpha = 0.3f)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("task_description_input"),
          maxLines = 2
        )

        Spacer(modifier = Modifier.height(10.dp))

        // URL Input
        OutlinedTextField(
          value = url,
          onValueChange = { url = it },
          label = { Text("Reference Link (Optional URL)", color = MutedText) },
          placeholder = { Text("https://example.com", color = Color.Gray) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = PureWhite,
            unfocusedTextColor = PureWhite,
            focusedBorderColor = AccordPurple,
            unfocusedBorderColor = AccordPurple.copy(alpha = 0.3f)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("task_url_input"),
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Date Picker & Time Picker Actions in one field row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = dueDateText,
            onValueChange = { dueDateText = it },
            label = { Text("Due Date & Time", color = MutedText) },
            readOnly = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = PureWhite,
              unfocusedTextColor = PureWhite,
              focusedBorderColor = AccordPurple,
              unfocusedBorderColor = AccordPurple.copy(alpha = 0.3f)
            ),
            modifier = Modifier
              .weight(1f)
              .testTag("task_datepicker_input"),
            trailingIcon = {
              IconButton(onClick = {
                // Open native systems DatePickerDialog first, followed by TimePickerDialog
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)
                val day = calendar.get(Calendar.DAY_OF_MONTH)

                val datePickerDialog = DatePickerDialog(
                  context,
                  { _, selectedYear, selectedMonth, selectedDay ->
                    calendar.set(Calendar.YEAR, selectedYear)
                    calendar.set(Calendar.MONTH, selectedMonth)
                    calendar.set(Calendar.DAY_OF_MONTH, selectedDay)

                    // Open time picker
                    TimePickerDialog(
                      context,
                      { _, selectedHour, selectedMinute ->
                        calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                        calendar.set(Calendar.MINUTE, selectedMinute)
                        dueDateText = dateFormatter.format(calendar.time)
                      },
                      calendar.get(Calendar.HOUR_OF_DAY),
                      calendar.get(Calendar.MINUTE),
                      true
                    ).show()

                  },
                  year,
                  month,
                  day
                )
                datePickerDialog.show()
              }) {
                Icon(
                  imageVector = Icons.Default.CalendarMonth,
                  contentDescription = "Pick Date and Time",
                  tint = AccordPurple
                )
              }
            }
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Reminder Selection dropdown
        Text(
          text = "Set Reminder Notification:",
          color = MutedText,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(bottom = 6.dp)
        )

        Box(
          modifier = Modifier.fillMaxWidth()
        ) {
          Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AccordPurple.copy(alpha = 0.3f)),
            modifier = Modifier
              .fillMaxWidth()
              .clickable { reminderExpanded = true }
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = if (reminderMins == -1) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                  contentDescription = "ReminderIcon",
                  tint = AccordPurple,
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = reminderOptions.firstOrNull { it.first == reminderMins }?.second ?: "None",
                  color = PureWhite,
                  fontSize = 14.sp
                )
              }
              Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Arrow Drop Down",
                tint = PureWhite
              )
            }
          }

          DropdownMenu(
            expanded = reminderExpanded,
            onDismissRequest = { reminderExpanded = false },
            modifier = Modifier
              .background(CardBackground)
              .border(1.dp, AccordPurple.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
          ) {
            reminderOptions.forEach { option ->
              DropdownMenuItem(
                text = {
                  Text(
                    text = option.second,
                    color = PureWhite,
                    fontSize = 14.sp
                  )
                },
                onClick = {
                  reminderMins = option.first
                  reminderExpanded = false
                },
                leadingIcon = {
                  Icon(
                    imageVector = if (option.first == -1) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = AccordPurple,
                    modifier = Modifier.size(16.dp)
                  )
                }
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Task Category dropdown
        Text(
          text = "Task Category:",
          color = MutedText,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(bottom = 6.dp)
        )

        Box(
          modifier = Modifier.fillMaxWidth()
        ) {
          Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AccordPurple.copy(alpha = 0.3f)),
            modifier = Modifier
              .fillMaxWidth()
              .clickable { categoryExpanded = true }
              .testTag("task_category_dropdown_btn")
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                val catColor = getCategoryColor(categorySelected)
                Icon(
                  imageVector = getCategoryIcon(categorySelected),
                  contentDescription = "CategoryIcon",
                  tint = catColor,
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = categorySelected,
                  color = PureWhite,
                  fontSize = 14.sp
                )
              }
              Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Arrow Drop Down",
                tint = PureWhite
              )
            }
          }

          DropdownMenu(
            expanded = categoryExpanded,
            onDismissRequest = { categoryExpanded = false },
            modifier = Modifier
              .background(CardBackground)
              .border(1.dp, AccordPurple.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
          ) {
            listOf("Job Application", "Movies", "Shopping", "Health", "Other").forEach { option ->
              DropdownMenuItem(
                text = {
                  Text(
                    text = option,
                    color = PureWhite,
                    fontSize = 14.sp
                  )
                },
                onClick = {
                  categorySelected = option
                  categoryExpanded = false
                },
                leadingIcon = {
                  val catColor = getCategoryColor(option)
                  Icon(
                    imageVector = getCategoryIcon(option),
                    contentDescription = null,
                    tint = catColor,
                    modifier = Modifier.size(16.dp)
                  )
                },
                modifier = Modifier.testTag("task_category_option_${option.lowercase().replace(" ", "_")}")
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Priority Level Selection Row
        Text(
          text = "Priority Level:",
          color = MutedText,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          listOf("LOW", "MEDIUM", "HIGH").forEach { level ->
            val isSelected = prioritySelected == level
            val priorityBtnColor = when (level) {
              "HIGH" -> HighPriorityColor
              "MEDIUM" -> MediumPriorityColor
              "LOW" -> LowPriorityColor
              else -> LowPriorityColor
            }

            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSelected) priorityBtnColor.copy(alpha = 0.2f) else CardBackground)
                .border(
                  width = 1.dp,
                  color = if (isSelected) priorityBtnColor else Color.Gray.copy(alpha = 0.2f),
                  shape = RoundedCornerShape(8.dp)
                )
                .clickable { prioritySelected = level }
                .padding(vertical = 10.dp)
                .testTag("priority_selection_$level"),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = level,
                color = if (isSelected) priorityBtnColor else MutedText,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = {
              keyboardController?.hide()
              focusManager.clearFocus()
              onDismiss()
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccordPurple),
            border = BorderStroke(1.dp, AccordPurple.copy(alpha = 0.4f)),
            modifier = Modifier
              .weight(1f)
              .testTag("cancel_task_button")
          ) {
            Text("Cancel", fontSize = 13.sp)
          }

          Button(
            onClick = {
              keyboardController?.hide()
              focusManager.clearFocus()
              onAdd(
                title.trim(),
                description.takeIf { it.isNotBlank() }?.trim(),
                url.takeIf { it.isNotBlank() }?.trim(),
                dueDateText,
                prioritySelected,
                reminderMins,
                categorySelected
              )
            },
            colors = ButtonDefaults.buttonColors(containerColor = AccordPurple),
            modifier = Modifier
              .weight(1f)
              .testTag("create_task_button"),
            enabled = title.isNotBlank()
          ) {
            Text("Create Card", color = PureWhite, fontSize = 13.sp)
          }
        }
      }
    }
  }
}

// Dialog: Edit Task with prepopulated fields
@Composable
fun EditTaskDialog(
  task: Task,
  onDismiss: () -> Unit,
  onSave: (Task) -> Unit
) {
  var title by remember { mutableStateOf(task.title) }
  var description by remember { mutableStateOf(task.description ?: "") }
  var url by remember { mutableStateOf(task.url ?: "") }

  // Category selection
  var categoryExpanded by remember { mutableStateOf(false) }
  var categorySelected by remember { mutableStateOf(task.category) }

  // Reminder selection
  var reminderExpanded by remember { mutableStateOf(false) }
  var reminderMins by remember { mutableStateOf(task.reminderMinutes) }
  val reminderOptions = listOf(
    -1 to "None",
    0 to "At due time",
    30 to "30 minutes before",
    60 to "1 hour before",
    1440 to "1 day before"
  )

  // Date and time defaults
  val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
  val calendar = Calendar.getInstance().apply {
    try {
      val parsedDate = dateFormatter.parse(task.dueDate)
      if (parsedDate != null) {
        time = parsedDate
      }
    } catch (e: Exception) {
      // Fallback
    }
  }
  var dueDateText by remember { mutableStateOf(task.dueDate) }

  var prioritySelected by remember { mutableStateOf(task.priority) }

  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current

  Dialog(
    onDismissRequest = {
      keyboardController?.hide()
      focusManager.clearFocus()
      onDismiss()
    },
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Card(
      colors = CardDefaults.cardColors(containerColor = DarkBackground),
      shape = RoundedCornerShape(20.dp),
      border = BorderStroke(1.dp, AccordPurple.copy(alpha = 0.4f)),
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .padding(16.dp)
        .testTag("edit_task_dialog")
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(20.dp)
          .verticalScroll(rememberScrollState())
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Edit Task Details",
            color = PureWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
          )
          IconButton(onClick = onDismiss) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = PureWhite)
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Title input
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Task Title *", color = MutedText) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = PureWhite,
            unfocusedTextColor = PureWhite,
            focusedBorderColor = AccordPurple,
            unfocusedBorderColor = AccordPurple.copy(alpha = 0.3f)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_task_title_input"),
          singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Description Input
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Description (Optional)", color = MutedText) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = PureWhite,
            unfocusedTextColor = PureWhite,
            focusedBorderColor = AccordPurple,
            unfocusedBorderColor = AccordPurple.copy(alpha = 0.3f)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_task_description_input"),
          maxLines = 2
        )

        Spacer(modifier = Modifier.height(10.dp))

        // URL Input
        OutlinedTextField(
          value = url,
          onValueChange = { url = it },
          label = { Text("Reference Link (Optional URL)", color = MutedText) },
          placeholder = { Text("https://example.com", color = Color.Gray) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = PureWhite,
            unfocusedTextColor = PureWhite,
            focusedBorderColor = AccordPurple,
            unfocusedBorderColor = AccordPurple.copy(alpha = 0.3f)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_task_url_input"),
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Date Picker & Time Picker Actions in one field row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = dueDateText,
            onValueChange = { dueDateText = it },
            label = { Text("Due Date & Time", color = MutedText) },
            readOnly = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = PureWhite,
              unfocusedTextColor = PureWhite,
              focusedBorderColor = AccordPurple,
              unfocusedBorderColor = AccordPurple.copy(alpha = 0.3f)
            ),
            modifier = Modifier
              .weight(1f)
              .testTag("edit_task_datepicker_input"),
            trailingIcon = {
              IconButton(onClick = {
                // Open native systems DatePickerDialog first, followed by TimePickerDialog
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)
                val day = calendar.get(Calendar.DAY_OF_MONTH)

                val datePickerDialog = DatePickerDialog(
                  context,
                  { _, selectedYear, selectedMonth, selectedDay ->
                    calendar.set(Calendar.YEAR, selectedYear)
                    calendar.set(Calendar.MONTH, selectedMonth)
                    calendar.set(Calendar.DAY_OF_MONTH, selectedDay)

                    // Open time picker
                    TimePickerDialog(
                      context,
                      { _, selectedHour, selectedMinute ->
                        calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                        calendar.set(Calendar.MINUTE, selectedMinute)
                        dueDateText = dateFormatter.format(calendar.time)
                      },
                      calendar.get(Calendar.HOUR_OF_DAY),
                      calendar.get(Calendar.MINUTE),
                      true
                    ).show()

                  },
                  year,
                  month,
                  day
                )
                datePickerDialog.show()
              }) {
                Icon(
                  imageVector = Icons.Default.CalendarMonth,
                  contentDescription = "Pick Date and Time",
                  tint = AccordPurple
                )
              }
            }
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Reminder Selection dropdown
        Text(
          text = "Set Reminder Notification:",
          color = MutedText,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(bottom = 6.dp)
        )

        Box(
          modifier = Modifier.fillMaxWidth()
        ) {
          Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AccordPurple.copy(alpha = 0.3f)),
            modifier = Modifier
              .fillMaxWidth()
              .clickable { reminderExpanded = true }
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = if (reminderMins == -1) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                  contentDescription = "ReminderIcon",
                  tint = AccordPurple,
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = reminderOptions.firstOrNull { it.first == reminderMins }?.second ?: "None",
                  color = PureWhite,
                  fontSize = 14.sp
                )
              }
              Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Arrow Drop Down",
                tint = PureWhite
              )
            }
          }

          DropdownMenu(
            expanded = reminderExpanded,
            onDismissRequest = { reminderExpanded = false },
            modifier = Modifier
              .background(CardBackground)
              .border(1.dp, AccordPurple.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
          ) {
            reminderOptions.forEach { option ->
              DropdownMenuItem(
                text = {
                  Text(
                    text = option.second,
                    color = PureWhite,
                    fontSize = 14.sp
                  )
                },
                onClick = {
                  reminderMins = option.first
                  reminderExpanded = false
                },
                leadingIcon = {
                  Icon(
                    imageVector = if (option.first == -1) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = AccordPurple,
                    modifier = Modifier.size(16.dp)
                  )
                }
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Task Category dropdown
        Text(
          text = "Task Category:",
          color = MutedText,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(bottom = 6.dp)
        )

        Box(
          modifier = Modifier.fillMaxWidth()
        ) {
          Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AccordPurple.copy(alpha = 0.3f)),
            modifier = Modifier
              .fillMaxWidth()
              .clickable { categoryExpanded = true }
              .testTag("edit_task_category_dropdown_btn")
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                val catColor = getCategoryColor(categorySelected)
                Icon(
                  imageVector = getCategoryIcon(categorySelected),
                  contentDescription = "CategoryIcon",
                  tint = catColor,
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = categorySelected,
                  color = PureWhite,
                  fontSize = 14.sp
                )
              }
              Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Arrow Drop Down",
                tint = PureWhite
              )
            }
          }

          DropdownMenu(
            expanded = categoryExpanded,
            onDismissRequest = { categoryExpanded = false },
            modifier = Modifier
              .background(CardBackground)
              .border(1.dp, AccordPurple.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
          ) {
            listOf("Job Application", "Movies", "Shopping", "Health", "Other").forEach { option ->
              DropdownMenuItem(
                text = {
                  Text(
                    text = option,
                    color = PureWhite,
                    fontSize = 14.sp
                  )
                },
                onClick = {
                  categorySelected = option
                  categoryExpanded = false
                },
                leadingIcon = {
                  val catColor = getCategoryColor(option)
                  Icon(
                    imageVector = getCategoryIcon(option),
                    contentDescription = null,
                    tint = catColor,
                    modifier = Modifier.size(16.dp)
                  )
                },
                modifier = Modifier.testTag("edit_task_category_option_${option.lowercase().replace(" ", "_")}")
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Priority Level Selection Row
        Text(
          text = "Priority Level:",
          color = MutedText,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(bottom = 6.dp)
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          listOf("LOW", "MEDIUM", "HIGH").forEach { level ->
            val isSelected = prioritySelected == level
            val priorityBtnColor = when (level) {
              "HIGH" -> HighPriorityColor
              "MEDIUM" -> MediumPriorityColor
              "LOW" -> LowPriorityColor
              else -> LowPriorityColor
            }

            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSelected) priorityBtnColor.copy(alpha = 0.2f) else CardBackground)
                .border(
                  width = 1.dp,
                  color = if (isSelected) priorityBtnColor else Color.Gray.copy(alpha = 0.2f),
                  shape = RoundedCornerShape(8.dp)
                )
                .clickable { prioritySelected = level }
                .padding(vertical = 10.dp)
                .testTag("edit_priority_selection_$level"),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = level,
                color = if (isSelected) priorityBtnColor else MutedText,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = {
              keyboardController?.hide()
              focusManager.clearFocus()
              onDismiss()
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccordPurple),
            border = BorderStroke(1.dp, AccordPurple.copy(alpha = 0.4f)),
            modifier = Modifier
              .weight(1f)
              .testTag("cancel_edit_task_button")
          ) {
            Text("Cancel", fontSize = 13.sp)
          }

          Button(
            onClick = {
              keyboardController?.hide()
              focusManager.clearFocus()
              onSave(
                task.copy(
                  title = title.trim(),
                  description = description.takeIf { it.isNotBlank() }?.trim(),
                  url = url.takeIf { it.isNotBlank() }?.trim(),
                  dueDate = dueDateText,
                  priority = prioritySelected.uppercase(),
                  reminderMinutes = reminderMins,
                  category = categorySelected,
                  isSynced = false // Mark unsynced for local updates
                )
              )
            },
            colors = ButtonDefaults.buttonColors(containerColor = AccordPurple),
            modifier = Modifier
              .weight(1f)
              .testTag("save_edit_task_button"),
            enabled = title.isNotBlank()
          ) {
            Text("Save Changes", color = PureWhite, fontSize = 13.sp)
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: TaskViewModel) {
  val email = remember { mutableStateOf("") }
  val code = remember { mutableStateOf("") }
  
  val otpSent by viewModel.otpSent.collectAsStateWithLifecycle()
  val authLoading by viewModel.authLoading.collectAsStateWithLifecycle()
  val authMessage by viewModel.authMessage.collectAsStateWithLifecycle()
  val authError by viewModel.authError.collectAsStateWithLifecycle()
  val resendCooldown by viewModel.resendCooldown.collectAsStateWithLifecycle()
  val loginDisplayNameInput by viewModel.loginDisplayNameInput.collectAsStateWithLifecycle()
  val isReturningUser by viewModel.isReturningUser.collectAsStateWithLifecycle()

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(DarkBackground)
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 400.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Box(
        modifier = Modifier
          .clip(CircleShape)
          .background(AccordPurple.copy(alpha = 0.15f))
          .padding(20.dp)
      ) {
        Icon(
          imageVector = Icons.Default.TaskAlt,
          contentDescription = "Task Pro Logo",
          tint = AccordPurple,
          modifier = Modifier.size(64.dp)
        )
      }

      Text(
        text = "Welcome to Task Pro",
        color = PureWhite,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )

      Text(
        text = if (!otpSent) 
          "Enter your email address below to receive a secure 8-digit confirmation code." 
          else "We have sent an 8-digit confirmation code to ${email.value}. Check your inbox and enter it below.",
        color = MutedText,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp,
        modifier = Modifier.padding(horizontal = 8.dp)
      )

      Spacer(modifier = Modifier.height(4.dp))

      if (!authError.isNullOrEmpty()) {
        Card(
          colors = CardDefaults.cardColors(containerColor = Color(0xFF4A1521)),
          border = BorderStroke(1.dp, Color(0xFFFF4E64)),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.ErrorOutline,
              contentDescription = "Error",
              tint = Color(0xFFFF4E64),
              modifier = Modifier.size(20.dp)
            )
            Text(
              text = authError!!,
              color = Color(0xFFFFB4BC),
              fontSize = 13.sp,
              modifier = Modifier.weight(1f)
            )
          }
        }
      }

      if (!authMessage.isNullOrEmpty() && authError.isNullOrEmpty()) {
        Card(
          colors = CardDefaults.cardColors(containerColor = Color(0xFF133221)),
          border = BorderStroke(1.dp, Color(0xFF2E7D32)),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = "Success",
              tint = Color(0xFF81C784),
              modifier = Modifier.size(20.dp)
            )
            Text(
              text = authMessage!!,
              color = Color(0xFFC8E6C9),
              fontSize = 13.sp,
              modifier = Modifier.weight(1f)
            )
          }
        }
      }

      if (!otpSent) {
        OutlinedTextField(
          value = email.value,
          onValueChange = { 
            email.value = it 
            viewModel.clearAuthError()
            viewModel.checkEmailProfile(it)
          },
          label = { Text("Email Address", color = MutedText) },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Email,
              contentDescription = "Email Icon",
              tint = MutedText
            )
          },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = PureWhite,
            unfocusedTextColor = PureWhite,
            focusedBorderColor = AccordPurple,
            unfocusedBorderColor = AccordPurple.copy(alpha = 0.3f)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("email_input")
        )
        val isEmailValid = remember(email.value) {
          android.util.Patterns.EMAIL_ADDRESS.matcher(email.value).matches()
        }

        if (email.value.isNotEmpty() && !isEmailValid) {
          Text(
            text = "Please enter a valid email address",
            color = Color(0xFFFF4E64),
            fontSize = 12.sp,
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 4.dp, vertical = 2.dp)
          )
        }
        OutlinedTextField(
          value = loginDisplayNameInput,
          onValueChange = { 
            viewModel.setLoginDisplayName(it)
          },
          label = { Text("Your Name", color = MutedText) },
          placeholder = { Text("Your Name", color = MutedText.copy(alpha = 0.5f)) },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Person,
              contentDescription = "Name Icon",
              tint = MutedText
            )
          },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = PureWhite,
            unfocusedTextColor = PureWhite,
            focusedBorderColor = AccordPurple,
            unfocusedBorderColor = AccordPurple.copy(alpha = 0.3f)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("display_name_input")
        )

        Text(
          text = "Use your first name, last name, or a short nickname",
          color = MutedText.copy(alpha = 0.5f),
          fontSize = 11.sp,
          lineHeight = 16.sp,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
        )

        Button(
          onClick = { viewModel.sendOtp(email.value) },
          colors = ButtonDefaults.buttonColors(containerColor = AccordPurple),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("send_code_button"),
          shape = RoundedCornerShape(12.dp),
          enabled = !authLoading && isEmailValid && loginDisplayNameInput.trim().isNotEmpty()
        ) {
          if (authLoading) {
            CircularProgressIndicator(
              color = PureWhite,
              modifier = Modifier.size(24.dp),
              strokeWidth = 2.dp
            )
          } else {
            Text(
              "Send Code",
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = PureWhite
            )
          }
        }
      } else {
        OutlinedTextField(
          value = code.value,
          onValueChange = { 
            code.value = it 
            viewModel.clearAuthError()
          },
          label = { Text("8-Digit Code", color = MutedText) },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Lock,
              contentDescription = "Lock Icon",
              tint = MutedText
            )
          },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = PureWhite,
            unfocusedTextColor = PureWhite,
            focusedBorderColor = AccordPurple,
            unfocusedBorderColor = AccordPurple.copy(alpha = 0.3f)
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("otp_input")
        )

        Button(
          onClick = { viewModel.verifyOtp(email.value, code.value) },
          colors = ButtonDefaults.buttonColors(containerColor = AccordPurple),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("confirm_otp_button"),
          shape = RoundedCornerShape(12.dp),
          enabled = !authLoading && code.value.trim().isNotEmpty()
        ) {
          if (authLoading) {
            CircularProgressIndicator(
              color = PureWhite,
              modifier = Modifier.size(24.dp),
              strokeWidth = 2.dp
            )
          } else {
            Text(
              "Verify & Sign In",
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = PureWhite
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          val resendText = if (resendCooldown > 0) "Resend in ${resendCooldown}s" else "Resend Code"
          TextButton(
            onClick = { viewModel.sendOtp(email.value) },
            enabled = !authLoading && resendCooldown == 0
          ) {
            Text(
              text = resendText,
              color = if (resendCooldown > 0) MutedText else AccordPurple,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold
            )
          }

          TextButton(
            onClick = { 
              viewModel.logout() 
              code.value = ""
            },
            enabled = !authLoading
          ) {
            Text("Edit Email", color = AccordPurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}
