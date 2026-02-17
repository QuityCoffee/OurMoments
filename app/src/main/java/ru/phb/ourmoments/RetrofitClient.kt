package ru.phb.ourmoments

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


object RetrofitClient {
    // ВАЖНО: Подставь сюда IP своей Ubuntu (172.x.x.x), который мы нашли через ip -4 a show eth0
    private const val BASE_URL = "http://192.168.137.46:5000/"

    private val okHttpClient = OkHttpClient.Builder()

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