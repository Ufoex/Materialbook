package com.eepiemi.materialbook.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

data class Release(val version: String, val pageUrl: String, val apkUrl: String, val notes: String = "")

private const val LATEST = "https://api.github.com/repos/Ufoex/Materialbook/releases/latest"

/** Newest GitHub release if it is newer than this build, else null. Null also on any failure. */
suspend fun checkForUpdate(): Release? = runCatching {
    HttpClient(OkHttp).use { client ->
        val json = JSONObject(client.get(LATEST).bodyAsText())
        val version = json.getString("tag_name").removePrefix("v")
        val apk = json.getJSONArray("assets").getJSONObject(0).getString("browser_download_url")
        Release(version, json.getString("html_url"), apk, releaseNotes(json.optString("body"))).takeIf { isNewer(version, BuildConfig.VERSION_NAME) }
    }
}.getOrNull()

/** Release body (markdown) without the boilerplate lines (what the build includes, the compare link). */
fun releaseNotes(body: String): String =
    body.lines()
        .filterNot { it.startsWith("Includes everything") || it.contains("Full Changelog") }
        .joinToString("\n") { it.trimEnd() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

sealed interface NoteLine {
    data class Heading(val text: String) : NoteLine
    data class Bullet(val text: String) : NoteLine
    data class Para(val text: String) : NoteLine
}

/** The few markdown shapes the release notes use: `## heading`, `- bullet`, plain paragraphs. */
fun parseNotes(notes: String): List<NoteLine> = notes.lines().filter { it.isNotBlank() }.map {
    when {
        it.startsWith("#") -> NoteLine.Heading(it.trimStart('#').trim())
        Regex("^[-*]\\s+").containsMatchIn(it) -> NoteLine.Bullet(it.replace(Regex("^[-*]\\s+"), ""))
        else -> NoteLine.Para(it.trim())
    }
}

/** `**bold**` and `code` spans of one notes line. */
private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    Regex("\\*\\*(.+?)\\*\\*|`(.+?)`").let { re ->
        var last = 0
        for (m in re.findAll(text)) {
            append(text.substring(last, m.range.first))
            val bold = m.groups[1]?.value
            if (bold != null) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
            else withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(m.groups[2]!!.value) }
            last = m.range.last + 1
        }
        append(text.substring(last))
    }
}

@Composable
private fun NotesList(notes: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        parseNotes(notes).forEach { line ->
            when (line) {
                is NoteLine.Heading -> Text(
                    inline(line.text),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                is NoteLine.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("\u2022", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(inline(line.text), style = MaterialTheme.typography.bodyMedium)
                }
                is NoteLine.Para -> Text(inline(line.text), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

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
        icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.update_available_desc, release.version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (downloading) {
                    Text(stringResource(R.string.update_downloading), style = MaterialTheme.typography.labelLarge)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else if (release.notes.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Box(
                            Modifier
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) { NotesList(release.notes) }
                    }
                }
            }
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
