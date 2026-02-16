package ru.phb.ourmoments

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import java.io.File

class MainViewModel(private val api: OurMomentsApi) : ViewModel() {

    fun startProcessing(context: Context, uri: Uri) {
        viewModelScope.launch {
            VideoCompressorHelper.compressVideo(context, uri).collect { status ->
                when (status) {
                    is CompressionStatus.Success -> {
                        uploadFileToServer(status.compressedFile)
                    }
                    is CompressionStatus.Progress -> {
                        Log.d("OurMoments", "Сжатие: ${(status.percent * 100).toInt()}%")
                    }
                    is CompressionStatus.Error -> {
                        Log.e("OurMoments", "Ошибка сжатия: ${status.failureMessage}")
                    }
                }
            }
        }
    }

    private fun uploadFileToServer(file: File) {
        viewModelScope.launch {
            // Используем наш FileRequestBody для потоковой передачи
            val requestFile = FileRequestBody(file, "video/mp4") { progress ->
                Log.d("OurMoments", "Загрузка на сервер: ${(progress * 100).toInt()}%")
            }

            val body = MultipartBody.Part.createFormData("video", file.name, requestFile)

            try {
                val response = api.uploadVideo(body)
                if (response.isSuccessful) {
                    Log.d("OurMoments", "Готово! Сохранено на G: ${response.body()?.path}")
                } else {
                    Log.e("OurMoments", "Ошибка сервера: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("OurMoments", "Ошибка сети: ${e.message}")
            }
        }
    }
}