package ru.phb.ourmoments

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
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
import androidx.compose.runtime.snapshots.SnapshotStateList
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChallengeScreen() {
    val primaryColor = Color(0xFFE91E63)
    val secondaryColor = Color(0xFFF0F2F5)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sharedPrefs = remember { context.getSharedPreferences("love_tasks", Context.MODE_PRIVATE) }

    // --- СОСТОЯНИЕ UI ---
    var gridScale by remember { mutableFloatStateOf(1.8f) }
    val animatedColumns by animateFloatAsState(targetValue = gridScale, animationSpec = spring(stiffness = Spring.StiffnessLow), label = "cols")
    var lastZoomTime by remember { mutableLongStateOf(0L) }

    var showPager by remember { mutableStateOf(false) }
    var initialPage by remember { mutableIntStateOf(0) }
    var taskForUploadDialog by remember { mutableStateOf<LoveTask?>(null) }
    var currentPickingTaskId by remember { mutableStateOf<Int?>(null) }

    // Поиск и фильтры
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var sortCompletedToTop by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    var isSyncing by remember { mutableStateOf(false) }
    var visibleItemsCount by remember { mutableIntStateOf(20) }
    val gridState = rememberLazyStaggeredGridState()

    val tasks = remember { mutableStateListOf<LoveTask>() }

    val availableCategories = remember(tasks) {
        tasks.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    }

    var userName by remember { mutableStateOf(sharedPrefs.getString("user_name", "")?.trim() ?: "") }
    var showNameDialog by remember { mutableStateOf(userName.isBlank()) }
    var isLoading by remember { mutableStateOf(userName.isNotBlank()) }

    // --- 1. ЗАГРУЗКА ИЗ КЭША И СЕРВЕРА ---
    LaunchedEffect(userName) {
        if (userName.isNotBlank()) {
            isLoading = true
            val cachedJson = sharedPrefs.getString("cached_server_tasks", null)
            if (cachedJson != null) {
                try {
                    val cachedTasks = Json.decodeFromString<List<ServerTask>>(cachedJson)
                    updateLocalTaskList(cachedTasks, tasks, sharedPrefs)
                } catch (e: Exception) { e.printStackTrace() }
            }

            scope.launch(Dispatchers.IO) {
                val serverTasks = SyncHelper.fetchServerData(userName)
                if (serverTasks.isNotEmpty()) {
                    sharedPrefs.edit().putString("cached_server_tasks", Json.encodeToString(serverTasks)).apply()
                    withContext(Dispatchers.Main) { updateLocalTaskList(serverTasks, tasks, sharedPrefs) }
                    processSequentialDownloads(context, serverTasks, tasks, sharedPrefs, scope)
                }
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    // --- 2. ЛАУНЧЕР ВЫБОРА ФАЙЛОВ ---
    val photoPickerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia()) { originalUri ->
        if (originalUri != null && currentPickingTaskId != null) {
            val flag = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(originalUri, flag)
            val index = tasks.indexOfFirst { it.id == currentPickingTaskId }
            if (index != -1) {
                val (exifDate, exifLoc) = try { ExifHelper.getPhotoDetails(context, originalUri) } catch (e: Exception) { "" to "" }
                val mimeType = context.contentResolver.getType(originalUri) ?: ""
                val isVideo = mimeType.startsWith("video/")

                var taskToProcess = tasks[index].copy(completedUri = originalUri.toString(), dateTaken = exifDate, location = exifLoc)
                sharedPrefs.edit().putString("task_${currentPickingTaskId}_uri", originalUri.toString()).putString("task_${currentPickingTaskId}_date", exifDate).putString("task_${currentPickingTaskId}_loc", exifLoc).apply()

                scope.launch(Dispatchers.Main) {
                    if (isVideo) {
                        Toast.makeText(context, "🎬 Сжатие видео...", Toast.LENGTH_SHORT).show()
                        var compressedFile: java.io.File? = null
                        VideoCompressorHelper.compressVideo(context, originalUri).collect { status ->
                            when (status) {
                                is CompressionStatus.Progress -> { val idx = tasks.indexOfFirst { it.id == taskToProcess.id }; if (idx != -1) tasks[idx] = tasks[idx].copy(isCompressing = true, uploadProgress = status.percent) }
                                is CompressionStatus.Success -> { compressedFile = status.compressedFile; val newUri = android.net.Uri.fromFile(compressedFile).toString(); sharedPrefs.edit().putString("task_${taskToProcess.id}_uri", newUri).apply(); taskToProcess = taskToProcess.copy(completedUri = newUri) }
                                is CompressionStatus.Error -> { Toast.makeText(context, "Ошибка: ${status.failureMessage}", Toast.LENGTH_LONG).show(); val idx = tasks.indexOfFirst { it.id == taskToProcess.id }; if (idx != -1) tasks[idx] = tasks[idx].copy(isCompressing = false); return@collect }
                            }
                        }
                        if (compressedFile != null) uploadWithProgress(context, taskToProcess, tasks, scope, userName)
                    } else uploadWithProgress(context, taskToProcess, tasks, scope, userName)
                }
            }
        }
        currentPickingTaskId = null
    }

    // --- 3. УМНАЯ ФИЛЬТРАЦИЯ ---
    LaunchedEffect(searchQuery, sortCompletedToTop, selectedCategory) {
        visibleItemsCount = 20
        gridState.scrollToItem(0)
    }

    val filteredTasks = tasks.filter { task ->
        val matchesCategory = selectedCategory == null || task.category.trim().equals(
            selectedCategory!!.trim(), ignoreCase = true)
        val matchesSearch = if (searchQuery.isBlank()) true else {
            val q = searchQuery.lowercase().trim()
            (task.id + 1).toString() == q || task.description.lowercase().contains(q) || task.category.lowercase().contains(q)
        }
        matchesCategory && matchesSearch
    }

    val sortedTasks = if (sortCompletedToTop) filteredTasks.sortedWith(compareByDescending<LoveTask> { it.completedUri != null }.thenBy { it.id }) else filteredTasks
    val paginatedTasks = sortedTasks.take(visibleItemsCount)

    LaunchedEffect(gridState, sortedTasks.size) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItemIndex >= totalItems - 4 && totalItems > 0
        }.collect { isNearEnd -> if (isNearEnd && visibleItemsCount < sortedTasks.size) visibleItemsCount += 20 }
    }

    // --- UI ---
    MaterialTheme(colorScheme = lightColorScheme(primary = primaryColor, background = secondaryColor)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(containerColor = secondaryColor) { paddingValues ->
                Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

                    ChallengeTopBar(
                        primaryColor = primaryColor, isSyncing = isSyncing, isSearchActive = isSearchActive, searchQuery = searchQuery, sortCompletedToTop = sortCompletedToTop, userName = userName,
                        onSearchQueryChange = { searchQuery = it }, onSearchToggle = { isActive -> isSearchActive = isActive; if (!isActive) { searchQuery = ""; focusManager.clearFocus() } }, onSortToggle = { sortCompletedToTop = !sortCompletedToTop }, onChangeNameClick = { showNameDialog = true }, focusRequester = focusRequester,
                        onSyncClick = {
                            if (!isSyncing && userName.isNotBlank()) {
                                isSyncing = true
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        tasks.filter { it.completedUri != null }.forEach { task ->
                                            if (task.completedUri?.startsWith("http") == false) SyncHelper.uploadTask(context, task, userName) {} else SyncHelper.updateTaskDetails(task, userName)
                                        }
                                        val serverTasks = SyncHelper.fetchServerData(userName)
                                        if(serverTasks.isNotEmpty()){
                                            sharedPrefs.edit().putString("cached_server_tasks", Json.encodeToString(serverTasks)).apply()
                                            withContext(Dispatchers.Main) { updateLocalTaskList(serverTasks, tasks, sharedPrefs) }
                                            processSequentialDownloads(context, serverTasks, tasks, sharedPrefs, scope)
                                        }
                                        withContext(Dispatchers.Main) { Toast.makeText(context, "Синхронизация завершена! ✅", Toast.LENGTH_SHORT).show() }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) { Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show() }
                                    } finally {
                                        withContext(Dispatchers.Main) { isSyncing = false }
                                    }
                                }
                            }
                        }
                    )

                    if (availableCategories.isNotEmpty()) {
                        LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null }, label = { Text("Все") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = primaryColor, selectedLabelColor = Color.White)) }
                            items(availableCategories) { cat -> FilterChip(selected = selectedCategory == cat, onClick = { selectedCategory = cat }, label = { Text(cat) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = primaryColor, selectedLabelColor = Color.White)) }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = primaryColor)
                        else {
                            Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                                awaitEachGesture { awaitFirstDown(false); do { val event = awaitPointerEvent(); val zoomChange = event.calculateZoom(); if (zoomChange != 1f) { gridScale = (gridScale / zoomChange.pow(0.5f)).coerceIn(1f, 5f); lastZoomTime = System.currentTimeMillis() } } while (event.changes.any { it.pressed }) }
                            }) {
                                LazyVerticalStaggeredGrid(
                                    columns = StaggeredGridCells.Fixed(animatedColumns.roundToInt()), state = gridState, verticalItemSpacing = 8.dp, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), modifier = Modifier.fillMaxSize()
                                ) {
                                    items(paginatedTasks, key = { it.id }) { task ->
                                        Box(modifier = Modifier.animateItemPlacement(tween(500))) {
                                            TaskCard(task = task, icon = Icons.Default.Favorite, primaryColor = primaryColor, onClick = {
                                                if (System.currentTimeMillis() - lastZoomTime > 500) { if (task.completedUri != null) { initialPage = tasks.indexOf(task); showPager = true } else taskForUploadDialog = task }
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showNameDialog) {
                var nameInput by remember { mutableStateOf(userName) }
                AlertDialog(
                    onDismissRequest = { if (userName.isNotBlank()) showNameDialog = false },
                    title = { Text("Добро пожаловать! 👋") },
                    text = { OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, placeholder = { Text("Ваше имя") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
                    confirmButton = { Button(onClick = { if (nameInput.isNotBlank()) { userName = nameInput.trim(); sharedPrefs.edit().putString("user_name", userName).apply(); showNameDialog = false } }) { Text("Сохранить") } }
                )
            }

            if (taskForUploadDialog != null) {
                val task = taskForUploadDialog!!
                Dialog(onDismissRequest = { taskForUploadDialog = null }) {
                    Surface(modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight(), shape = RoundedCornerShape(24.dp), color = Color.White) {
                        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Задание №${task.id + 1}", color = Color.Gray)
                            Text(task.description, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = { currentPickingTaskId = task.id; photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)); taskForUploadDialog = null }, modifier = Modifier.fillMaxWidth()) { Text("Выбрать фото или видео") }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showPager, enter = fadeIn(), exit = fadeOut()) {
                BackHandler { showPager = false }
                val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { tasks.size })
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    HorizontalPager(state = pagerState) { page ->
                        PhotoDetailScreen(
                            task = tasks[page], onDismiss = { showPager = false },
                            onSaveDetails = { date, loc -> val idx = tasks.indexOfFirst { it.id == tasks[page].id }; if (idx != -1) { val updated = tasks[idx].copy(dateTaken = date, location = loc); tasks[idx] = updated; sharedPrefs.edit().putString("task_${updated.id}_date", date).putString("task_${updated.id}_loc", loc).apply(); if (updated.completedUri?.startsWith("http") == true) scope.launch(Dispatchers.IO) { SyncHelper.updateTaskDetails(updated, userName) } } },
                            onDelete = { deleteFromServer -> val taskId = tasks[page].id; val idx = tasks.indexOfFirst { it.id == taskId }; if (idx != -1) { tasks[idx] = tasks[idx].copy(completedUri = null); sharedPrefs.edit().remove("task_${taskId}_uri").apply(); if (deleteFromServer) scope.launch(Dispatchers.IO) { SyncHelper.deleteTask(taskId, userName) } }; showPager = false }
                        )
                    }
                }
            }
        }
    }
}

// --- КОМПОНЕНТ ШАПКИ ---
@Composable
fun ChallengeTopBar(
    primaryColor: Color, isSyncing: Boolean, isSearchActive: Boolean, searchQuery: String, sortCompletedToTop: Boolean, userName: String,
    onSearchQueryChange: (String) -> Unit, onSearchToggle: (Boolean) -> Unit, onSortToggle: () -> Unit, onChangeNameClick: () -> Unit, focusRequester: FocusRequester, onSyncClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            if (isSearchActive) {
                OutlinedTextField(value = searchQuery, onValueChange = onSearchQueryChange, modifier = Modifier.fillMaxWidth().focusRequester(focusRequester), placeholder = { Text("Поиск...") }, singleLine = true, trailingIcon = { IconButton(onClick = { onSearchToggle(false) }) { Icon(Icons.Default.Close, null) } })
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Наш Альбом ❤️", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = primaryColor, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onSearchToggle(true) }) { Icon(Icons.Default.Search, null, tint = primaryColor) }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null, tint = primaryColor) }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text(if (isSyncing) "⏳ Синхронизация..." else "☁️ Синхронизация") }, onClick = { showMenu = false; onSyncClick() })
                            DropdownMenuItem(text = { Text(if (sortCompletedToTop) "📑 По порядку" else "🔝 Выполненные") }, onClick = { showMenu = false; onSortToggle() })
                            DropdownMenuItem(text = { Text("👤 Имя: $userName") }, onClick = { showMenu = false; onChangeNameClick() })
                        }
                    }
                }
            }
        }
        AnimatedVisibility(visible = isSyncing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = primaryColor)
        }
    }
}

