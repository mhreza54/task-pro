package com.taskpro.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.taskpro.data.*
import com.taskpro.model.Task
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class TaskFilter {
  ALL, ACTIVE, COMPLETED, OVERDUE, DUE_TODAY
}

enum class TaskSortBy {
  DUE_DATE, PRIORITY
}

class TaskViewModel(application: Application) : AndroidViewModel(application) {
  private val configManager = SupabaseConfigManager(application)
  private val taskDao = AppDatabase.getDatabase(application).taskDao()
  private val repository = TaskRepository(taskDao, configManager)
  private var authService = SupabaseAuthService.create(configManager.getSupabaseUrl())

  // Authentication states
  private val _isUserLoggedIn = MutableStateFlow(configManager.isUserLoggedIn())
  val isUserLoggedIn: StateFlow<Boolean> = _isUserLoggedIn.asStateFlow()

  private val _userEmail = MutableStateFlow(configManager.getUserEmail())
  val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

  private val _userDisplayName = MutableStateFlow<String?>(configManager.getUserDisplayName())
  val userDisplayName: StateFlow<String?> = _userDisplayName.asStateFlow()

  private val _loginDisplayNameInput = MutableStateFlow("")
  val loginDisplayNameInput: StateFlow<String> = _loginDisplayNameInput.asStateFlow()

  private val _isReturningUser = MutableStateFlow(false)
  val isReturningUser: StateFlow<Boolean> = _isReturningUser.asStateFlow()

  private val _profileUpdateSuccess = MutableStateFlow(false)
  val profileUpdateSuccess: StateFlow<Boolean> = _profileUpdateSuccess.asStateFlow()

  fun setLoginDisplayName(name: String) {
    _loginDisplayNameInput.value = name
  }

  private val _sessionChecked = MutableStateFlow(false)
  val sessionChecked: StateFlow<Boolean> = _sessionChecked.asStateFlow()

  private val _otpSent = MutableStateFlow(false)
  val otpSent: StateFlow<Boolean> = _otpSent.asStateFlow()

  private val _authLoading = MutableStateFlow(false)
  val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

  private val _authMessage = MutableStateFlow<String?>(null)
  val authMessage: StateFlow<String?> = _authMessage.asStateFlow()

  private val _authError = MutableStateFlow<String?>(null)
  val authError: StateFlow<String?> = _authError.asStateFlow()

  private val _resendCooldown = MutableStateFlow(0)
  val resendCooldown: StateFlow<Int> = _resendCooldown.asStateFlow()

  private var cooldownJob: kotlinx.coroutines.Job? = null

  private fun startCooldownTimer() {
    cooldownJob?.cancel()
    cooldownJob = viewModelScope.launch {
      _resendCooldown.value = 60
      while (_resendCooldown.value > 0) {
        kotlinx.coroutines.delay(1000)
        _resendCooldown.value -= 1
      }
    }
  }

  // Filters and sorting
  private val _filter = MutableStateFlow(TaskFilter.DUE_TODAY)
  val filter: StateFlow<TaskFilter> = _filter.asStateFlow()

  private val _categoryFilter = MutableStateFlow("All")
  val categoryFilter: StateFlow<String> = _categoryFilter.asStateFlow()

  private val _sortBy = MutableStateFlow(TaskSortBy.DUE_DATE)
  val sortBy: StateFlow<TaskSortBy> = _sortBy.asStateFlow()

  // Dynamic credentials states
  private val _isConfigured = MutableStateFlow(configManager.isConfigured())
  val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

  private val _supabaseUrl = MutableStateFlow(configManager.getSupabaseUrl())
  val supabaseUrl: StateFlow<String> = _supabaseUrl.asStateFlow()

  private val _supabaseKey = MutableStateFlow(configManager.getSupabaseKey())
  val supabaseKey: StateFlow<String> = _supabaseKey.asStateFlow()

  // Interaction states
  private val _isSyncing = MutableStateFlow(false)
  val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

  private val _syncStatusString = MutableStateFlow<String?>(null)
  val syncStatusString: StateFlow<String?> = _syncStatusString.asStateFlow()

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  private fun sortTasks(list: List<Task>, sort: TaskSortBy): List<Task> {
    return when (sort) {
      TaskSortBy.DUE_DATE -> list.sortedBy { it.dueDate }
      TaskSortBy.PRIORITY -> list.sortedWith(
        compareBy<Task> {
          when (it.priority.uppercase()) {
            "HIGH" -> 0
            "MEDIUM" -> 1
            "LOW" -> 2
            else -> 3
          }
        }.thenBy { it.dueDate }
      )
    }
  }

