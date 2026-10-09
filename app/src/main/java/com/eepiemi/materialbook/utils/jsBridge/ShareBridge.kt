package com.eepiemi.materialbook.utils.jsBridge

import android.content.Context
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.JavascriptInterface
import com.eepiemi.materialbook.utils.ShareBox
import com.eepiemi.materialbook.utils.publishChatShortcuts
import org.json.JSONArray
import org.json.JSONObject

/**
 * Used by share_into_chat.js in the Messages layer: hands over what is being shared so it can
 * be typed into the chat that is open, and reports the chats of the inbox for direct share.
 */
class ShareBridge(private val context: Context, private val onDone: () -> Unit) {
    @JavascriptInterface
    fun active(): Boolean = ShareBox.inChat != null

    @JavascriptInterface
    fun text(): String = ShareBox.inChat?.text.orEmpty()

    @JavascriptInterface
    fun files(): String {
        val list = JSONArray()
        ShareBox.inChat?.files?.forEach { uri ->
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            }.getOrNull() ?: "file"
            list.put(JSONObject().put("name", name).put("mime", context.contentResolver.getType(uri) ?: "application/octet-stream"))
        }
        return list.toString()
    }

    /** One file as base64 (up to 25 MB; larger ones are skipped). */
    @JavascriptInterface
    fun fileData(index: Int): String = runCatching {
        val uri = ShareBox.inChat!!.files[index]
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        if (bytes.size > 25 * 1024 * 1024) "" else Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrDefault("")

    @JavascriptInterface
    fun done() = onDone()

    /** [{id, name, img}] of the first chats of the inbox. */
    @JavascriptInterface
    fun chats(json: String) = publishChatShortcuts(context, json)
}
