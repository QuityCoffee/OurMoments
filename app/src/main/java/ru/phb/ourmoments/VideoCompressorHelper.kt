package ru.phb.ourmoments

import android.content.Context
import android.net.Uri
import com.abedelazizshe.lightcompressorlibrary.CompressionListener
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor
import com.abedelazizshe.lightcompressorlibrary.VideoQuality
import com.abedelazizshe.lightcompressorlibrary.config.AppSpecificStorageConfiguration
import com.abedelazizshe.lightcompressorlibrary.config.Configuration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

sealed class CompressionStatus {
    data class Progress(val percent: Float) : CompressionStatus()
    data class Success(val compressedFile: File) : CompressionStatus()
    data class Error(val failureMessage: String) : CompressionStatus()
}

object VideoCompressorHelper {

    fun compressVideo(context: Context, sourceUri: Uri): Flow<CompressionStatus> = callbackFlow {
        // Задаем имя будущего файла
        val fileName = "comp_${System.currentTimeMillis()}.mp4"

        VideoCompressor.start(
            context = context,
            uris = listOf(sourceUri), // Библиотека теперь требует СПИСОК (List)
            isStreamable = false,
            // Сохраняем в кэш приложения, чтобы не мусорить в галерее телефона
            appSpecificStorageConfiguration = AppSpecificStorageConfiguration(
                subFolderName = "OurMomentsCache"
            ),
            configureWith = Configuration(
                quality = VideoQuality.MEDIUM,
                videoNames = listOf(fileName), // Имена тоже передаются списком
                isMinBitrateCheckEnabled = false,
                videoBitrateInMbps = 3, // Жмем мощно!
                disableAudio = false,
                keepOriginalResolution = false,
                videoHeight = 1080.0,
                videoWidth = 1920.0
            ),
            listener = object : CompressionListener {
                // Во всех методах теперь есть параметр index: Int
                override fun onProgress(index: Int, percent: Float) {
                    trySend(CompressionStatus.Progress(percent / 100f))
                }

                override fun onStart(index: Int) {}

                override fun onSuccess(index: Int, size: Long, path: String?) {
                    if (path != null) {
                        trySend(CompressionStatus.Success(File(path)))
                    } else {
                        trySend(CompressionStatus.Error("Ошибка: путь к файлу пуст"))
                    }
                    close()
                }

                override fun onFailure(index: Int, failureMessage: String) {
                    trySend(CompressionStatus.Error(failureMessage))
                    close()
                }

                override fun onCancelled(index: Int) {
                    close()
                }
            }
        )
        // Если корутину отменят, останавливаем сжатие
        awaitClose { VideoCompressor.cancel() }
    }
}