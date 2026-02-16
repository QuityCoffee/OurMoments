package ru.phb.ourmoments

import android.content.Context
import android.net.Uri
import android.util.Log
import com.abedelazizshe.lightcompressorlibrary.CompressionListener
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor
import com.abedelazizshe.lightcompressorlibrary.VideoQuality
import com.abedelazizshe.lightcompressorlibrary.config.AppSpecificStorageConfiguration
import com.abedelazizshe.lightcompressorlibrary.config.Configuration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MultipartBody
import java.io.File

sealed class CompressionStatus {
    data class Progress(val percent: Float) : CompressionStatus()
    data class Success(val compressedFile: File) : CompressionStatus()
    data class Error(val failureMessage: String) : CompressionStatus()
}

object VideoCompressorHelper {

    fun compressVideo(context: Context, sourceUri: Uri): Flow<CompressionStatus> = callbackFlow {
        val fileName = "comp_${System.currentTimeMillis()}.mp4"

        VideoCompressor.start(
            context = context,
            uris = listOf(sourceUri),
            isStreamable = false,
            appSpecificStorageConfiguration = AppSpecificStorageConfiguration(
                subFolderName = "OurMomentsCache"
            ),
            configureWith = Configuration(
                quality = VideoQuality.MEDIUM,
                videoNames = listOf(fileName),
                isMinBitrateCheckEnabled = false,
                videoBitrateInMbps = 3,
                disableAudio = false,
                keepOriginalResolution = false,
                videoHeight = 1080.0,
                videoWidth = 1920.0
            ),
            listener = object : CompressionListener {
                override fun onProgress(index: Int, percent: Float) {
                    trySend(CompressionStatus.Progress(percent / 100f))
                }

                override fun onStart(index: Int) {}

                override fun onSuccess(index: Int, size: Long, path: String?) {
                    if (path != null) {
                        // ПЕРЕДАЕМ ГОТОВЫЙ ФАЙЛ ДАЛЬШЕ
                        trySend(CompressionStatus.Success(File(path)))
                    } else {
                        trySend(CompressionStatus.Error("Путь к файлу пуст"))
                    }
                    close() // Закрываем поток после успеха
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
        awaitClose { VideoCompressor.cancel() }
    }
}