  // UI state
  val tasks: StateFlow<List<Task>> = combine(
    repository.getTasksFlow(),
    _filter,
    _categoryFilter,
    _sortBy
  ) { allTasks, currentFilter, currentCategory, currentSort ->
    if (currentCategory != "All") {
      // Category filter views (Work, Personal, Shopping, etc.):
      // - Within each category view, apply the same logic: active tasks first, then completed, then overdue — each visually distinct
      // - Do not hide tasks from category views based on completion status — let the user see everything within their chosen category
      val catTasks = allTasks.filter { it.category.equals(currentCategory, ignoreCase = true) }
      
      val activeTasks = catTasks.filter { !it.isCompleted && !isTaskOverdue(it) }
      val completedTasks = catTasks.filter { it.isCompleted }
      val overdueTasks = catTasks.filter { !it.isCompleted && isTaskOverdue(it) }

      // Active tasks: due today at top, then next upcoming at top if no due today, then remaining
      val dueTodayActive = activeTasks.filter { isTaskDueToday(it) }
      val (top, remaining) = if (dueTodayActive.isNotEmpty()) {
        Pair(dueTodayActive, activeTasks.filter { !isTaskDueToday(it) })
      } else {
        val nextUpcoming = activeTasks.sortedBy { it.dueDate }.firstOrNull()
        if (nextUpcoming != null) {
          Pair(listOf(nextUpcoming), activeTasks.filter { it.id != nextUpcoming.id })
        } else {
          Pair(emptyList(), emptyList())
        }
      }

      val sortedActive = sortTasks(top, currentSort) + sortTasks(remaining, currentSort)
      val sortedCompleted = completedTasks.sortedByDescending { it.id }
      val sortedOverdue = overdueTasks.sortedBy { it.dueDate }

      sortedActive + sortedCompleted + sortedOverdue
    } else {
      // Normal filtering based on currentFilter when currentCategory is "All"
      when (currentFilter) {
        TaskFilter.ALL -> {
          // Main homepage / "All" view:
          // - Only show tasks that are NOT completed and NOT overdue
          // - If a task is due today, always show it at the top of the homepage regardless of other filters
          // - If there are no tasks due today, show the next upcoming task (the one with the nearest due date that is not yet overdue or completed) at the top
          // - The rest of the homepage shows remaining active tasks sorted by due date (nearest first)
          // - Completed and overdue tasks should NOT appear on the homepage/main "All" view at all
          val activeTasks = allTasks.filter { !it.isCompleted && !isTaskOverdue(it) }

          val dueTodayActive = activeTasks.filter { isTaskDueToday(it) }
          val (top, remaining) = if (dueTodayActive.isNotEmpty()) {
            Pair(dueTodayActive, activeTasks.filter { !isTaskDueToday(it) })
          } else {
            val nextUpcoming = activeTasks.sortedBy { it.dueDate }.firstOrNull()
            if (nextUpcoming != null) {
              Pair(listOf(nextUpcoming), activeTasks.filter { it.id != nextUpcoming.id })
            } else {
              Pair(emptyList(), emptyList())
            }
          }

          val sortedRemaining = if (currentSort == TaskSortBy.DUE_DATE) {
            remaining.sortedBy { it.dueDate }
          } else {
            sortTasks(remaining, currentSort)
          }

          sortTasks(top, currentSort) + sortedRemaining
        }
        TaskFilter.ACTIVE -> {
          allTasks.filter { !it.isCompleted }
        }
        TaskFilter.COMPLETED -> {
          // "Completed" filter view: only show tasks that have been marked as complete
          // Show them sorted by completion date (most recently completed first)
          allTasks.filter { it.isCompleted }.sortedByDescending { it.id }
        }
        TaskFilter.OVERDUE -> {
          // "Overdue" filter view: only show tasks where the due date has passed and the task is still not marked as complete
          // Show them sorted by due date (most overdue first - oldest first)
          allTasks.filter { isTaskOverdue(it) }.sortedBy { it.dueDate }
        }
        TaskFilter.DUE_TODAY -> {
          // "Due Today" filter view: show all tasks (completed or not) that have a due date of today
          // Completed tasks due today still appear but visually marked as completed
          sortTasks(allTasks.filter { isTaskDueToday(it) }, currentSort)
        }
      }
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val allTasks: StateFlow<List<Task>> = repository.getTasksFlow()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  init {
    checkSession()
  }

  fun checkSession() {
    viewModelScope.launch {
      _sessionChecked.value = false
      if (!configManager.isConfigured()) {
        _sessionChecked.value = true
        return@launch
      }

      authService = SupabaseAuthService.create(configManager.getSupabaseUrl())
      repository.updateApiService()

      if (configManager.isUserLoggedIn()) {
        if (configManager.isSessionExpired()) {
          val refreshToken = configManager.getRefreshToken()
          if (!refreshToken.isNullOrEmpty()) {
            _authLoading.value = true
            try {
              val apiKey = configManager.getSupabaseKey()
              val response = authService.refreshToken(apiKey = apiKey, request = RefreshTokenRequest(refreshToken))
              if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                configManager.saveSession(
                  accessToken = body.accessToken,
                  refreshToken = body.refreshToken,
                  expiresInSeconds = body.expiresIn,
                  userId = body.user.id,
                  userEmail = body.user.email ?: configManager.getUserEmail() ?: ""
                )
                _isUserLoggedIn.value = true
                _userEmail.value = configManager.getUserEmail()
                repository.updateApiService()
                fetchProfileOnStartup(body.user.id)
                syncTasks()
              } else {
                logout()
              }
            } catch (e: Exception) {
              Log.e("TaskViewModel", "Auto-refresh exception", e)
              if (e is java.io.IOException || e is java.net.ConnectException || e is java.net.UnknownHostException) {
                _isUserLoggedIn.value = true
                _userEmail.value = configManager.getUserEmail()
              } else {
                logout()
              }
            } finally {
              _authLoading.value = false
            }
          } else {
            logout()
          }
        } else {
          _isUserLoggedIn.value = true
          _userEmail.value = configManager.getUserEmail()
          val uId = configManager.getUserId()
          if (!uId.isNullOrEmpty()) {
            fetchProfileOnStartup(uId)
          }
          syncTasks()
        }
      } else {
        _isUserLoggedIn.value = false
      }
      _sessionChecked.value = true
    }
  }

  fun sendOtp(email: String) {
    if (email.trim().isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
      _authError.value = "Please enter a valid email address"
      return
    }
    if (_resendCooldown.value > 0) {
      _authError.value = "Please wait ${_resendCooldown.value}s before requesting a new code"
      return
    }
    viewModelScope.launch {
      _authLoading.value = true
      _authError.value = null
      _authMessage.value = null
      try {
        val apiKey = configManager.getSupabaseKey()
        authService = SupabaseAuthService.create(configManager.getSupabaseUrl())
        val request = OtpRequest(
          email = email.trim(), 
          options = null
        )
        val response = authService.signupOrSigninWithOtp(apiKey, request)
        if (response.isSuccessful) {
          _otpSent.value = true
          _authMessage.value = "A secure 8-digit confirmation code has been sent to your email!"
          startCooldownTimer()
        } else {
          val errBody = response.errorBody()?.string() ?: "Unknown API response"
          _authError.value = "Failed to send verification code: $errBody"
        }
      } catch (e: Exception) {
        _authError.value = "Network or connection error: ${e.message}"
      } finally {
        _authLoading.value = false
      }
    }
  }

  fun handleDeepLink(uri: android.net.Uri) {
    Log.d("TaskViewModel", "Handling deep link: $uri")
    val params = mutableMapOf<String, String>()
    
    // Parse query parameters
    try {
      if (uri.queryParameterNames != null) {
        for (name in uri.queryParameterNames) {
          uri.getQueryParameter(name)?.let { params[name] = it }
        }
      }
    } catch (e: Exception) {
      Log.e("TaskViewModel", "Error parsing query parameters", e)
    }

    // Parse fragment (hash) which is standard for Supabase redirect
    try {
      val fragment = uri.fragment
      if (!fragment.isNullOrEmpty()) {
        val pairs = fragment.split("&")
        for (pair in pairs) {
          val parts = pair.split("=", limit = 2)
          if (parts.size == 2) {
            params[parts[0]] = parts[1]
          }
        }
      }
    } catch (e: Exception) {
      Log.e("TaskViewModel", "Error parsing fragment", e)
    }

    // Check for errors redirected by Supabase
    val error = params["error"] ?: params["error_description"]
    if (!error.isNullOrEmpty()) {
      _authError.value = "Authentication failed: ${error.replace("+", " ")}"
      _otpSent.value = false // Let them type their email again
      _isUserLoggedIn.value = false
      return
    }

    val accessToken = params["access_token"]
    val refreshToken = params["refresh_token"]
    val expiresInStr = params["expires_in"]

    if (accessToken.isNullOrEmpty() || refreshToken.isNullOrEmpty()) {
      _authError.value = "The login link is invalid or has expired. Please request a new link."
      _otpSent.value = false
      _isUserLoggedIn.value = false
      return
    }

    val expiresInSeconds = expiresInStr?.toLongOrNull() ?: 3600L

    viewModelScope.launch {
      _authLoading.value = true
      _authError.value = null
      _authMessage.value = "Completing Supabase session..."
      try {
        val apiKey = configManager.getSupabaseKey()
        authService = SupabaseAuthService.create(configManager.getSupabaseUrl())
        
        // Fetch current user details of this token to verify, get ID and real email
        val userResponse = authService.getCurrentUser(apiKey, "Bearer $accessToken")
        if (userResponse.isSuccessful && userResponse.body() != null) {
          val user = userResponse.body()!!
          
          // Clear previous user tasks (offline cached ones)
          taskDao.clearAll()

          // Save the completed session parameters
          configManager.saveSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresInSeconds = expiresInSeconds,
            userId = user.id,
            userEmail = user.email ?: ""
          )
          
          _isUserLoggedIn.value = true
          _userEmail.value = user.email
          _otpSent.value = false
          _authMessage.value = "Welcome back!"
          
          repository.updateApiService()
          syncUserProfileAfterLogin(user.id, user.email ?: "", _loginDisplayNameInput.value)
          syncTasks()
        } else {
          val errBody = userResponse.errorBody()?.string() ?: "Token validation error"
          Log.e("TaskViewModel", "Failed to validate token: $errBody")
          _authError.value = "The login link is invalid or has expired. Please request a new link."
          _otpSent.value = false
          _isUserLoggedIn.value = false
        }
      } catch (e: Exception) {
        Log.e("TaskViewModel", "Network error during deep link verification", e)
        _authError.value = "Network or connection error during verification: ${e.message}"
      } finally {
        _authLoading.value = false
      }
    }
  }

