package ru.phb.ourmoments

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
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
import kotlin.random.Random

@Composable
fun ChallengeScreen() {
    var isSecretMode by remember { mutableStateOf(true) }

    val primaryColor by animateColorAsState(if (isSecretMode) Color(0xFF4CAF50) else Color(0xFFE91E63), label = "c")
    val secondaryColor by animateColorAsState(if (isSecretMode) Color(0xFFE8F5E9) else Color(0xFFFFF0F5), label = "b")
    var sliderValue by remember { mutableStateOf(2f) }
    val columnCount by animateIntAsState(targetValue = sliderValue.toInt(), animationSpec = spring(stiffness = Spring.StiffnessLow), label = "col")
    val mainIcon = if (isSecretMode) Icons.Default.Add else Icons.Default.Favorite
    val titleText = if (isSecretMode) "Менеджер задач" else "Наш Челлендж ❤️"

    var selectedTaskForDetail by remember { mutableStateOf<LoveTask?>(null) }

    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("love_tasks", Context.MODE_PRIVATE) }

    val myDescriptions = listOf(
        "Сделай селфи с самым смешным выражением лица",
        "Сфотографируй то, что напоминает обо мне",
        "Наш романтический ужин",
        "Поцелуй в щечку"
    )

    val initialTasks = remember {
        List(200) { index ->
            val randomRatio = Random.nextDouble(0.7, 1.5).toFloat()
            val text = if (index < myDescriptions.size) myDescriptions[index] else "Секретное задание №${index + 1}"

            LoveTask(id = index, description = text, heightRatio = randomRatio).apply {
                completedUri = sharedPrefs.getString("task_${index}_uri", null)
                dateTaken = sharedPrefs.getString("task_${index}_date", "") ?: ""
                location = sharedPrefs.getString("task_${index}_loc", "") ?: ""
            }
        }
    }
    val tasks = remember { mutableStateListOf<LoveTask>().apply { addAll(initialTasks) } }
    var currentPickingTaskId by remember { mutableStateOf<Int?>(null) }

    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && currentPickingTaskId != null) {
            val flag = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flag)

            val (exifDate, exifLoc) = ExifHelper.getPhotoDetails(context, uri)
            val index = tasks.indexOfFirst { it.id == currentPickingTaskId }
            if (index != -1) {
                tasks[index] = tasks[index].copy(completedUri = uri.toString(), dateTaken = exifDate, location = exifLoc)
                sharedPrefs.edit()
                    .putString("task_${currentPickingTaskId}_uri", uri.toString())
                    .putString("task_${currentPickingTaskId}_date", exifDate)
                    .putString("task_${currentPickingTaskId}_loc", exifLoc)
                    .apply()
            }
        }
        currentPickingTaskId = null
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = primaryColor, background = secondaryColor)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = secondaryColor,
                bottomBar = {
                    Surface(tonalElevation = 8.dp, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), color = Color.White) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Размер фото", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
                            Slider(value = sliderValue, onValueChange = { sliderValue = it }, valueRange = 1f..4f, steps = 2, colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor))
                        }
                    }
                }
            ) { paddingValues ->
                Surface(modifier = Modifier.fillMaxSize().padding(paddingValues), color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { isSecretMode = !isSecretMode }) {
                            Crossfade(targetState = titleText, label = "title") { text ->
                                Text(text = text, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = primaryColor, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                            }
                        }

                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(columnCount),
                            verticalItemSpacing = 8.dp,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier.fillMaxSize().animateContentSize()
                        ) {
                            items(tasks, key = { it.id }) { task ->
                                TaskCard(
                                    task = task,
                                    isSecretMode = isSecretMode,
                                    icon = mainIcon,
                                    primaryColor = primaryColor,
                                    onClick = {
                                        if (task.completedUri != null) {
                                            selectedTaskForDetail = task
                                        } else {
                                            currentPickingTaskId = task.id
                                            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (selectedTaskForDetail != null) {
                val task = selectedTaskForDetail!!
                PhotoDetailScreen(
                    task = task,
                    isSecretMode = isSecretMode,
                    onDismiss = { selectedTaskForDetail = null },
                    onSaveDetails = { newDate, newLoc ->
                        val index = tasks.indexOfFirst { it.id == task.id }
                        if (index != -1) {
                            tasks[index] = tasks[index].copy(dateTaken = newDate, location = newLoc)
                            sharedPrefs.edit()
                                .putString("task_${task.id}_date", newDate)
                                .putString("task_${task.id}_loc", newLoc)
                                .apply()
                        }
                        selectedTaskForDetail = null
                    },
                    onDelete = {
                        val index = tasks.indexOfFirst { it.id == task.id }
                        if (index != -1) {
                            tasks[index] = tasks[index].copy(completedUri = null, dateTaken = "", location = "")
                            sharedPrefs.edit()
                                .remove("task_${task.id}_uri")
                                .remove("task_${task.id}_date")
                                .remove("task_${task.id}_loc")
                                .apply()
                        }
                        selectedTaskForDetail = null
                    }
                )
            }
        }
    }
}