package com.ceob68.sismoalerta.data.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    
    private val gson: Gson = GsonBuilder()
        .setLenient()
        .create()
    
    private val httpClient: OkHttpClient
        get() {
            val builder = OkHttpClient.Builder()
            
            // Logging interceptor
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(loggingInterceptor)
            
            // Timeouts
            builder.connectTimeout(30, TimeUnit.SECONDS)
            builder.readTimeout(30, TimeUnit.SECONDS)
            builder.writeTimeout(30, TimeUnit.SECONDS)
            
            return builder.build()
        }
    
    val sismoApiService: SismoApiService
        get() = Retrofit.Builder()
            .baseUrl(SismoApiService.BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(SismoApiService::class.java)
}