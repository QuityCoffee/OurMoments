package ru.phb.ourmoments

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ru.phb.ourmoments.ui.theme.OurMomentsTheme

// ==========================================
// 1. КОНФИГУРАЦИЯ ПРИЛОЖЕНИЯ (AppConfig)
// ==========================================
enum class Environment {
    TEST,
    PROD
}

object AppConfig {
    // По умолчанию ставим ТЕСТ, чтобы случайно не сломать Прод при разработке
    var currentEnv: Environment = Environment.PROD
    var isDebug: Boolean = true
    // Динамически отдаем нужный URL в зависимости от выбранной среды
    val apiUrl: String
        get() = when (currentEnv) {
            Environment.PROD -> "https://quityrom.ru/api.php"
            Environment.TEST -> "https://quityrom.ru/test/apitest.php"
        }

    // При желании можно сделать разные ключи безопасности
    val apiKey: String
        get() = when (currentEnv) {
            Environment.PROD -> "MyLoveSecret2026quityromgmailcom"
            Environment.TEST -> "MyLoveSecret2026quityromgmailcom" // Если поменял ключ в test/api.php
        }
}

// ==========================================
// 2. ГЛАВНЫЙ ЭКРАН (MainActivity)
// ==========================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // РУБИЛЬНИК СРЕДЫ (ПЕРЕД РЕЛИЗОМ МЕНЯТЬ НА PROD!)
        AppConfig.currentEnv = Environment.PROD

        setContent {
            OurMomentsTheme {
                val context = LocalContext.current
                var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

                // Проверяем обновления при запуске
                LaunchedEffect(Unit) {
                    // Ищем обновления только на Проде, чтобы не мешало при разработке
                    if (AppConfig.currentEnv == Environment.PROD) {
                        updateInfo = UpdateManager.checkForUpdate(context)
                    }
                }

                // Рисуем окно, если есть обнова
                updateInfo?.let { info ->
                    AlertDialog(
                        onDismissRequest = { /* Можно закрыть, если не обязательное */ },
                        title = { Text("Доступно обновление ${info.versionName} ✨") },
                        text = { Text(info.releaseNotes) },
                        confirmButton = {
                            Button(onClick = {
                                UpdateManager.downloadAndInstallUpdate(context, info.apkUrl)
                                updateInfo = null // Прячем диалог
                            }) {
                                Text("Обновить")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { updateInfo = null }) { Text("Позже") }
                        }
                    )
                }

                // Сам экран
                ChallengeScreen()
            }
        }
    }
}