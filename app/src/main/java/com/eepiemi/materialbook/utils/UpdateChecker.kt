package com.eepiemi.materialbook.utils

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.eepiemi.materialbook.BuildConfig
import com.eepiemi.materialbook.R
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import org.json.JSONObject

data class Release(val version: String, val pageUrl: String, val apkUrl: String)

private const val LATEST = "https://api.github.com/repos/Ufoex/Materialbook/releases/latest"

/** Newest GitHub release if it is newer than this build, else null. Null also on any failure. */
suspend fun checkForUpdate(): Release? = runCatching {
    HttpClient(OkHttp).use { client ->
        val json = JSONObject(client.get(LATEST).bodyAsText())
        val version = json.getString("tag_name").removePrefix("v")
        val apk = json.getJSONArray("assets").getJSONObject(0).getString("browser_download_url")
        Release(version, json.getString("html_url"), apk).takeIf { isNewer(version, BuildConfig.VERSION_NAME) }
    }
}.getOrNull()

internal fun isNewer(remote: String, local: String): Boolean {
    val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
    val l = local.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(r.size, l.size)) {
        val a = r.getOrElse(i) { 0 }
        val b = l.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}

@Composable
fun UpdateDialog(release: Release, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = { Text(stringResource(R.string.update_available_desc, release.version, BuildConfig.VERSION_NAME)) },
        confirmButton = {
            TextButton(onClick = { openExternalUrl(context, release.apkUrl); onDismiss() }) {
                Text(stringResource(R.string.update_download))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) } }
    )
}