// --- ФУНКЦИИ ОБНОВЛЕНИЯ И ЗАГРУЗКИ ---
fun updateLocalTaskList(serverTasks: List<ServerTask>, tasks: SnapshotStateList<LoveTask>, sharedPrefs: SharedPreferences) {
    val mappedTasks = serverTasks.map { sTask ->
        val localUri = sharedPrefs.getString("task_${sTask.id}_uri", null)
        val existingTask = tasks.find { it.id == sTask.id }
        LoveTask(
            id = sTask.id, description = sTask.description ?: "Задание №${sTask.id + 1}",
            heightRatio = existingTask?.heightRatio ?: Random.nextDouble(0.7, 1.8).toFloat(),
            completedUri = localUri ?: sTask.media_url, dateTaken = sTask.date_taken ?: "",
            location = sTask.location ?: "", category = sTask.category ?: ""
        )
    }
    tasks.clear()
    tasks.addAll(mappedTasks)
}


private suspend fun updateTaskUI(
    tasks: SnapshotStateList<LoveTask>,
    id: Int,
    transform: (LoveTask) -> LoveTask
) {
    withContext(Dispatchers.Main) { // Переключаемся на главный поток для UI
        val idx = tasks.indexOfFirst { it.id == id }
        if (idx != -1) {
            tasks[idx] = transform(tasks[idx])
        }
    }
}


