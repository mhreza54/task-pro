package com.taskpro.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SupabaseProfile(
  @Json(name = "id") val id: String,
  @Json(name = "display_name") val displayName: String,
  @Json(name = "email") val email: String,
  @Json(name = "created_at") val createdAt: String? = null
)