  fun verifyOtp(email: String, token: String) {
    if (token.trim().length < 4) {
      _authError.value = "Please enter a valid verification code"
      return
    }
    viewModelScope.launch {
      _authLoading.value = true
      _authError.value = null
      try {
        val apiKey = configManager.getSupabaseKey()
        authService = SupabaseAuthService.create(configManager.getSupabaseUrl())
        val response = authService.verifyOtp(apiKey, VerifyOtpRequest(email = email.trim(), token = token.trim()))
        if (response.isSuccessful && response.body() != null) {
          val body = response.body()!!
          
          taskDao.clearAll()
          
          configManager.saveSession(
            accessToken = body.accessToken,
            refreshToken = body.refreshToken,
            expiresInSeconds = body.expiresIn,
            userId = body.user.id,
            userEmail = body.user.email ?: email.trim()
          )
          _isUserLoggedIn.value = true
          _userEmail.value = configManager.getUserEmail()
          _otpSent.value = false
          _authMessage.value = "Welcome back!"
          
          repository.updateApiService()
          syncUserProfileAfterLogin(body.user.id, body.user.email ?: email.trim(), _loginDisplayNameInput.value)
          syncTasks()
        } else {
          val errBody = response.errorBody()?.string() ?: "Unknown verification error"
          if (errBody.contains("invalid", ignoreCase = true) || errBody.contains("invalid_grant", ignoreCase = true)) {
            _authError.value = "Incorrect code. Please check and try again."
          } else if (errBody.contains("expired", ignoreCase = true) || errBody.contains("token_expired", ignoreCase = true) || errBody.contains("expired_token", ignoreCase = true) || errBody.contains("otp_expired", ignoreCase = true)) {
            _authError.value = "Incorrect code. Please check and try again, or request a new one if it's been a while."
          } else {
            _authError.value = "Verification unsuccessful: $errBody"
          }
        }
      } catch (e: Exception) {
        _authError.value = "Network or connection error during verification: ${e.message}"
      } finally {
        _authLoading.value = false
      }
    }
  }