// --- ОБНОВЛЕННАЯ ОЧЕРЕДЬ С ПРОВЕРКОЙ НАЛИЧИЯ ФАЙЛА ---
suspend fun processSequentialDownloads(
    context: Context,
    serverTasks: List<ServerTask>,
    tasks: SnapshotStateList<LoveTask>,
    sharedPrefs: SharedPreferences,
    scope: CoroutineScope
) {
    // Ограничиваем параллельность загрузки
    val dispatcher = Dispatchers.IO.limitedParallelism(3)

    // Используем цикл for, так как он позволяет вызывать suspend функции внутри
    for (sTask in serverTasks) {
        if (sTask.media_url != null) {
            val localUriString = sharedPrefs.getString("task_${sTask.id}_uri", null)

            // Проверка физического наличия файла
            val fileExists = if (localUriString != null) {
                try {
                    val uri = Uri.parse(localUriString)
                    context.contentResolver.openInputStream(uri)?.use { true } ?: false
                } catch (e: Exception) { false }
            } else false

            if (fileExists) {
                // Вызываем suspend функцию напрямую в цикле
                updateTaskUI(tasks, sTask.id) {
                    it.copy(completedUri = localUriString, isDownloading = false, downloadStatus = "")
                }
            } else {
                // Запускаем асинхронную загрузку
                scope.launch(dispatcher) {
                    updateTaskUI(tasks, sTask.id) {
                        it.copy(isDownloading = true, downloadStatus = "В очереди...", downloadProgress = 0f)
                    }

                    val isVideo = sTask.media_type == "video" || sTask.media_url.endsWith(".mp4")
                    val downloadedUri = GalleryDownloader.downloadAndSaveToGallery(
                        context, sTask.media_url, sTask.id, isVideo
                    ) { progress, status ->
                        // Внутри лямбды GalleryDownloader мы запускаем новую корутину для обновления UI
                        scope.launch {
                            updateTaskUI(tasks, sTask.id) {
                                it.copy(downloadProgress = progress, downloadStatus = status)
                            }
                        }
                    }

                    if (downloadedUri != null) {
                        sharedPrefs.edit().putString("task_${sTask.id}_uri", downloadedUri).apply()
                        updateTaskUI(tasks, sTask.id) {
                            it.copy(isDownloading = false, completedUri = downloadedUri, downloadStatus = "")
                        }
                    } else {
                        updateTaskUI(tasks, sTask.id) {
                            it.copy(isDownloading = false, downloadStatus = "Ошибка загрузки")
                        }
                    }
                }
            }
        }
    }
}



// Функция загрузки (уже была у тебя, оставляем для полноты)
fun uploadWithProgress(
    context: Context,
    taskToUpload: LoveTask,
    tasks: SnapshotStateList<LoveTask>,
    scope: CoroutineScope,
    userName: String
) {
    scope.launch(Dispatchers.IO) {
        try {
            // Оборачиваем в launch(Dispatchers.Main), так как updateTaskUI — это suspend функция
            updateTaskUI(tasks, taskToUpload.id) {
                it.copy(isUploading = true, uploadProgress = 0f)
            }

            SyncHelper.uploadTask(context, taskToUpload, userName) { progress ->
                // Здесь мы уже внутри коллбэка, запускаем корутину для обновления UI
                scope.launch {
                    updateTaskUI(tasks, taskToUpload.id) {
                        it.copy(uploadProgress = progress)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Ошибка загрузки", Toast.LENGTH_SHORT).show()
            }
        } finally {
            // Финальный сброс индикаторов загрузки
            updateTaskUI(tasks, taskToUpload.id) {
                it.copy(isUploading = false, isCompressing = false)
            }
        }
    }
}