package com.taskpro.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@Entity(tableName = "tasks")
@JsonClass(generateAdapter = true)
data class Task(
  @PrimaryKey(autoGenerate = false)
  @Json(name = "id") val id: Long, // Use timestamp/random for local unsynced IDs, or backend ID
  @Json(name = "title") val title: String,
  @Json(name = "description") val description: String? = null,
  @Json(name = "url") val url: String? = null,
  @ColumnInfo(name = "due_date")
  @Json(name = "due_date") val dueDate: String, // String representation formatted beautifully
  @Json(name = "priority") val priority: String, // "LOW", "MEDIUM", "HIGH"
  @ColumnInfo(name = "is_completed")
  @Json(name = "is_completed") val isCompleted: Boolean = false,
  @ColumnInfo(name = "created_at")
  @Json(name = "created_at") val createdAt: String? = null,
  @ColumnInfo(name = "is_synced")
  @Json(name = "is_synced") val isSynced: Boolean = false,
  @ColumnInfo(name = "reminder_minutes")
  @Json(name = "reminder_minutes") val reminderMinutes: Int = -1,
  @ColumnInfo(name = "category")
  @Json(name = "category") val category: String = "Other",
  @ColumnInfo(name = "user_id")
  @Json(name = "user_id") val userId: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseTask(
  @Json(name = "id") val id: Long,
  @Json(name = "title") val title: String,
  @Json(name = "description") val description: String? = null,
  @Json(name = "url") val url: String? = null,
  @Json(name = "due_date") val dueDate: String,
  @Json(name = "priority") val priority: String,
  @Json(name = "is_completed") val isCompleted: Boolean = false,
  @Json(name = "created_at") val createdAt: String? = null,
  @Json(name = "user_id") val userId: String? = null
)

fun Task.toSupabaseTask() = SupabaseTask(
  id = id,
  title = title,
  description = description,
  url = url,
  dueDate = dueDate,
  priority = priority,
  isCompleted = isCompleted,
  createdAt = createdAt,
  userId = userId
)

fun SupabaseTask.toTask(isSynced: Boolean = true, reminderMinutes: Int = -1, category: String = "Other") = Task(
  id = id,
  title = title,
  description = description,
  url = url,
  dueDate = dueDate,
  priority = priority,
  isCompleted = isCompleted,
  createdAt = createdAt,
  isSynced = isSynced,
  reminderMinutes = reminderMinutes,
  category = category,
  userId = userId
)
