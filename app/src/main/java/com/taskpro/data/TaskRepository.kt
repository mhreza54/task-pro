package com.taskpro.data

import android.util.Log
import com.taskpro.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

import com.taskpro.model.SupabaseProfile
import com.taskpro.model.toSupabaseTask
import com.taskpro.model.toTask

class TaskRepository(
  private val taskDao: TaskDao,
  private val configManager: SupabaseConfigManager
) {
  private var apiService: SupabaseApiService? = null
  private var currentBaseUrl: String = ""

  init {
    updateApiService()
  }

  fun updateApiService() {
    val baseUrl = configManager.getSupabaseUrl()
    if (baseUrl.isNotEmpty() && baseUrl != currentBaseUrl) {
      currentBaseUrl = baseUrl
      apiService = try {
        SupabaseApiService.create(baseUrl)
      } catch (e: Exception) {
        Log.e("TaskRepository", "Error creating Supabase API service", e)
        null
      }
    } else if (baseUrl.isEmpty()) {
      apiService = null
      currentBaseUrl = ""
    }
  }

  fun getTasksFlow(): Flow<List<Task>> = taskDao.getAllTasks()

  private fun getHeaders(): Pair<String, String>? {
    val key = configManager.getSupabaseKey()
    if (key.isEmpty()) return null
    val token = configManager.getAccessToken()
    val authHeader = if (configManager.isUserLoggedIn() && !token.isNullOrEmpty()) "Bearer $token" else "Bearer $key"
    return Pair(key, authHeader)
  }

  /**
   * Syncs with remote database.
   * 1. Upload unsynced local tasks.
   * 2. Download all tasks from remote and merge/replace local cache.
   */
  suspend fun syncWithRemote(): Result<Unit> = withContext(Dispatchers.IO) {
    if (!configManager.isConfigured()) {
      return@withContext Result.failure(Exception("Supabase is not configured"))
    }
    updateApiService()
    val api = apiService ?: return@withContext Result.failure(Exception("Supabase api connection failed"))
    val (apiKey, auth) = getHeaders() ?: return@withContext Result.failure(Exception("Supabase credentials missing"))

    try {
      // 1. Check for unsynced local tasks and upload them
      val unsynced = taskDao.getUnsyncedTasks()
      for (task in unsynced) {
        try {
          // If the task was created locally with a temporary ID, we might insert it
          val response = api.insertTask(apiKey, auth, task.toSupabaseTask())
          if (response.isSuccessful) {
            val insertedList = response.body()
            if (!insertedList.isNullOrEmpty()) {
              // Delete local temp task and insert task with database ID
              taskDao.deleteTaskById(task.id)
              taskDao.insertTask(insertedList[0].toTask(
                isSynced = true,
                reminderMinutes = task.reminderMinutes,
                category = task.category
              ))
            } else {
              // Just mark as synced if return representation isn't fully supported
              taskDao.insertTask(task.copy(isSynced = true))
            }
          }
        } catch (e: Exception) {
          Log.e("TaskRepository", "Failed uploading unsynced task: ${task.title}", e)
        }
      }

      // 2. Fetch all remote tasks
      val response = api.getTasks(apiKey, auth)
      if (response.isSuccessful) {
        val remoteTasks = response.body() ?: emptyList()
        // Save them to local Room to keep cache updated
        // Get existing tasks to preserve category & reminderMinutes
        val existingTasksMap = taskDao.getAllTasksList().associateBy { it.id }
        
        val mergedTasks = remoteTasks.map { remote ->
          val existing = existingTasksMap[remote.id]
          remote.toTask(
            isSynced = true,
            reminderMinutes = existing?.reminderMinutes ?: -1,
            category = existing?.category ?: "Other"
          )
        }
        
        taskDao.clearAll()
        taskDao.insertTasks(mergedTasks)
        return@withContext Result.success(Unit)
      } else {
        val errorBody = response.errorBody()?.string() ?: "Unknown error"
        val userFriendlyMessage = if (errorBody.contains("PGRST205") || errorBody.contains("relation") || response.code() == 404) {
          "Supabase HTTP 404 (PGRST205): The 'tasks' table was not found in your database. Please create the table in your Supabase SQL Editor using the schema provided in Settings."
        } else {
          "Supabase HTTP ${response.code()}: $errorBody"
        }
        return@withContext Result.failure(Exception(userFriendlyMessage))
      }
    } catch (e: Exception) {
      Log.e("TaskRepository", "Exception during remote sync", e)
      return@withContext Result.failure(e)
    }
  }

  suspend fun insertTask(task: Task): Result<Unit> = withContext(Dispatchers.IO) {
    if (!configManager.isConfigured()) {
      // Offline mode saving
      taskDao.insertTask(task.copy(isSynced = false))
      return@withContext Result.success(Unit)
    }

    updateApiService()
    val api = apiService
    val headers = getHeaders()

    if (api == null || headers == null) {
      taskDao.insertTask(task.copy(isSynced = false))
      return@withContext Result.success(Unit)
    }

    try {
      val (apiKey, auth) = headers
      val response = api.insertTask(apiKey, auth, task.toSupabaseTask())
      if (response.isSuccessful) {
        val insertedList = response.body()
        if (!insertedList.isNullOrEmpty()) {
          taskDao.insertTask(insertedList[0].toTask(
            isSynced = true,
            reminderMinutes = task.reminderMinutes,
            category = task.category
          ))
        } else {
          taskDao.insertTask(task.copy(isSynced = true))
        }
        return@withContext Result.success(Unit)
      } else {
        val errorBody = response.errorBody()?.string() ?: "Unknown error"
        val userFriendlyMessage = if (errorBody.contains("PGRST205") || errorBody.contains("relation") || response.code() == 404) {
          "Supabase HTTP 404 (PGRST205): The 'tasks' table was not found in your database. Please create the table in your Supabase SQL Editor using the schema provided in Settings."
        } else {
          "API insert unsuccessful, saved locally. Code ${response.code()}: $errorBody"
        }
        // Fallback: save locally and mark unsynced
        taskDao.insertTask(task.copy(isSynced = false))
        return@withContext Result.failure(Exception(userFriendlyMessage))
      }
    } catch (e: Exception) {
      taskDao.insertTask(task.copy(isSynced = false))
      return@withContext Result.failure(e)
    }
  }

  suspend fun updateTask(task: Task): Result<Unit> = withContext(Dispatchers.IO) {
    if (!configManager.isConfigured()) {
      taskDao.insertTask(task.copy(isSynced = false))
      return@withContext Result.success(Unit)
    }

    updateApiService()
    val api = apiService
    val headers = getHeaders()

    if (api == null || headers == null) {
      taskDao.insertTask(task.copy(isSynced = false))
      return@withContext Result.success(Unit)
    }

    try {
      val (apiKey, auth) = headers
      val response = api.updateTask(apiKey, auth, "eq.${task.id}", task.toSupabaseTask())
      if (response.isSuccessful) {
        val updatedList = response.body()
        if (!updatedList.isNullOrEmpty()) {
          taskDao.insertTask(updatedList[0].toTask(
            isSynced = true,
            reminderMinutes = task.reminderMinutes,
            category = task.category
          ))
        } else {
          taskDao.insertTask(task.copy(isSynced = true))
        }
        return@withContext Result.success(Unit)
      } else {
        val errorBody = response.errorBody()?.string() ?: "Unknown error"
        val userFriendlyMessage = if (errorBody.contains("PGRST205") || errorBody.contains("relation") || response.code() == 404) {
          "Supabase HTTP 404 (PGRST205): The 'tasks' table was not found in your database. Please create the table in your Supabase SQL Editor using the schema provided in Settings."
        } else {
          "API update unsuccessful, saved locally. Code ${response.code()}: $errorBody"
        }
        taskDao.insertTask(task.copy(isSynced = false))
        return@withContext Result.failure(Exception(userFriendlyMessage))
      }
    } catch (e: Exception) {
      taskDao.insertTask(task.copy(isSynced = false))
      return@withContext Result.failure(e)
    }
  }

  suspend fun deleteTask(task: Task): Result<Unit> = withContext(Dispatchers.IO) {
    taskDao.deleteTaskById(task.id)

    if (!configManager.isConfigured()) {
      return@withContext Result.success(Unit)
    }

    updateApiService()
    val api = apiService
    val headers = getHeaders()

    if (api == null || headers == null) {
      return@withContext Result.success(Unit)
    }

    try {
      val (apiKey, auth) = headers
      val response = api.deleteTask(apiKey, auth, "eq.${task.id}")
      if (response.isSuccessful) {
        return@withContext Result.success(Unit)
      } else {
        return@withContext Result.failure(Exception("API delete failed with code ${response.code()}"))
      }
    } catch (e: Exception) {
      return@withContext Result.failure(e)
    }
  }

  suspend fun getProfileByEmail(email: String): Result<SupabaseProfile?> = withContext(Dispatchers.IO) {
    updateApiService()
    val api = apiService ?: return@withContext Result.failure(Exception("Supabase API connection failed"))
    val headers = getHeaders() ?: return@withContext Result.failure(Exception("No API credentials"))
    try {
      val response = api.getProfileByEmail(headers.first, headers.second, "eq.${email.trim()}")
      if (response.isSuccessful) {
        Result.success(response.body()?.firstOrNull())
      } else {
        Result.failure(Exception("Query profile error: ${response.errorBody()?.string()}"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun getProfileById(id: String): Result<SupabaseProfile?> = withContext(Dispatchers.IO) {
    updateApiService()
    val api = apiService ?: return@withContext Result.failure(Exception("Supabase API connection failed"))
    val headers = getHeaders() ?: return@withContext Result.failure(Exception("No API credentials"))
    try {
      val response = api.getProfileById(headers.first, headers.second, "eq.$id")
      if (response.isSuccessful) {
        Result.success(response.body()?.firstOrNull())
      } else {
        Result.failure(Exception("Query profile error: ${response.errorBody()?.string()}"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun insertProfile(profile: SupabaseProfile): Result<SupabaseProfile> = withContext(Dispatchers.IO) {
    updateApiService()
    val api = apiService ?: return@withContext Result.failure(Exception("Supabase API connection failed"))
    val headers = getHeaders() ?: return@withContext Result.failure(Exception("No API credentials"))
    try {
      val response = api.insertProfile(headers.first, headers.second, profile)
      if (response.isSuccessful) {
        val inserted = response.body()?.firstOrNull() ?: profile
        Result.success(inserted)
      } else {
        Result.failure(Exception("Insert profile error: ${response.errorBody()?.string()}"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun updateProfile(id: String, profile: SupabaseProfile): Result<SupabaseProfile> = withContext(Dispatchers.IO) {
    updateApiService()
    val api = apiService ?: return@withContext Result.failure(Exception("Supabase API connection failed"))
    val headers = getHeaders() ?: return@withContext Result.failure(Exception("No API credentials"))
    try {
      val response = api.updateProfile(headers.first, headers.second, "eq.$id", profile)
      if (response.isSuccessful) {
        val updated = response.body()?.firstOrNull() ?: profile
        Result.success(updated)
      } else {
        Result.failure(Exception("Update profile error: ${response.errorBody()?.string()}"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
