package ru.phb.ourmoments

import android.content.Context
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.InputStream

// --- 1. МОДЕЛЬ ДАННЫХ ---
data class LoveTask(
    val id: Int,
    val downloadStatus: String = "",
    val description: String,
    val heightRatio: Float = 1.0f,
    var completedUri: String? = null,
    var dateTaken: String = "",
    var location: String = "",
    var category: String = "",
    val isUploading: Boolean = false,
    val isCompressing: Boolean = false,
    val uploadProgress: Float = 0f,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f
) {
    fun getIsVideo(context: Context): Boolean {
        val uriString = completedUri ?: return false
        val uri = Uri.parse(uriString)
        val type = context.contentResolver.getType(uri)
        if (type?.startsWith("video") == true) return true
        val lower = uriString.lowercase()
        return lower.contains("video") || lower.endsWith(".mp4") || lower.endsWith(".mov")
    }
}

// --- 2. ХЕЛПЕР ДЛЯ EXIF ---
object ExifHelper {
    fun getPhotoDetails(context: Context, uri: Uri): Pair<String, String> {
        var date = ""
        var location = ""
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val exif = androidx.exifinterface.media.ExifInterface(inputStream)
                val dateTag = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME)
                if (!dateTag.isNullOrEmpty()) {
                    date = dateTag.substringBefore(" ").replace(":", "-")
                }
                val latLong = exif.latLong
                if (latLong != null) location = "Геометка найдена"
                inputStream.close()
            }
        } catch (e: Exception) { e.printStackTrace() }
        return Pair(date, location)
    }
}

// --- 3. UI-КОМПОНЕНТ КАРТОЧКИ ---
@Composable
fun TaskCard(
    task: LoveTask,
    icon: ImageVector,
    primaryColor: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val isCompleted = task.completedUri != null
    val isVideo = task.getIsVideo(context)

    val cardBgColor = if (isCompleted) Color.White else Color(0xFFFCE4EC)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f / task.heightRatio)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            Crossfade(targetState = isCompleted, animationSpec = tween(500), label = "cardFade") { completed ->
                if (completed) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isVideo && !task.isDownloading) {
                            VideoPreview(uri = task.completedUri!!, modifier = Modifier.fillMaxSize())
                        } else if (!task.isDownloading) {
                            AsyncImage(
                                model = task.completedUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Бейджик Категории (Левый верхний угол)
                        if (task.category.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(primaryColor.copy(alpha = 0.8f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(text = task.category, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Бейджик Даты (Правый нижний угол)
                        if (task.dateTaken.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(text = task.dateTaken.take(10), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Если есть категория у пустого задания — показываем
                        if (task.category.isNotBlank()) {
                            Text(
                                text = task.category.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        Icon(icon, null, tint = primaryColor, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = task.description,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = Color.Black.copy(alpha = 0.7f),
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // ИНДИКАТОР ПРОГРЕССА
            if (task.isUploading || task.isCompressing || task.isDownloading) {
                val currentProgress = if (task.isDownloading) task.downloadProgress else task.uploadProgress
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                        CircularProgressIndicator(progress = { currentProgress }, color = Color.White, strokeWidth = 4.dp, modifier = Modifier.size(45.dp))
                        Spacer(modifier = Modifier.height(12.dp))

                        val statusText = when {
                            task.isCompressing -> "Сжатие видео..."
                            task.isDownloading -> task.downloadStatus.ifBlank { "Загрузка..." } // <-- ДИНАМИЧЕСКИЙ СТАТУС
                            else -> "Загрузка..."
                        }

                        Text(
                            text = statusText,
                            color = Color.White,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 14.sp
                        )
                        Text(text = "${(currentProgress * 100).toInt()}%", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}