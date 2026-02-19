package ru.phb.ourmoments

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object UpdateManager {
    private const val UPDATE_JSON_URL = "https://quityrom.ru/update.json" // Ссылка на твой JSON

    // Проверяем версию
    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val client = HttpClient(Android)
            val responseText = client.get(UPDATE_JSON_URL).bodyAsText()
            val json = JSONObject(responseText)

            val serverVersionCode = json.getInt("versionCode")

            // Получаем текущую версию приложения
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= 28) {
                packageInfo.longVersionCode.toInt()
            } else {
                packageInfo.versionCode
            }

            // Если на сервере версия больше - нужно обновляться
            if (serverVersionCode > currentVersionCode) {
                return@withContext UpdateInfo(
                    versionName = json.getString("versionName"),
                    apkUrl = json.getString("apkUrl"),
                    releaseNotes = json.getString("releaseNotes")
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    // Скачиваем и запускаем установку
// Скачиваем и запускаем установку
// Скачиваем и запускаем установку
// Скачиваем и запускаем установку
    fun downloadAndInstallUpdate(context: Context, apkUrl: String) {
        // МАГИЯ ЗДЕСЬ: Уникальное имя файла при каждой загрузке!
        val fileName = "Update_${System.currentTimeMillis()}.apk"
        val destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Обновление Нашей Истории ❤️")
            .setDescription("Скачивание новой версии...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            // Возвращаем загрузку в безопасную личную папку приложения
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    // Передаем точный, уникальный файл в установщик
                    installApk(ctxt, destinationFile)
                    ctxt.unregisterReceiver(this)
                }
            }
        }

        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        Toast.makeText(context, "Скачиваем обновление...", Toast.LENGTH_SHORT).show()
    }

    private fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(context, "Ошибка: Файл обновления не найден", Toast.LENGTH_LONG).show()
            return
        }

        // --- ДИАГНОСТИКА: Проверяем размер скачанного файла ---
        val fileSizeInBytes = apkFile.length()
        val fileSizeInMb = fileSizeInBytes / (1024 * 1024)

        // Если файл весит меньше 1 Мегабайта, это точно не APK
        if (fileSizeInMb < 1) {
            Toast.makeText(context, "Скачался битый файл! Вес: $fileSizeInBytes байт. Проверь ссылку в update.json", Toast.LENGTH_LONG).show()
            return
        }
        // ------------------------------------------------------

        try {
            val uri = FileProvider.getUriForFile(context, "ru.phb.ourmoments.fileprovider", apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            // БРОНЕБОЙНЫЙ ФИКС ДЛЯ SAMSUNG: Явно даем права всем системным установщикам
            val resInfoList = context.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Ошибка запуска установки: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

data class UpdateInfo(val versionName: String, val apkUrl: String, val releaseNotes: String)