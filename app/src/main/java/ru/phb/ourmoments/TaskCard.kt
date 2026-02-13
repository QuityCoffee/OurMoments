package ru.phb.ourmoments

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun TaskCard(
    task: LoveTask,
    isSecretMode: Boolean,
    icon: ImageVector,
    primaryColor: Color,
    onClick: () -> Unit
) {
    val isCompleted = task.completedUri != null

    // Анимация уменьшения при нажатии (эффект кнопки)
    var isPressed by remember { mutableStateOf(false) }
    val scaleEffect by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "scale")

    // Цвет фона: белый для готовых, серый для скрытых, розовый для активных заданий
    val cardBgColor = if (isCompleted) {
        Color.White
    } else if (isSecretMode) {
        Color(0xFFF5F5F5)
    } else {
        Color(0xFFFFEBEE)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f / task.heightRatio)
            .scale(scaleEffect)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = true
                onClick()
                isPressed = false
            }
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Плавное переключение между "Пустой карточкой" и "Контентом"
            Crossfade(
                targetState = isCompleted,
                animationSpec = tween(500),
                label = "content_fade"
            ) { completed ->
                if (completed) {
                    // Если это видео — запускаем превью, если фото — обычную картинку
                    if (task.isVideo) {
                        VideoPreivew(
                            uri = task.completedUri!!,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = task.completedUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    // Содержимое пустой карточки (Иконка + Текст)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(32.dp)
                        )

                        // Показываем текст, только если карточка не слишком "сплюснута"
                        if (task.heightRatio > 0.6) {
                            Spacer(modifier = Modifier.height(6.dp))

                            // Выбор текста в зависимости от режима конспирации
                            val textToShow = if (isSecretMode) {
                                "System Log ${task.id + 400}"
                            } else {
                                task.description
                            }

                            Text(
                                text = textToShow,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 14.sp,
                                color = Color.Black.copy(alpha = 0.7f),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}