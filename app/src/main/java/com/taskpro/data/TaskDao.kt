package com.taskpro.data

import androidx.room.*
import com.taskpro.model.Task
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
  @Query("SELECT * FROM tasks ORDER BY due_date ASC")
  fun getAllTasks(): Flow<List<Task>>

  @Query("SELECT * FROM tasks WHERE is_synced = 0")
  suspend fun getUnsyncedTasks(): List<Task>

  @Query("SELECT * FROM tasks")
  suspend fun getAllTasksList(): List<Task>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTask(task: Task)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTasks(tasks: List<Task>)

  @Query("DELETE FROM tasks WHERE id = :id")
  suspend fun deleteTaskById(id: Long)

  @Delete
  suspend fun deleteTasks(tasks: List<Task>)

  @Query("DELETE FROM tasks")
  suspend fun clearAll()
}
