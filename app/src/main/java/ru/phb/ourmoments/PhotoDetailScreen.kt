package ru.phb.ourmoments

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

@OptIn(UnstableApi::class)
@Composable
fun PhotoDetailScreen(
    task: LoveTask,
    isSecretMode: Boolean,
    onDismiss: () -> Unit,
    onSaveDetails: (date: String, location: String) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var dateText by remember { mutableStateOf(task.dateTaken) }
    var locationText by remember { mutableStateOf(task.location) }

    // Настройки для конспирации
    val titleText = if (isSecretMode) "System Log #${task.id + 400}" else "Файл №${task.id + 1}"
    val descText = if (isSecretMode) "Error: Data corruption. Check logs." else task.description
    val titleColor = if (isSecretMode) Color(0xFF4CAF50) else Color(0xFFE91E63)

    // Инициализируем плеер для видео (будет работать только если это видео)
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(task.completedUri ?: ""))
            prepare()
            playWhenReady = true // Автозапуск со звуком
        }
    }

    // Освобождаем память при закрытии экрана
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        // --- КОНТЕНТ (ФОТО ИЛИ ВИДЕО) ---
        if (task.isVideo) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        player = exoPlayer
                        useController = true // Показываем кнопки управления (пауза, громкость)
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 280.dp)
            )
        } else {
            AsyncImage(
                model = task.completedUri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 280.dp)
            )
        }

        // Кнопка закрыть
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }

        // Нижняя панель
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(24.dp)
        ) {
            Text(text = titleText, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = titleColor)
            Text(text = descText, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp), color = Color.Black)

            OutlinedTextField(
                value = dateText,
                onValueChange = { dateText = it },
                label = { Text(if (isSecretMode) "TS-Value" else "Когда это было?") },
                leadingIcon = { Icon(Icons.Default.DateRange, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = locationText,
                onValueChange = { locationText = it },
                label = { Text(if (isSecretMode) "Loc-Source" else "Где это было?") },
                leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = if (isSecretMode) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)),
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Delete, null, tint = Color.Red)
                    Text("Удалить", color = Color.Red, modifier = Modifier.padding(start = 4.dp))
                }

                Button(
                    onClick = { onSaveDetails(dateText, locationText) },
                    colors = ButtonDefaults.buttonColors(containerColor = titleColor),
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Сохранить")
                }
            }
        }
    }
}