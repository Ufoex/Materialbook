package com.eepiemi.materialbook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eepiemi.materialbook.R
import com.eepiemi.materialbook.utils.SharedContent

/** Where something shared to the app (or from Facebook's share sheet) should go. */
@Composable
fun ShareDialog(
    content: SharedContent,
    messengerAppAvailable: Boolean,
    canPostLink: Boolean,
    onMessages: () -> Unit,
    onMessengerApp: () -> Unit,
    onPost: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Send, contentDescription = null) },
        title = { Text(stringResource(R.string.share_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        content.text?.let { Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) }
                        if (content.files.isNotEmpty()) {
                            Text(
                                pluralStringResource(R.plurals.share_files, content.files.size, content.files.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                FilledTonalButton(onClick = onMessages, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Outlined.Message, contentDescription = null)
                    Text(stringResource(R.string.share_in_messages), Modifier.padding(start = 8.dp))
                }
                if (messengerAppAvailable) {
                    FilledTonalButton(onClick = onMessengerApp, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Outlined.Message, contentDescription = null)
                        Text(stringResource(R.string.share_in_messenger_app), Modifier.padding(start = 8.dp))
                    }
                }
                if (canPostLink) {
                    FilledTonalButton(onClick = onPost, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Language, contentDescription = null)
                        Text(stringResource(R.string.share_on_facebook), Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.share_cancel)) } },
    )
}