  fun logout() {
    viewModelScope.launch {
      configManager.clearSession()
      taskDao.clearAll()
      _isUserLoggedIn.value = false
      _userEmail.value = null
      _userDisplayName.value = null
      _loginDisplayNameInput.value = ""
      _isReturningUser.value = false
      _otpSent.value = false
      _authMessage.value = null
      _authError.value = null
      cooldownJob?.cancel()
      _resendCooldown.value = 0
    }
  }

  fun checkEmailProfile(email: String) {
    if (email.trim().isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
      return
    }
    viewModelScope.launch {
      repository.getProfileByEmail(email.trim())
        .onSuccess { profile ->
          if (profile != null) {
            _loginDisplayNameInput.value = profile.displayName
            _isReturningUser.value = true
          } else {
            _isReturningUser.value = false
          }
        }
        .onFailure {
          _isReturningUser.value = false
        }
    }
  }

  fun updateProfileDisplayName(newName: String, onComplete: (Boolean) -> Unit = {}) {
    val userId = configManager.getUserId() ?: return
    val email = configManager.getUserEmail() ?: ""
    viewModelScope.launch {
      _profileUpdateSuccess.value = false
      val profile = com.taskpro.model.SupabaseProfile(
        id = userId,
        displayName = newName.trim(),
        email = email
      )
      repository.updateProfile(userId, profile)
        .onSuccess {
          configManager.saveUserDisplayName(it.displayName)
          _userDisplayName.value = it.displayName
          _profileUpdateSuccess.value = true
          onComplete(true)
          kotlinx.coroutines.delay(3000)
          _profileUpdateSuccess.value = false
        }
        .onFailure {
          showError("Failed to update profile name: ${it.message}")
          onComplete(false)
        }
    }
  }

