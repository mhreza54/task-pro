package com.taskpro.data

import com.taskpro.model.SupabaseProfile
import com.taskpro.model.SupabaseTask
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*

interface SupabaseApiService {
  @GET("rest/v1/tasks")
  suspend fun getTasks(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Query("select") select: String = "*"
  ): Response<List<SupabaseTask>>

  @POST("rest/v1/tasks")
  @Headers("Prefer: return=representation")
  suspend fun insertTask(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Body task: SupabaseTask
  ): Response<List<SupabaseTask>>

  @PATCH("rest/v1/tasks")
  @Headers("Prefer: return=representation")
  suspend fun updateTask(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Query("id") idFilter: String,
    @Body task: SupabaseTask
  ): Response<List<SupabaseTask>>

  @DELETE("rest/v1/tasks")
  suspend fun deleteTask(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Query("id") idFilter: String
  ): Response<Unit>

  @GET("rest/v1/profiles")
  suspend fun getProfileByEmail(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Query("email") emailFilter: String
  ): Response<List<SupabaseProfile>>

  @GET("rest/v1/profiles")
  suspend fun getProfileById(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Query("id") idFilter: String
  ): Response<List<SupabaseProfile>>

  @POST("rest/v1/profiles")
  @Headers("Prefer: return=representation")
  suspend fun insertProfile(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Body profile: SupabaseProfile
  ): Response<List<SupabaseProfile>>

  @PATCH("rest/v1/profiles")
  @Headers("Prefer: return=representation")
  suspend fun updateProfile(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authHeader: String,
    @Query("id") idFilter: String,
    @Body profile: SupabaseProfile
  ): Response<List<SupabaseProfile>>

  companion object {
    fun create(baseUrl: String): SupabaseApiService {
      val logger = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
      }
      val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(logger)
        .build()

      val cleanUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

      return Retrofit.Builder()
        .baseUrl(cleanUrl)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(SupabaseApiService::class.java)
    }
  }
}
