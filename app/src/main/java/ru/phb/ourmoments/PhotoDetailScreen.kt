package ru.phb.ourmoments
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

@OptIn(UnstableApi::class)
@Composable
fun PhotoDetailScreen(
    task: LoveTask,
    onDismiss: () -> Unit,
    onSaveDetails: (date: String, location: String) -> Unit,
    onDelete: (deleteFromServer: Boolean) -> Unit
) {
    val context = LocalContext.current
    var isPanelVisible by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var dateText by remember { mutableStateOf(task.dateTaken) }
    var locationText by remember { mutableStateOf(task.location) }

    val isVideo = task.getIsVideo(context)

    // Плеер для полного экрана (со звуком)
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            if (task.completedUri != null) {
                setMediaItem(MediaItem.fromUri(task.completedUri!!))
                prepare()
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ONE
            }
        }
    }

    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                isPanelVisible = !isPanelVisible
            }
    ) {
        // 1. Контент
        if (isVideo) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = task.completedUri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. Кнопка Закрыть
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(Icons.Default.Close, "Close", tint = Color.White)
        }

        // 3. Кнопка Редактировать (если панель скрыта)
        AnimatedVisibility(
            visible = !isPanelVisible,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(24.dp)
        ) {
            FloatingActionButton(
                onClick = { isPanelVisible = true },
                containerColor = Color(0xFFE91E63),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Edit, "Edit")
            }
        }

        // 4. Панель редактирования
        AnimatedVisibility(
            visible = isPanelVisible,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)

        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(24.dp)

                    .navigationBarsPadding() // Отступ для трех кнопок навигации
                    .imePadding()            // Отступ для выезжающей клавиатуры

                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {} // Перехват клика
            ) {
                Text("Фото №${task.id + 1}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE91E63))
                Text(task.description, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))

                OutlinedTextField(value = dateText, onValueChange = { dateText = it }, label = { Text("Дата") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = locationText, onValueChange = { locationText = it }, label = { Text("Место") }, modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(16.dp))

                Row {
                    Button(
                        onClick = { showDeleteDialog = true }, // Открываем диалог!
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Удалить", color = Color.Red)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(onClick = { onSaveDetails(dateText, locationText); isPanelVisible = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)), modifier = Modifier.weight(1f)) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
// --- ДИАЛОГ ПОДТВЕРЖДЕНИЯ УДАЛЕНИЯ ---
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удаление воспоминания", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Выберите, откуда удалить это задание:")
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            showDeleteDialog = false

                            onDelete(false) // Только локально
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5F5F5), contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Только из приложения")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            showDeleteDialog = false
                            onDelete(true) // Отовсюду
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE), contentColor = Color.Red),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("С приложения и с сервера")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Отмена", color = Color.Gray)
                }
            }
        )
    }
}