  private fun fetchProfileOnStartup(userId: String) {
    viewModelScope.launch {
      repository.getProfileById(userId).onSuccess { profile ->
        if (profile != null) {
          configManager.saveUserDisplayName(profile.displayName)
          _userDisplayName.value = profile.displayName
        }
      }
    }
  }

  private fun syncUserProfileAfterLogin(userId: String, email: String, inputName: String) {
    viewModelScope.launch {
      repository.getProfileById(userId)
        .onSuccess { profile ->
          if (profile != null) {
            if (inputName.isNotEmpty() && profile.displayName != inputName) {
              val updatedProfile = profile.copy(displayName = inputName)
              repository.updateProfile(userId, updatedProfile)
                .onSuccess {
                  configManager.saveUserDisplayName(it.displayName)
                  _userDisplayName.value = it.displayName
                }
            } else {
              configManager.saveUserDisplayName(profile.displayName)
              _userDisplayName.value = profile.displayName
            }
          } else {
            val newProfile = com.taskpro.model.SupabaseProfile(
              id = userId,
              displayName = if (inputName.isNotEmpty()) inputName else "User",
              email = email
            )
            repository.insertProfile(newProfile)
              .onSuccess {
                configManager.saveUserDisplayName(it.displayName)
                _userDisplayName.value = it.displayName
              }
          }
        }
        .onFailure {
          if (inputName.isNotEmpty()) {
            val newProfile = com.taskpro.model.SupabaseProfile(
              id = userId,
              displayName = inputName,
              email = email
            )
            repository.insertProfile(newProfile)
              .onSuccess {
                configManager.saveUserDisplayName(it.displayName)
                _userDisplayName.value = it.displayName
              }
          }
        }
    }
  }

  fun clearAuthError() {
    _authError.value = null
  }

  fun clearAuthMessage() {
    _authMessage.value = null
  }

  fun setFilter(newFilter: TaskFilter) {
    _filter.value = newFilter
  }

  fun setCategoryFilter(newCategory: String) {
    _categoryFilter.value = newCategory
  }

  fun setSortBy(newSort: TaskSortBy) {
    _sortBy.value = newSort
  }

  fun updateCredentials(url: String, key: String) {
    viewModelScope.launch {
      configManager.saveSupabaseUrl(url)
      configManager.saveSupabaseKey(key)
      _supabaseUrl.value = url
      _supabaseKey.value = key
      _isConfigured.value = configManager.isConfigured()
      authService = SupabaseAuthService.create(url)
      repository.updateApiService()
      checkSession()
    }
  }

