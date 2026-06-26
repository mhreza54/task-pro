package com.taskpro.data

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*

interface SupabaseAuthService {

  @POST("auth/v1/otp")
  suspend fun signupOrSigninWithOtp(
    @Header("apikey") apiKey: String,
    @Body request: OtpRequest
  ): Response<Unit>

  @POST("auth/v1/verify")
  suspend fun verifyOtp(
    @Header("apikey") apiKey: String,
    @Body request: VerifyOtpRequest
  ): Response<AuthResponse>

  @POST("auth/v1/token")
  suspend fun refreshToken(
    @Header("apikey") apiKey: String,
    @Query("grant_type") grantType: String = "refresh_token",
    @Body request: RefreshTokenRequest
  ): Response<AuthResponse>

  @GET("auth/v1/user")
  suspend fun getCurrentUser(
    @Header("apikey") apiKey: String,
    @Header("Authorization") authorization: String
  ): Response<SupabaseUser>

  companion object {
    fun create(baseUrl: String): SupabaseAuthService {
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
        .create(SupabaseAuthService::class.java)
    }
  }
}

@JsonClass(generateAdapter = true)
data class OtpOptions(
  @Json(name = "email_redirect_to") val emailRedirectTo: String? = null
)

@JsonClass(generateAdapter = true)
data class OtpRequest(
  @Json(name = "email") val email: String,
  @Json(name = "create_user") val createUser: Boolean = true,
  @Json(name = "options") val options: OtpOptions? = null
)

@JsonClass(generateAdapter = true)
data class VerifyOtpRequest(
  @Json(name = "type") val type: String = "email",
  @Json(name = "email") val email: String,
  @Json(name = "token") val token: String
)

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(
  @Json(name = "refresh_token") val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
  @Json(name = "access_token") val accessToken: String,
  @Json(name = "token_type") val tokenType: String,
  @Json(name = "expires_in") val expiresIn: Long,
  @Json(name = "refresh_token") val refreshToken: String,
  @Json(name = "user") val user: SupabaseUser
)

@JsonClass(generateAdapter = true)
data class SupabaseUser(
  @Json(name = "id") val id: String,
  @Json(name = "email") val email: String? = null
)
