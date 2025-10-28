package org.eshragh.kartext.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.eshragh.kartext.models.CreateLeaveRequest
import org.eshragh.kartext.models.LoginRequest
import org.eshragh.kartext.models.LoginResponse
import org.eshragh.kartext.models.Record
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ApiService {

    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("api/logs")
    suspend fun getLogs(@Header("Authorization") token: String): Response<List<Record>>

    @POST("api/logs")
    suspend fun addLog(@Header("Authorization") token: String, @Body record: Record): Response<Record>

    @PUT("api/logs/{id}")
    suspend fun updateLog(
        @Header("Authorization") token: String,
        @Path("id") id: String,
        @Body record: Record
    ): Response<Record>

    @DELETE("api/logs/{id}")
    suspend fun deleteLog(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): Response<Unit>

    @POST("api/leaves")
    suspend fun requestLeave(@Header("Authorization") token: String, @Body request: CreateLeaveRequest): Response<Unit>

}

object RetrofitClient {
    private const val BASE_URL = "https://kartext.eshragh.org/"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient)
            .build()

        retrofit.create(ApiService::class.java)
    }
}