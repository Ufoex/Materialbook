package com.eepiemi.materialbook.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

/** Downloads the APK and hands it to the system installer. False if the download failed. */
private suspend fun downloadAndInstall(context: Context, release: Release): Boolean = runCatching {
    val file = File(context.cacheDir, "updates/update.apk").apply { parentFile?.mkdirs() }
    withContext(Dispatchers.IO) {
        HttpClient(OkHttp).use { client ->
            client.get(release.apkUrl).bodyAsChannel().copyTo(file.outputStream())
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    context.startActivity(
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}.isSuccess

@Composable
fun UpdateDialog(release: Release, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Text(
                if (downloading) stringResource(R.string.update_downloading)
                else stringResource(R.string.update_available_desc, release.version, BuildConfig.VERSION_NAME)
            )
        },
        confirmButton = {
            TextButton(enabled = !downloading, onClick = {
                // First time: Android needs the user to allow this app to install APKs.
                if (!context.packageManager.canRequestPackageInstalls()) {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    Toast.makeText(context, R.string.update_allow_install, Toast.LENGTH_LONG).show()
                    return@TextButton
                }
                downloading = true
                scope.launch {
                    if (!downloadAndInstall(context, release)) {
                        Toast.makeText(context, R.string.update_failed, Toast.LENGTH_LONG).show()
                        openExternalUrl(context, release.apkUrl)
                    }
                    onDismiss()
                }
            }) { Text(stringResource(R.string.update_install)) }
        },
        dismissButton = { TextButton(enabled = !downloading, onClick = onDismiss) { Text(stringResource(R.string.update_later)) } }
    )
}
