package ru.phb.ourmoments

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupHelper {

    // ЭКСПОРТ (Создание ZIP)
    fun createBackup(context: Context, tasks: List<LoveTask>): File {
        val backupDir = File(context.cacheDir, "backup_temp")
        if (backupDir.exists()) backupDir.deleteRecursively()
        backupDir.mkdirs()

        val zipFile = File(context.cacheDir, "OurMoments_Backup.zip")
        val zos = ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile)))

        // 1. Сохраняем JSON с данными
        val jsonArray = JSONArray()
        tasks.filter { it.completedUri != null }.forEach { task ->
            val jsonObj = JSONObject()
            jsonObj.put("id", task.id)
            jsonObj.put("date", task.dateTaken)
            jsonObj.put("loc", task.location)

            // Копируем файл фото/видео внутрь архива
            val sourceUri = Uri.parse(task.completedUri)
            val fileName = "file_${task.id}" + getExtension(context, sourceUri)
            jsonObj.put("fileName", fileName)

            // Добавляем файл в ZIP
            try {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    zos.putNextEntry(ZipEntry("files/$fileName"))
                    input.copyTo(zos)
                    zos.closeEntry()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            jsonArray.put(jsonObj)
        }

        // Записываем сам JSON в архив
        zos.putNextEntry(ZipEntry("data.json"))
        zos.write(jsonArray.toString().toByteArray())
        zos.closeEntry()

        zos.close()
        return zipFile
    }

    // ИМПОРТ (Восстановление из ZIP)
    fun restoreBackup(context: Context, uri: Uri, onTaskRestored: (Int, String, String, String) -> Unit) {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return
        val zipStream = ZipInputStream(inputStream)
        var entry = zipStream.nextEntry

        val filesDir = context.filesDir // Папка для вечного хранения
        var jsonString = ""

        while (entry != null) {
            if (entry.name == "data.json") {
                jsonString = zipStream.readBytes().toString(Charsets.UTF_8)
            } else if (entry.name.startsWith("files/")) {
                // Извлекаем фото/видео
                val fileName = File(entry.name).name
                val outFile = File(filesDir, fileName)
                FileOutputStream(outFile).use { output ->
                    zipStream.copyTo(output)
                }
            }
            entry = zipStream.nextEntry
        }
        zipStream.close()

        // Разбираем JSON и обновляем задачи
        if (jsonString.isNotEmpty()) {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getInt("id")
                val date = obj.optString("date")
                val loc = obj.optString("loc")
                val fileName = obj.optString("fileName")

                val savedFile = File(filesDir, fileName)
                if (savedFile.exists()) {
                    // Возвращаем данные в приложение (URI теперь локальный!)
                    onTaskRestored(id, Uri.fromFile(savedFile).toString(), date, loc)
                }
            }
        }
    }

    private fun getExtension(context: Context, uri: Uri): String {
        val type = context.contentResolver.getType(uri)
        return when {
            type?.contains("video") == true -> ".mp4"
            else -> ".jpg"
        }
    }
}