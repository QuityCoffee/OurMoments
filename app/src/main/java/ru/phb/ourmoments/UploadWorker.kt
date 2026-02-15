package ru.phb.ourmoments

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

class UploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getInt("taskId", -1)
        if (taskId == -1) return Result.failure()

        // Получаем актуальные данные из SharedPreferences или БД
        val sharedPrefs = applicationContext.getSharedPreferences("love_tasks", Context.MODE_PRIVATE)
        val uri = sharedPrefs.getString("task_${taskId}_uri", null) ?: return Result.failure()
        val date = sharedPrefs.getString("task_${taskId}_date", "") ?: ""
        val loc = sharedPrefs.getString("task_${taskId}_loc", "") ?: ""

        // Создаем временный объект для SyncHelper
        val tempTask = LoveTask(id = taskId, description = "", heightRatio = 1f).apply {
            completedUri = uri
            dateTaken = date
            location = loc
        }
        return try {
            // Добавили пустые скобки {} в конце
            SyncHelper.uploadTask(applicationContext, tempTask) {}
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}