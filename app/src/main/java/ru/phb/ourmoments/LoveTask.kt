package ru.phb.ourmoments

data class LoveTask(
    val id: Int,
    val description: String,
    var completedUri: String? = null,
    var dateTaken: String = "",
    var location: String = "",
    val heightRatio: Float = 1.0f
) {
    // Расширенная проверка для .mov и других форматов
    val isVideo: Boolean get() {
        val uri = completedUri?.lowercase() ?: return false
        return uri.contains("video") ||
                uri.endsWith(".mov") ||
                uri.endsWith(".mp4") ||
                uri.endsWith(".mkv")
    }
}