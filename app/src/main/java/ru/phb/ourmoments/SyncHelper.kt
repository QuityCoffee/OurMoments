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
import io.ktor.utils.io.jvm.javaio.toByteReadChannel

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
data class SyncResponse(val tasks: List<ServerTask>)

object SyncHelper {


    private const val BASE_URL = "http://192.168.137.46:5000/"

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    // --- ОБНОВЛЕНИЕ ТЕКСТА (Пока остается на старом API или ждет реализации в C#) ---
    suspend fun updateTaskDetails(task: LoveTask): Boolean {
        return try {
            val response = client.submitForm(
                url = "${BASE_URL}/api/upload/details", // Путь для будущего контроллера
                formParameters = Parameters.build {
                    append("id", task.id.toString())
                    append("date_taken", task.dateTaken)
                    append("location", task.location)
                }
            )
            response.status.isSuccess()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- ЗАГРУЗКА ВИДЕО (ТЯЖЕЛЫЕ ФАЙЛЫ + ПРОГРЕСС) ---
    suspend fun uploadTask(
        context: Context,
        task: LoveTask,
        onProgress: (Float) -> Unit
    ) {
        val uploadClient = HttpClient(Android) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout) {
                requestTimeoutMillis = 3600000L // 1 час (для очень тяжелых видео)
                connectTimeoutMillis = 60000L   // 1 минута
                socketTimeoutMillis = 3600000L
            }
        }

        val uriStr = task.completedUri ?: ""
        val uri = Uri.parse(uriStr)

        // Определяем тип файла
        var mimeType = context.contentResolver.getType(uri)
        if (mimeType == null) {
            mimeType = if (uriStr.lowercase().endsWith(".mp4")) "video/mp4" else "image/jpeg"
        }

        val isVideo = mimeType.startsWith("video/")
        val ext = if (isVideo) "mp4" else "jpg"

        // Получаем точный размер файла
        val fileSize = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L

        try {
            // Путь к твоему новому UploadController в .NET
            uploadClient.post("${BASE_URL}/api/upload/video") {
                setBody(MultiPartFormDataContent(
                    formData {
                        append("id", task.id.toString())
                        append("date_taken", task.dateTaken)
                        append("location", task.location)

                        // "video" - это имя поля, которое ожидает наш C# код
                        append(
                            "video",
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
            throw e
        } finally {
            uploadClient.close()
        }
    }

    // --- СИНХРОНИЗАЦИЯ (СКАЧИВАНИЕ СПИСКА) ---
    suspend fun fetchServerData(): List<ServerTask> {
        return try {
            val response: SyncResponse = client.get("${BASE_URL}/api/upload/sync").body()
            response.tasks
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}