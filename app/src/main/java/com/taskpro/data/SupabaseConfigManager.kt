package com.taskpro.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit

class SupabaseConfigManager(context: Context) {
  private val sharedPrefs = context.getSharedPreferences("supabase_config", Context.MODE_PRIVATE)
  private val TAG = "SupabaseConfigManager"

  fun saveSupabaseUrl(url: String) {
    sharedPrefs.edit { putString("supabase_url", url.trim()) }
  }

  fun saveSupabaseKey(key: String) {
    sharedPrefs.edit { putString("supabase_key", key.trim()) }
  }

  fun saveSession(accessToken: String, refreshToken: String, expiresInSeconds: Long, userId: String, userEmail: String, displayName: String? = null) {
    val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000)
    sharedPrefs.edit {
      putString("session_access_token", accessToken)
      putString("session_refresh_token", refreshToken)
      putLong("session_expires_at", expiresAt)
      putString("session_user_id", userId)
      putString("session_user_email", userEmail)
      if (displayName != null) {
        putString("session_user_display_name", displayName)
      }
    }
  }

  fun saveUserDisplayName(displayName: String) {
    sharedPrefs.edit { putString("session_user_display_name", displayName.trim()) }
  }

  fun getUserDisplayName(): String? {
    return sharedPrefs.getString("session_user_display_name", null)
  }

  fun clearSession() {
    sharedPrefs.edit {
      remove("session_access_token")
      remove("session_refresh_token")
      remove("session_expires_at")
      remove("session_user_id")
      remove("session_user_email")
      remove("session_user_display_name")
    }
  }

  fun getAccessToken(): String? {
    return sharedPrefs.getString("session_access_token", null)
  }

  fun getRefreshToken(): String? {
    return sharedPrefs.getString("session_refresh_token", null)
  }

  fun getUserId(): String? {
    return sharedPrefs.getString("session_user_id", null)
  }

  fun getUserEmail(): String? {
    return sharedPrefs.getString("session_user_email", null)
  }

  fun getSessionExpiresAt(): Long {
    return sharedPrefs.getLong("session_expires_at", 0)
  }

  fun isUserLoggedIn(): Boolean {
    return !getUserId().isNullOrEmpty()
  }

  fun isSessionExpired(): Boolean {
    val expiresAt = getSessionExpiresAt()
    if (expiresAt == 0L) return true
    return System.currentTimeMillis() + 60000 > expiresAt
  }

  fun clear() {
    sharedPrefs.edit {
      remove("supabase_url")
      remove("supabase_key")
    }
    clearSession()
  }

  fun getSupabaseUrl(): String {
    val saved = sharedPrefs.getString("supabase_url", "") ?: ""
    if (saved.isNotEmpty()) {
      Log.d(TAG, "getSupabaseUrl: returning user-saved config: $saved")
      return saved
    }

    // Fallback to BuildConfig if compiled-in
    val buildConfigValue = try {
      val buildConfigClass = Class.forName("com.example.BuildConfig")
      val field = buildConfigClass.getField("SUPABASE_URL")
      field.get(null) as? String ?: ""
    } catch (e: Exception) {
      Log.w(TAG, "getSupabaseUrl: BuildConfig.SUPABASE_URL not available via reflection", e)
      ""
    }
    
    if (buildConfigValue.isNotEmpty() && buildConfigValue.lowercase() != "placeholder_url") {
      Log.d(TAG, "getSupabaseUrl: returning compiled-in BuildConfig: $buildConfigValue")
      return buildConfigValue
    }

    // Ultimate fallback: Safely hardcoded in Kotlin setup code
    val hardcodedUrl = "https://zqxgfttjreispftyqtam.supabase.co"
    Log.d(TAG, "getSupabaseUrl: returning ultimate default fallback: $hardcodedUrl")
    return hardcodedUrl
  }

  fun getSupabaseKey(): String {
    val saved = sharedPrefs.getString("supabase_key", "") ?: ""
    if (saved.isNotEmpty()) {
      Log.d(TAG, "getSupabaseKey: returning user-saved config key (length ${saved.length})")
      return saved
    }

    // Fallback to BuildConfig if compiled-in
    val buildConfigValue = try {
      val buildConfigClass = Class.forName("com.example.BuildConfig")
      val field = buildConfigClass.getField("SUPABASE_KEY")
      field.get(null) as? String ?: ""
    } catch (e: Exception) {
      Log.w(TAG, "getSupabaseKey: BuildConfig.SUPABASE_KEY not available via reflection", e)
      ""
    }
    
    if (buildConfigValue.isNotEmpty() && buildConfigValue.lowercase() != "placeholder_key") {
      Log.d(TAG, "getSupabaseKey: returning compiled-in BuildConfig key (length ${buildConfigValue.length})")
      return buildConfigValue
    }

    // Ultimate fallback: Safely hardcoded in Kotlin setup code
    val hardcodedKey = "sb_publishable_DAlvQj1PVSk8qWGV2nFCpg_rfxZjiUZ"
    Log.d(TAG, "getSupabaseKey: returning ultimate default fallback key (length ${hardcodedKey.length})")
    return hardcodedKey
  }

  fun isConfigured(): Boolean {
    val url = getSupabaseUrl()
    val key = getSupabaseKey()
    return url.isNotEmpty() && url.startsWith("https://") && key.isNotEmpty()
  }
}
