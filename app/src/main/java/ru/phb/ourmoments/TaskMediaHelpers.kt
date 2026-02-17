package ru.phb.ourmoments

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.abedelazizshe.lightcompressorlibrary.CompressionListener
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor
import com.abedelazizshe.lightcompressorlibrary.VideoQuality
import com.abedelazizshe.lightcompressorlibrary.config.Configuration
import com.abedelazizshe.lightcompressorlibrary.config.SaveLocation
import com.abedelazizshe.lightcompressorlibrary.config.SharedStorageConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

// ==========================================
// 1. ДИАЛОГ ЗАГРУЗКИ (UploadTaskDialog)
// ==========================================
@Composable
fun UploadTaskDialog(
    task: LoveTask,
    primaryColor: Color,
    onDismiss: () -> Unit,
    onUploadClick: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Favorite, null, tint = primaryColor, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("История №${task.id + 1}", fontSize = 14.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = task.description,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onUploadClick,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Добавить фото/видео")
                }
            }
        }
    }
}

// ==========================================
// 2. ФОНОВЫЙ ВОРКЕР (UploadWorker)
// ==========================================
class UploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getInt("taskId", -1)
        if (taskId == -1) return Result.failure()

        val sharedPrefs = applicationContext.getSharedPreferences("love_tasks", Context.MODE_PRIVATE)
        val uri = sharedPrefs.getString("task_${taskId}_uri", null) ?: return Result.failure()
        val date = sharedPrefs.getString("task_${taskId}_date", "") ?: ""
        val loc = sharedPrefs.getString("task_${taskId}_loc", "") ?: ""

        val tempTask = LoveTask(id = taskId, description = "", heightRatio = 1f).apply {
            completedUri = uri
            dateTaken = date
            location = loc
        }
        return try {
            SyncHelper.uploadTask(applicationContext, tempTask) {}
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

// ==========================================
// 3. УТИЛИТЫ ДЛЯ ВИДЕО (VideoUtils)
// ==========================================
sealed class CompressionStatus {
    data class Progress(val percent: Float) : CompressionStatus()
    data class Success(val compressedFile: File) : CompressionStatus()
    data class Error(val failureMessage: String) : CompressionStatus()
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPreview(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
        }
    }

    LaunchedEffect(uri) {
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        modifier = modifier
    )
}

object VideoCompressorHelper {
    fun compressVideo(context: Context, sourceUri: Uri): Flow<CompressionStatus> = callbackFlow {
        val fileName = "comp_${System.currentTimeMillis()}.mp4"

        VideoCompressor.start(
            context = context,
            uris = listOf(sourceUri),
            isStreamable = false,
            sharedStorageConfiguration = SharedStorageConfiguration(
                saveAt = SaveLocation.movies, // Сохраняем в галерею!
                subFolderName = "OurMoments"
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
        awaitClose { VideoCompressor.cancel() }
    }
}

// ==========================================
// 4. ЗАГРУЗЧИК В ГАЛЕРЕЮ (GalleryDownloader)
// ==========================================
object GalleryDownloader {
    suspend fun downloadAndSaveToGallery(
        context: Context,
        fileUrl: String,
        taskId: Int,
        isVideo: Boolean
    ): String? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(fileUrl).openConnection()
            val inputStream = connection.getInputStream()

            val resolver = context.contentResolver
            val extension = if (isVideo) "mp4" else "jpg"
            val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
            val fileName = "moment_${taskId}_${System.currentTimeMillis()}.$extension"

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val directory = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/OurMoments")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val collection = if (isVideo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val uri = resolver.insert(collection, contentValues)

            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    inputStream.copyTo(outputStream)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(it, contentValues, null, null)
                }

                return@withContext it.toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}