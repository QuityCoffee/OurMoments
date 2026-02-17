package ru.phb.ourmoments

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
// ВАЖНЫЙ ИМПОРТ ДЛЯ ПОТОКОВОЙ ПЕРЕДАЧИ (СТРИМИНГА):
import io.ktor.utils.io.jvm.javaio.toByteReadChannel

// Модель ответа от сервера
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ServerTask(
    val id: Int,
    val date_taken: String?,
    val description: String? = null,
    val location: String?,
    val media_url: String?,
    val media_type: String?
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class SyncResponse(val tasks: List<ServerTask> = emptyList()) {

}

object SyncHelper {

    // Основной клиент для легких запросов
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    // --- ОБНОВЛЕНИЕ ТЕКСТА (БЕЗ ФАЙЛА) ---
    suspend fun updateTaskDetails(task: LoveTask): Boolean {
        return try {
            val response = client.submitForm(
                url = AppConfig.apiUrl,
                formParameters = Parameters.build {
                    append("action", "upload")
                    append("id", task.id.toString())
                    append("date_taken", task.dateTaken)
                    append("location", task.location)
                }
            ) {
                headers { append(HttpHeaders.Authorization, "Bearer ${AppConfig.apiKey}") }
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- УДАЛЕНИЕ ---
    suspend fun deleteTask(id: Int): Boolean {
        return try {
            val response = client.submitForm(
                url = AppConfig.apiUrl,
                formParameters = Parameters.build {
                    append("action", "delete")
                    append("id", id.toString())
                }
            ) {
                headers { append(HttpHeaders.Authorization, "Bearer ${AppConfig.apiKey}") }
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- ЗАГРУЗКА (ТЯЖЕЛЫЕ ФАЙЛЫ + ПРОГРЕСС) ---
    suspend fun uploadTask(
        context: Context,
        task: LoveTask,
        onProgress: (Float) -> Unit
    ) {
        // Создаем отдельный клиент, чтобы задать ему "резиновые" таймауты
        val uploadClient = HttpClient(Android) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout) {
                requestTimeoutMillis = 3600000L // 1 час
                connectTimeoutMillis = 60000L   // 1 минута
                socketTimeoutMillis = 3600000L  // 1 час
            }
        }

        val uriStr = task.completedUri ?: ""
        val uri = Uri.parse(uriStr)

// 1. Пытаемся получить тип через систему (работает для оригиналов из галереи)
        var mimeType = context.contentResolver.getType(uri)

// 2. Если система вернула null (это наш сжатый файл с путем file://...mp4), смотрим на расширение в тексте
        if (mimeType == null) {
            mimeType = if (uriStr.lowercase().endsWith(".mp4")) {
                "video/mp4"
            } else {
                "image/jpeg"
            }
        }

// 3. Теперь точно знаем, видео это или нет
        val isVideo = mimeType.startsWith("video/")
        val ext = if (isVideo) "mp4" else "jpg"

// Точный размер файла для прогресс-бара
        val fileSize = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L

        try {
            uploadClient.post("${AppConfig.apiUrl}?action=upload") {
                // НЕ ЗАБЫВАЕМ АВТОРИЗАЦИЮ!
                headers { append(HttpHeaders.Authorization, "Bearer ${AppConfig.apiKey}") }

                setBody(MultiPartFormDataContent(
                    formData {
                        // Ktor требует строки для текстовых полей формы
                        append("id", task.id.toString())
                        append("date_taken", task.dateTaken)
                        append("location", task.location)

                        // ПОТОКОВАЯ ПЕРЕДАЧА: Отправляем файл, не забивая оперативную память
                        append(
                            "media",
                            ChannelProvider(size = fileSize) {
                                context.contentResolver.openInputStream(uri)!!.toByteReadChannel()
                            },
                            Headers.build {
                                append(HttpHeaders.ContentType, mimeType)
                                append(HttpHeaders.ContentDisposition, "filename=\"task_${task.id}.$ext\"")
                            }
                        )
                    }
                ))

                // ОТСЛЕЖИВАНИЕ БАЙТОВ ДЛЯ КАРТОЧКИ
                onUpload { bytesSentTotal, contentLength ->
                    val total = if (contentLength > 0) contentLength else fileSize
                    if (total > 0) {
                        val progress = bytesSentTotal.toFloat() / total.toFloat()
                        onProgress(progress)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e // Важно пробросить ошибку, чтобы интерфейс её поймал
        } finally {
            uploadClient.close() // Закрываем клиент, освобождаем ресурсы
        }
    }

    // --- СИНХРОНИЗАЦИЯ (СКАЧИВАНИЕ БД) ---
    suspend fun fetchServerData(): List<ServerTask> {
        return try {
            val response: SyncResponse = client.get(AppConfig.apiUrl) {
                url { parameters.append("action", "sync_down") }
                headers { append(HttpHeaders.Authorization, "Bearer ${AppConfig.apiKey}") }
            }.body()
            response.tasks
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}