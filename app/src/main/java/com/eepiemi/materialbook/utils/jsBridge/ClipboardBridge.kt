package com.eepiemi.materialbook.utils.jsBridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.webkit.JavascriptInterface
import com.eepiemi.materialbook.utils.showToast
import androidx.core.content.FileProvider
import com.eepiemi.materialbook.R
import java.io.File
import java.io.FileOutputStream

class ClipboardBridge(private val context: Context) {
    @JavascriptInterface
    fun copyText(text: String) {
        runCatching {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("text", text)
            clipboardManager.setPrimaryClip(clip)
            context.showToast(context.getString(R.string.text_copied_to_clipboard))
        }
    }

    @JavascriptInterface
    fun copyImageToClipboard(base64Data: String, mimeType: String) {
        runCatching {
            if (!base64Data.contains(",")) {
                context.showToast(context.getString(R.string.copy_failed_invalid_data))
                return
            }

            val data = Base64.decode(base64Data.split(",")[1], Base64.DEFAULT)

            // Decode bitmap to verify it's a valid image
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            if (bitmap == null) {
                context.showToast(context.getString(R.string.copy_failed_invalid_image))
                return
            }

            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            // Create a temporary file to hold the image
            val cacheDir = File(context.cacheDir, "clipboard_images")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            // Clean up old cached images
            cacheDir.listFiles()?.forEach { file ->
                if (System.currentTimeMillis() - file.lastModified() > 3600000) { // 1 hour
                    file.delete()
                }
            }

            // Save as PNG for maximum compatibility across apps
            val imageFile = File(cacheDir, "clipboard_${System.currentTimeMillis()}.png")
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )

            val clip = ClipData.newUri(context.contentResolver, "Image", contentUri)
            clipboardManager.setPrimaryClip(clip)

            context.showToast(context.getString(R.string.image_copied_to_clipboard))
        }.onFailure {
            context.showToast(context.getString(R.string.failed_to_copy_image))
        }
    }
}
