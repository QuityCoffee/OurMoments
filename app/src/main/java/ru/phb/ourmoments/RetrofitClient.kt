package ru.phb.ourmoments

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

:GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // ВАЖНО: Подставь сюда IP своей Ubuntu (172.x.x.x), который мы нашли через ip -4 a show eth0
    private const val BASE_URL = "http://192.168.137.46:5000/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.MINUTES) // Время на установку связи
        .writeTimeout(30, TimeUnit.MINUTES)   // Время на передачу файла (для 7ГБ лучше побольше)
        .readTimeout(1, TimeUnit.MINUTES)
        .build()

    val api: OurMomentsApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttpClient)
            .build()
            .create(OurMomentsApi::class.java)
    }
}