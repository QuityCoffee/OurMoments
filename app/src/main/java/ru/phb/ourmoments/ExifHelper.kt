package ru.phb.ourmoments

import android.content.Context
import android.net.Uri
import java.io.InputStream

object ExifHelper {
    fun getPhotoDetails(context: Context, uri: Uri): Pair<String, String> {
        var date = ""
        var location = ""

        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                // Используем "полное имя" (androidx.exifinterface...), чтобы студия точно не перепутала
                val exif = androidx.exifinterface.media.ExifInterface(inputStream)

                // 1. Достаем дату
                val dateTag = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME)
                if (!dateTag.isNullOrEmpty()) {
                    // Превращаем "2023:02:14 18:30" в "2023-02-14"
                    date = dateTag.substringBefore(" ").replace(":", "-")
                }

                // 2. Достаем координаты
                val latLong = exif.latLong
                if (latLong != null) {
                    location = "Геометка найдена"
                }

                inputStream.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Pair(date, location)
    }
}