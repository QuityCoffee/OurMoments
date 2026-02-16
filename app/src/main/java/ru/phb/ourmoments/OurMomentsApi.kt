package ru.phb.ourmoments

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface OurMomentsApi {
    @Multipart
    @POST("api/upload/video") // Путь должен совпадать с [Route("api/[controller]")] в C#
    suspend fun uploadVideo(
        @Part video: MultipartBody.Part
    ): Response<UploadResponse>
}

// Класс ответа от сервера (C# возвращает json с этими полями)
data class UploadResponse(
    val message: String,
    val path: String
)