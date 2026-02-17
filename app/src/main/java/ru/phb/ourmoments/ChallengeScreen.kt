package ru.phb.ourmoments

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChallengeScreen() {
    val primaryColor = Color(0xFFE91E63)
    val secondaryColor = Color(0xFFFFF0F5)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sharedPrefs = remember { context.getSharedPreferences("love_tasks", Context.MODE_PRIVATE) }

    // --- СОСТОЯНИЕ UI ---
    var gridScale by remember { mutableFloatStateOf(1.8f) }
    val animatedColumns by animateFloatAsState(
        targetValue = gridScale,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "cols"
    )

    var lastZoomTime by remember { mutableLongStateOf(0L) }

    // Навигация и диалоги
    var showPager by remember { mutableStateOf(false) }
    var initialPage by remember { mutableIntStateOf(0) }
    var taskForUploadDialog by remember { mutableStateOf<LoveTask?>(null) }
    var currentPickingTaskId by remember { mutableStateOf<Int?>(null) }

    // Поиск
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Синхронизация и загрузка
    var isSyncing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    // Список заданий
    val tasks = remember { mutableStateListOf<LoveTask>() }

    // --- 1. ЗАГРУЗКА ДАННЫХ ПРИ СТАРТЕ ---
    LaunchedEffect(Unit) {
        isLoading = true
        val serverTasks = SyncHelper.fetchServerData()

        if (serverTasks.isNotEmpty()) {
            val mappedTasks = serverTasks.map { sTask ->
                val randomRatio = Random.nextDouble(0.6, 2.0).toFloat()
                LoveTask(
                    id = sTask.id,
                    description = sTask.description ?: "Секретное задание №${sTask.id + 1}",
                    heightRatio = randomRatio
                ).apply {
                    completedUri = sTask.media_url
                    dateTaken = sTask.date_taken ?: ""
                    location = sTask.location ?: ""
                }
            }
            tasks.clear()
            tasks.addAll(mappedTasks)
        }
        isLoading = false
    }

// --- 2. ЛАУНЧЕРЫ (Выбор фото/видео) ---
    val photoPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) { originalUri ->
        if (originalUri != null && currentPickingTaskId != null) {
            val flag = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(originalUri, flag)
            val (exifDate, exifLoc) = try { ExifHelper.getPhotoDetails(context, originalUri) } catch (e: Exception) { "" to "" }

            val index = tasks.indexOfFirst { it.id == currentPickingTaskId }
            if (index != -1) {
                val mimeType = context.contentResolver.getType(originalUri) ?: ""
                val isVideo = mimeType.startsWith("video/")

                // Базовый объект задачи
                var taskToProcess = tasks[index].copy(
                    completedUri = originalUri.toString(), // Изначально сохраняем оригинал
                    dateTaken = exifDate,
                    location = exifLoc
                )
                // Сохраняем метаданные оригинала локально
                sharedPrefs.edit()
                    .putString("task_${currentPickingTaskId}_uri", originalUri.toString())
                    .putString("task_${currentPickingTaskId}_date", exifDate)
                    .putString("task_${currentPickingTaskId}_loc", exifLoc)
                    .apply()

                scope.launch(Dispatchers.Main) {
                    if (isVideo) {
                        // --- ЭТАП 1: СЖАТИЕ ВИДЕО ---
                        Toast.makeText(context, "🎬 Начинаю подготовку видео...", Toast.LENGTH_SHORT).show()
                        var compressedFile: java.io.File? = null

                        VideoCompressorHelper.compressVideo(context, originalUri).collect { status ->
                            when (status) {
                                is CompressionStatus.Progress -> {
                                    // Обновляем UI: статус "Сжатие" и прогресс
                                    val idx = tasks.indexOfFirst { it.id == taskToProcess.id }
                                    if (idx != -1) tasks[idx] = tasks[idx].copy(isCompressing = true, isUploading = false, uploadProgress = status.percent)
                                }
                                is CompressionStatus.Success -> {
                                    compressedFile = status.compressedFile
                                    // Видео сжато! Подменяем URI в задаче на путь к сжатому файлу
                                    taskToProcess = taskToProcess.copy(completedUri = android.net.Uri.fromFile(compressedFile).toString())
                                }
                                is CompressionStatus.Error -> {
                                    Toast.makeText(context, "Ошибка сжатия: ${status.failureMessage}", Toast.LENGTH_LONG).show()
                                    // Сбрасываем индикаторы
                                    val idx = tasks.indexOfFirst { it.id == taskToProcess.id }
                                    if (idx != -1) tasks[idx] = tasks[idx].copy(isCompressing = false, isUploading = false)
                                    return@collect // Прерываем процесс
                                }
                            }
                        }

                        // Если сжатие не удалось и мы вышли из collect, дальше не идем
                        if (compressedFile == null) return@launch

                        // --- ЭТАП 2: ЗАГРУЗКА СЖАТОГО ВИДЕО ---
                        uploadWithProgress(context, taskToProcess, tasks, scope) {
                            // После успешной загрузки удаляем временный сжатый файл
                            compressedFile.delete()
                        }

                    } else {
                        // --- ЭТО ФОТО: Сразу грузим ---
                        uploadWithProgress(context, taskToProcess, tasks, scope)
                    }
                }
            }
        }
        currentPickingTaskId = null
    }

    // --- 3. ФИЛЬТРАЦИЯ ---
    val displayedTasks = if (searchQuery.isBlank()) tasks else {
        tasks.filter { (it.id + 1).toString() == searchQuery || it.description.contains(searchQuery, ignoreCase = true) }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = primaryColor, background = secondaryColor)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(containerColor = secondaryColor) { paddingValues ->
                Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    ChallengeTopBar(
                        primaryColor = primaryColor,
                        isSyncing = isSyncing,
                        isSearchActive = isSearchActive,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onSearchToggle = { isActive ->
                            isSearchActive = isActive
                            if (!isActive) { searchQuery = ""; focusManager.clearFocus() }
                        },
                        focusRequester = focusRequester,
                        onSyncClick = {
                            if (!isSyncing) {
                                isSyncing = true
                                // ЗАПУСКАЕМ КОРУТИНУ ДЛЯ ФОНОВЫХ ЗАДАЧ:
                                scope.launch(Dispatchers.IO) {

                                    // 1. Отправляем на сервер наши изменения
                                    tasks.filter { it.completedUri != null }.forEach { task ->
                                        if (task.completedUri?.startsWith("http") == false) {
                                            SyncHelper.uploadTask(context, task) { /* прогресс */ }
                                        }
                                        else SyncHelper.updateTaskDetails(task)
                                    }

                                    // 2. Скачиваем данные с сервера
                                    val serverTasks = SyncHelper.fetchServerData()

                                    // 3. Обрабатываем пришедшие задания
                                    serverTasks.forEach { sTask ->
                                        val idx = tasks.indexOfFirst { it.id == sTask.id }
                                        if (idx != -1 && sTask.media_url != null) {

                                            val localUri = sharedPrefs.getString("task_${sTask.id}_uri", null)

                                            if (localUri == null) {
                                                // Файла у нас нет, надо скачивать!
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Скачиваю новый момент №${sTask.id + 1}...", Toast.LENGTH_SHORT).show()
                                                }

                                                val isVideo = sTask.media_type == "video" || sTask.media_url.endsWith(".mp4")
                                                // Вызываем наш внедренный класс из TaskMediaHelpers:
                                                val downloadedUri = GalleryDownloader.downloadAndSaveToGallery(context, sTask.media_url, sTask.id, isVideo)

                                                if (downloadedUri != null) {
                                                    sharedPrefs.edit().putString("task_${sTask.id}_uri", downloadedUri).apply()

                                                    // Обновляем UI в главном потоке
                                                    withContext(Dispatchers.Main) {
                                                        tasks[idx] = tasks[idx].copy(
                                                            completedUri = downloadedUri,
                                                            dateTaken = sTask.date_taken ?: "",
                                                            location = sTask.location ?: "",
                                                            description = sTask.description ?: tasks[idx].description
                                                        )
                                                    }
                                                }
                                            } else {
                                                // Файл уже есть в локальной Галерее, просто обновляем
                                                withContext(Dispatchers.Main) {
                                                    tasks[idx] = tasks[idx].copy(
                                                        completedUri = localUri,
                                                        dateTaken = sTask.date_taken ?: "",
                                                        location = sTask.location ?: "",
                                                        description = sTask.description ?: tasks[idx].description
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 4. Завершаем синхронизацию
                                    withContext(Dispatchers.Main) {
                                        isSyncing = false
                                        Toast.makeText(context, "Синхронизация завершена!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    )

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = primaryColor)
                        } else if (tasks.isEmpty()) {
                            Text("Нет связи с сервером.", modifier = Modifier.align(Alignment.Center), textAlign = TextAlign.Center)
                        } else {
                            Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(false)
                                    do {
                                        val event = awaitPointerEvent()
                                        val zoomChange = event.calculateZoom()
                                        if (zoomChange != 1f) {
                                            gridScale = (gridScale / zoomChange.pow(0.5f)).coerceIn(1f, 5f)
                                            lastZoomTime = System.currentTimeMillis()
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }) {
                                LazyVerticalStaggeredGrid(
                                    columns = StaggeredGridCells.Fixed(animatedColumns.roundToInt()),
                                    verticalItemSpacing = 8.dp,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(displayedTasks, key = { it.id }) { task ->
                                        Box(modifier = Modifier.animateItemPlacement(tween(500))) {
                                            TaskCard(task = task, icon = Icons.Default.Favorite, primaryColor = primaryColor, onClick = {
                                                if (System.currentTimeMillis() - lastZoomTime > 500) {
                                                    if (task.completedUri != null) { initialPage = tasks.indexOf(task); showPager = true }
                                                    else taskForUploadDialog = task
                                                }
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (taskForUploadDialog != null) {
                val task = taskForUploadDialog!!
                Dialog(onDismissRequest = { taskForUploadDialog = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                    Surface(modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(), shape = RoundedCornerShape(24.dp), color = Color.White) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Favorite, null, tint = primaryColor, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Задание №${task.id + 1}", color = Color.Gray)
                            Text(task.description, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = {
                                    currentPickingTaskId = task.id
                                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                    taskForUploadDialog = null
                                },
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

            AnimatedVisibility(visible = showPager, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                BackHandler { showPager = false }
                val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { tasks.size })
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        PhotoDetailScreen(
                            task = tasks[page],
                            onDismiss = { showPager = false },
                            onSaveDetails = { date, loc ->
                                val idx = tasks.indexOfFirst { it.id == tasks[page].id }
                                if (idx != -1) {
                                    val updated = tasks[idx].copy(dateTaken = date, location = loc)
                                    tasks[idx] = updated
                                    sharedPrefs.edit().putString("task_${updated.id}_date", date).putString("task_${updated.id}_loc", loc).apply()
                                    if (updated.completedUri?.startsWith("http") == true) scope.launch(Dispatchers.IO) { SyncHelper.updateTaskDetails(updated) }
                                }
                            },
                            onDelete = { deleteFromServer ->
                                val taskId = tasks[page].id
                                val idx = tasks.indexOfFirst { it.id == taskId }
                                if (idx != -1) {
                                    tasks[idx] = tasks[idx].copy(completedUri = null, dateTaken = "", location = "")
                                    sharedPrefs.edit().remove("task_${taskId}_uri").apply()
                                    if (deleteFromServer) scope.launch(Dispatchers.IO) { SyncHelper.deleteTask(taskId) }
                                }
                                showPager = false
                            }
                        )
                    }
                }
            }
        }
    }
}

// Вспомогательная функция для запуска загрузки и обновления UI
fun uploadWithProgress(
    context: Context,
    taskToUpload: LoveTask,
    tasks: androidx.compose.runtime.snapshots.SnapshotStateList<LoveTask>,
    scope: kotlinx.coroutines.CoroutineScope,
    onSuccessCleanup: () -> Unit = {}
) {
    scope.launch(Dispatchers.IO) {
        try {
            withContext(Dispatchers.Main) {
                val idx = tasks.indexOfFirst { it.id == taskToUpload.id }
                if (idx != -1) tasks[idx] = tasks[idx].copy(isCompressing = false, isUploading = true, uploadProgress = 0f)
            }

            SyncHelper.uploadTask(context, taskToUpload) { progress ->
                scope.launch(Dispatchers.Main) {
                    val idx = tasks.indexOfFirst { it.id == taskToUpload.id }
                    if (idx != -1) tasks[idx] = tasks[idx].copy(isUploading = true, uploadProgress = progress)
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "✅ Загружено на сервер!", Toast.LENGTH_SHORT).show()
                onSuccessCleanup()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) { Toast.makeText(context, "Ошибка загрузки. Попробуйте позже.", Toast.LENGTH_SHORT).show() }
        } finally {
            withContext(Dispatchers.Main) {
                val idx = tasks.indexOfFirst { it.id == taskToUpload.id }
                if (idx != -1) tasks[idx] = tasks[idx].copy(isUploading = false, isCompressing = false, uploadProgress = 0f)
            }
        }
    }
}

// --- ВЫНЕСЕННАЯ ВЕРХНЯЯ ПАНЕЛЬ ---
@Composable
fun ChallengeTopBar(
    primaryColor: Color,
    isSyncing: Boolean,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    onSyncClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 16.dp, start = 16.dp, end = 8.dp)
            .animateContentSize()
    ) {
        if (isSearchActive) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                placeholder = { Text("Поиск (слово или номер)...", color = Color.Gray) },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    cursorColor = primaryColor
                ),
                trailingIcon = {
                    IconButton(onClick = { onSearchToggle(false) }) {
                        Icon(Icons.Default.Close, "Close", tint = primaryColor)
                    }
                }
            )
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Наш Альбом ❤️",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = primaryColor,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start
                )

                IconButton(onClick = { onSearchToggle(true) }) {
                    Icon(Icons.Default.Search, "Search", tint = primaryColor)
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Menu", tint = primaryColor)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color.White)
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (isSyncing) "Синхронизация..." else "☁️ Синхронизация с сервером") },
                            onClick = {
                                showMenu = false
                                onSyncClick()
                            }
                        )
                    }
                }
            }
        }
    }
}