  fun resetCredentials() {
    viewModelScope.launch {
      logout()
      configManager.clear()
      _supabaseUrl.value = ""
      _supabaseKey.value = ""
      _isConfigured.value = false
      repository.updateApiService()
    }
  }

  fun syncTasks() {
    if (!configManager.isConfigured()) {
      showError("Please set Supabase configuration first")
      return
    }
    viewModelScope.launch {
      _isSyncing.value = true
      _syncStatusString.value = "Syncing with Supabase..."
      repository.syncWithRemote()
        .onSuccess {
          _syncStatusString.value = "Synced successfully!"
          _isSyncing.value = false
        }
        .onFailure { error ->
          _errorMessage.value = "Sync Alert: ${error.message}"
          _syncStatusString.value = "Local offline mode active"
          _isSyncing.value = false
        }
    }
  }

  fun addTask(title: String, description: String?, url: String?, dueDate: String, priority: String, reminderMinutes: Int, category: String) {
    viewModelScope.launch {
      // Local or remote creation
      val task = Task(
        id = System.currentTimeMillis(), // Unique local base ID
        title = title,
        description = description,
        url = url,
        dueDate = dueDate,
        priority = priority.uppercase(),
        isCompleted = false,
        isSynced = false,
        reminderMinutes = reminderMinutes,
        category = category,
        userId = configManager.getUserId()
      )
      repository.insertTask(task)
        .onSuccess {
          ReminderScheduler.scheduleReminder(getApplication(), task)
        }
        .onFailure { error ->
          showError("Saved offline. Remote error: ${error.message}")
          ReminderScheduler.scheduleReminder(getApplication(), task)
        }
    }
  }

  fun updateTask(task: Task) {
    viewModelScope.launch {
      repository.updateTask(task)
        .onSuccess {
          ReminderScheduler.scheduleReminder(getApplication(), task)
        }
        .onFailure { error ->
          showError("Saved locally. Sync failed: ${error.message}")
          ReminderScheduler.scheduleReminder(getApplication(), task)
        }
    }
  }

  fun toggleTaskCompletion(task: Task) {
    viewModelScope.launch {
      val updated = task.copy(isCompleted = !task.isCompleted)
      repository.updateTask(updated)
        .onSuccess {
          ReminderScheduler.scheduleReminder(getApplication(), updated)
        }
        .onFailure { error ->
          showError("Status changed locally. Sync failed: ${error.message}")
          ReminderScheduler.scheduleReminder(getApplication(), updated)
        }
    }
  }

  fun deleteTask(task: Task) {
    viewModelScope.launch {
      repository.deleteTask(task)
        .onSuccess {
          ReminderScheduler.cancelReminder(getApplication(), task)
        }
        .onFailure { error ->
          showError("Deleted locally. Sync failed: ${error.message}")
          ReminderScheduler.cancelReminder(getApplication(), task)
        }
    }
  }

  fun clearError() {
    _errorMessage.value = null
  }

  private fun showError(message: String) {
    _errorMessage.value = message
  }

  private fun isTaskOverdue(task: Task, currentMillis: Long = System.currentTimeMillis()): Boolean {
    if (task.isCompleted) return false
    return try {
      val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
      val date = formatter.parse(task.dueDate)
      date != null && date.time < currentMillis
    } catch (e: Exception) {
      false
    }
  }

  private fun isTaskDueToday(task: Task, currentMillis: Long = System.currentTimeMillis()): Boolean {
    return try {
      val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
      val taskDate = formatter.parse(task.dueDate) ?: return false
      
      val calTask = java.util.Calendar.getInstance().apply { time = taskDate }
      val calToday = java.util.Calendar.getInstance().apply { timeInMillis = currentMillis }
      
      calTask.get(java.util.Calendar.YEAR) == calToday.get(java.util.Calendar.YEAR) &&
        calTask.get(java.util.Calendar.DAY_OF_YEAR) == calToday.get(java.util.Calendar.DAY_OF_YEAR)
    } catch (e: Exception) {
      false
    }
  }

  class Factory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
        @Suppress("UNCHECKED_CAST")
        return TaskViewModel(application) as T
      }
      throw IllegalArgumentException("Unknown ViewModel class")
    }
  }
}
