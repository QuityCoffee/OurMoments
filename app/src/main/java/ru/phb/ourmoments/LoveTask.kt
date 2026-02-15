package ru.phb.ourmoments

import android.content.Context
import android.net.Uri

data class LoveTask(
    val id: Int,
    val description: String,
    val heightRatio: Float = 1.0f,
    var completedUri: String? = null,
    var dateTaken: String = "",
    var location: String = "",
    val isUploading: Boolean = false,
    val isCompressing: Boolean = false, // <-- НОВОЕ ПОЛЕ
    val uploadProgress: Float = 0f
) {
    // Умная проверка: видео это или фото?
    fun getIsVideo(context: Context): Boolean {
        val uriString = completedUri ?: return false
        val uri = Uri.parse(uriString)

        // 1. Спрашиваем у системы
        val type = context.contentResolver.getType(uri)
        if (type?.startsWith("video") == true) return true

        // 2. Проверяем расширение (на всякий случай)
        val lower = uriString.lowercase()
        return lower.contains("video") || lower.endsWith(".mp4") || lower.endsWith(".mov")
    }
}