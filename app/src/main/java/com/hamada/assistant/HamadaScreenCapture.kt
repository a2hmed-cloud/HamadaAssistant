package com.hamada.assistant

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * User-consented, one-frame screen capture. Android shows the system consent dialog;
 * secure/protected windows may still be blank. This does not continuously record.
 */
class HamadaScreenCapture(private val context: Context) {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val busy = AtomicBoolean(false)

    fun start(resultCode: Int, data: Intent): Boolean {
        stop()
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mediaProjection = manager.getMediaProjection(resultCode, data) ?: return false
        projection = mediaProjection
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val scale = minOf(1f, 1280f / metrics.widthPixels.toFloat())
        val width = (metrics.widthPixels * scale).toInt().coerceAtLeast(1)
        val height = (metrics.heightPixels * scale).toInt().coerceAtLeast(1)
        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader = imageReader
        display = mediaProjection.createVirtualDisplay(
            "HamadaConsentCapture", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.surface, null, Handler(Looper.getMainLooper())
        )
        return true
    }

    /** Captures a single fresh frame and returns a compact JPEG data URL. */
    fun captureOnce(callback: (Result<String>) -> Unit) {
        val imageReader = reader
        if (projection == null || imageReader == null) {
            callback(Result.failure(IllegalStateException("إذن تصوير الشاشة غير مفعّل")))
            return
        }
        if (!busy.compareAndSet(false, true)) {
            callback(Result.failure(IllegalStateException("التقاط الشاشة جارٍ بالفعل")))
            return
        }
        val handler = Handler(Looper.getMainLooper())
        var attempts = 0
        val capture = object : Runnable {
            override fun run() {
                try {
                    val image = imageReader.acquireLatestImage()
                    if (image == null && attempts++ < 10) {
                        handler.postDelayed(this, 120)
                        return
                    }
                    if (image == null) throw IllegalStateException("لم تصل صورة من الشاشة")
                    image.use {
                        val plane = it.planes[0]
                        val buffer = plane.buffer
                        val pixelStride = plane.pixelStride
                        val rowStride = plane.rowStride
                        val rowPadding = rowStride - pixelStride * it.width
                        val padded = Bitmap.createBitmap(it.width + rowPadding / pixelStride, it.height, Bitmap.Config.ARGB_8888)
                        padded.copyPixelsFromBuffer(buffer)
                        val bitmap = Bitmap.createBitmap(padded, 0, 0, it.width, it.height)
                        padded.recycle()
                        val out = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out)
                        bitmap.recycle()
                        val encoded = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                        callback(Result.success("data:image/jpeg;base64,$encoded"))
                    }
                } catch (e: Exception) {
                    callback(Result.failure(e))
                } finally {
                    busy.set(false)
                }
            }
        }
        // Let the virtual display deliver a new frame before reading.
        handler.postDelayed(capture, 350)
    }

    fun stop() {
        display?.release()
        display = null
        reader?.close()
        reader = null
        projection?.stop()
        projection = null
        busy.set(false)
    }
}
