package com.wallifyhd.app.util

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.wallifyhd.app.data.model.Wallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

enum class WallpaperTarget {
    HOME,
    LOCK,
    BOTH
}

enum class WallpaperScaleMode {
    CROP,
    FIT
}

class ImageDownloader(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    suspend fun download(wallpaper: Wallpaper): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(wallpaper.imageUrl)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unable to download image (${response.code})")
            }

            val body = response.body ?: throw IOException("Downloaded image body was empty")
            val mimeType = body.contentType()?.toString() ?: "image/jpeg"
            val fileExtension = if (mimeType.contains("png")) "png" else "jpg"
            val fileName = "wallify_${wallpaper.remoteId}_${System.currentTimeMillis()}.$fileExtension"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}${File.separator}Wallify HD"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Unable to create media entry")

                resolver.openOutputStream(uri)?.use { output ->
                    body.byteStream().copyTo(output)
                } ?: throw IOException("Unable to open media output stream")

                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return@withContext uri.toString()
            }

            @Suppress("DEPRECATION")
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Wallify HD"
            ).apply {
                if (!exists()) mkdirs()
            }

            val file = File(directory, fileName)
            FileOutputStream(file).use { output ->
                body.byteStream().copyTo(output)
            }
            return@withContext Uri.fromFile(file).toString()
        }
    }
}

class WallpaperSetter(
    private val context: Context,
    private val imageLoader: ImageLoader
) {
    suspend fun setWallpaper(
        wallpaper: Wallpaper,
        target: WallpaperTarget,
        scaleMode: WallpaperScaleMode
    ) = withContext(Dispatchers.IO) {
        val originalBitmap = loadBitmap(wallpaper.primaryImage)
        val (targetWidth, targetHeight) = getTargetSize()
        val processedBitmap = scaleBitmap(
            bitmap = originalBitmap,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            scaleMode = scaleMode
        )

        val wallpaperManager = WallpaperManager.getInstance(context)
        wallpaperManager.suggestDesiredDimensions(targetWidth, targetHeight)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val flags = when (target) {
                WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
                WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
                WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }
            wallpaperManager.setBitmap(processedBitmap, null, true, flags)
        } else {
            if (target == WallpaperTarget.LOCK) {
                throw IllegalStateException("Lock-screen wallpaper needs Android 7.0 or newer")
            }
            wallpaperManager.setBitmap(processedBitmap)
        }
    }

    private suspend fun loadBitmap(source: String): Bitmap {
        val request = ImageRequest.Builder(context)
            .data(source)
            .allowHardware(false)
            .build()

        val result = imageLoader.execute(request) as? SuccessResult
            ?: throw IOException("Unable to decode wallpaper")
        return result.drawable.toBitmap()
    }

    private fun getTargetSize(): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics
        return metrics.widthPixels.coerceAtLeast(1080) to metrics.heightPixels.coerceAtLeast(1920)
    }

    private fun scaleBitmap(
        bitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        scaleMode: WallpaperScaleMode
    ): Bitmap {
        if (bitmap.width <= 0 || bitmap.height <= 0) return bitmap

        val widthScale = targetWidth.toFloat() / bitmap.width.toFloat()
        val heightScale = targetHeight.toFloat() / bitmap.height.toFloat()
        val scaleFactor = when (scaleMode) {
            WallpaperScaleMode.CROP -> maxOf(widthScale, heightScale)
            WallpaperScaleMode.FIT -> minOf(widthScale, heightScale)
        }

        val scaledWidth = (bitmap.width * scaleFactor).toInt().coerceAtLeast(1)
        val scaledHeight = (bitmap.height * scaleFactor).toInt().coerceAtLeast(1)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)

        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(android.graphics.Color.BLACK)

        val left = (targetWidth - scaledWidth) / 2
        val top = (targetHeight - scaledHeight) / 2
        val destRect = Rect(left, top, left + scaledWidth, top + scaledHeight)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(scaledBitmap, null, destRect, paint)
        return output
    }
}
