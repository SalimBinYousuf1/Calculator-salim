package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class FrankfurterRatesResponse(
    val amount: Double? = 1.0,
    val base: String? = "EUR",
    val date: String? = "",
    val rates: Map<String, Double> = emptyMap()
)

interface FrankfurterApiService {
    @GET("v2/rates")
    suspend fun getLatestRates(
        @Query("base") base: String = "EUR"
    ): FrankfurterRatesResponse

    @GET("v2/currencies")
    suspend fun getCurrencies(): Map<String, String>
}

object FrankfurterClient {
    private const val BASE_URL = "https://api.frankfurter.dev/"

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    val apiService: FrankfurterApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FrankfurterApiService::class.java)
    }
}
