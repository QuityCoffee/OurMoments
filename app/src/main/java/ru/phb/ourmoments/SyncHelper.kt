package ru.phb.ourmoments

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class ServerTask(
    val id: Int,
    val date_taken: String?,
    val description: String? = null,
    val location: String?,
    val media_url: String?,
    val media_type: String?,
    val category: String? = null // <--- И ВОТ ЭТА СТРОКА!
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class SyncResponse(val tasks: List<ServerTask> = emptyList())

@OptIn(InternalSerializationApi::class)
object SyncHelper {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun fetchServerData(userName: String): List<ServerTask> {
        return try {
            val response: SyncResponse = client.get(AppConfig.apiUrl) {
                url {
                    parameters.append("action", "sync_down")
                    parameters.append("user", userName)
                }
                headers { append(HttpHeaders.Authorization, "Bearer ${AppConfig.apiKey}") }
            }.body()
            response.tasks
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun uploadTask(context: Context, task: LoveTask, userName: String, onProgress: (Float) -> Unit) {
        val uploadClient = HttpClient(Android) {
            engine {
                connectTimeout = 100_000
                socketTimeout = 100_000
            }
        }
        try {
            val uri = Uri.parse(task.completedUri)
            val isVideo = task.getIsVideo(context)
            val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
            val ext = if (isVideo) "mp4" else "jpg"

            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: throw Exception("Не удалось открыть файл")
            val fileSize = inputStream.available().toLong()

            uploadClient.post(AppConfig.apiUrl) {
                url { parameters.append("action", "upload") }
                headers { append(HttpHeaders.Authorization, "Bearer ${AppConfig.apiKey}") }
                setBody(MultiPartFormDataContent(
                    formData {
                        append("id", task.id.toString())
                        append("date_taken", task.dateTaken)
                        append("location", task.location)
                        append("media_type", if (isVideo) "video" else "image")
                        append("user", userName)

                        // ИСПРАВЛЕНИЕ: Заменили InputProvider на ChannelProvider
                        append("media", ChannelProvider(size = fileSize) {
                            contentResolver.openInputStream(uri)!!.toByteReadChannel()
                        }, Headers.build {
                            append(HttpHeaders.ContentType, mimeType)
                            append(HttpHeaders.ContentDisposition, "filename=\"task_${task.id}.$ext\"")
                        })
                    }
                ))
                onUpload { bytesSentTotal, contentLength ->
                    val total = if (contentLength > 0) contentLength else fileSize
                    if (total > 0) {
                        onProgress(bytesSentTotal.toFloat() / total.toFloat())
                    }
                }
            }
        } finally {
            uploadClient.close()
        }
    }

    suspend fun updateTaskDetails(task: LoveTask, userName: String) {
        try {
            client.post(AppConfig.apiUrl) {
                url { parameters.append("action", "update_details") }
                headers { append(HttpHeaders.Authorization, "Bearer ${AppConfig.apiKey}") }
                setBody(MultiPartFormDataContent(formData {
                    append("id", task.id.toString())
                    append("date_taken", task.dateTaken)
                    append("location", task.location)
                    append("user", userName)
                }))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteTask(taskId: Int, userName: String) {
        try {
            client.post(AppConfig.apiUrl) {
                url { parameters.append("action", "delete") }
                headers { append(HttpHeaders.Authorization, "Bearer ${AppConfig.apiKey}") }
                setBody(MultiPartFormDataContent(formData {
                    append("id", taskId.toString())
                    append("user", userName)
                }))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}