package com.eepiemi.materialbook.ui.components.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Diversity1
import androidx.compose.material.icons.filled.EmojiPeople
import androidx.compose.material.icons.filled.Padding
import androidx.compose.material.icons.filled.Try
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.SwipeVertical
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PanoramaWideAngle
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Pinch
import androidx.compose.material.icons.outlined.ScreenLockRotation
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eepiemi.materialbook.R
import com.eepiemi.materialbook.ui.viewmodel.SettingsViewModel
import com.eepiemi.materialbook.utils.effectiveDesktop
import com.eepiemi.materialbook.utils.rememberAutoDesktop
import com.eepiemi.materialbook.utils.openExternalUrl
import com.eepiemi.materialbook.utils.openMessenger

@Composable
fun SettingsContent(
    modifier: Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    var isOpenDialog by rememberSaveable { mutableStateOf(false) }
    var isPipRatioDialog by rememberSaveable { mutableStateOf(false) }
    var isMessengerDialog by rememberSaveable { mutableStateOf(false) }

    val removeAds = viewModel.removeAds.collectAsState()
    val braveBlockList = viewModel.braveBlockList.collectAsState()
    val enableDownloadContent = viewModel.enableDownloadContent.collectAsState()
    val enableCopyToClipboard = viewModel.enableCopyToClipboard.collectAsState()
    val desktopLayout = viewModel.desktopLayout.collectAsState()
    val immersiveMode = viewModel.immersiveMode.collectAsState()
    val stickyNavbar = viewModel.stickyNavbar.collectAsState()
    val collapsingToolbar = viewModel.collapsingToolbar.collectAsState()
    val reelControls = viewModel.reelControls.collectAsState()
    val pinchToZoom = viewModel.pinchToZoom.collectAsState()
    val materialYou = viewModel.materialYou.collectAsState()
    val amoledBlack = viewModel.amoledBlack.collectAsState()
    val pipEnabled = viewModel.pipEnabled.collectAsState()
    val pipLockscreenAudio = viewModel.pipLockscreenAudio.collectAsState()
    val pipPortraitRatio = viewModel.pipPortraitRatio.collectAsState()
    val messengerPackage = viewModel.messengerPackage.collectAsState()
    val messagesDesktop = viewModel.messagesDesktop.collectAsState()

    val isAutoDesktop = rememberAutoDesktop()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsGroup(
            items = listOf(
                SettingsItem(
                    icon = Icons.Outlined.Message,
                    title = stringResource(R.string.open_messenger_title),
                    supportingText = stringResource(R.string.open_messenger_desc),
                    isActive = null,
                    onClick = {
                        val ok = openMessenger(context, "https://m.me/", messengerPackage.value)
                        if (!ok) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.messenger_redirect_toast),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                ),
                SettingsItem(
                    icon = Icons.Outlined.Message,
                    title = stringResource(R.string.messages_desktop_title),
                    supportingText = stringResource(R.string.messages_desktop_desc),
                    isActive = messagesDesktop.value,
                    onClick = { viewModel.setMessagesDesktop(!messagesDesktop.value) },
                ),
                SettingsItem(
                    icon = Icons.Outlined.Message,
                    title = stringResource(R.string.messenger_title),
                    supportingText = messengerPackage.value,
                    isActive = null,
                    onClick = { isMessengerDialog = true },
                )
            )
        )

        SettingsGroup(
            items = listOf(
                SettingsItem(
                    icon = Icons.Outlined.Shield,
                    title = stringResource(R.string.remove_ads_title),
                    supportingText = stringResource(R.string.hide_sponsored_ads_from_your_feed),
                    isActive = removeAds.value,
                    onClick = { viewModel.setRemoveAds(!removeAds.value) },
                ),
                SettingsItem(
                    icon = Icons.Outlined.Block,
                    title = stringResource(R.string.brave_blocklist_title),
                    supportingText = stringResource(R.string.brave_blocklist_supporting),
                    isActive = braveBlockList.value,
                    onClick = { viewModel.setBraveBlockList(!braveBlockList.value) },
                ),
                SettingsItem(
                    icon = Icons.Outlined.FileDownload,
                    title = stringResource(R.string.download_content_title),
                    supportingText = stringResource(R.string.enable_download_button_on_media_view),
                    isActive = enableDownloadContent.value,
                    onClick = { viewModel.setEnableDownloadContent(!enableDownloadContent.value) },
                ),
                SettingsItem(
                    icon = Icons.Outlined.ContentCopy,
                    title = stringResource(R.string.copy_to_clipboard_title),
                    supportingText = stringResource(R.string.enable_copy_to_clipboard_button_on_media_view),
                    isActive = enableCopyToClipboard.value,
                    onClick = { viewModel.setEnableCopyToClipboard(!enableCopyToClipboard.value) },
                ),
                SettingsItem(
                    icon = Icons.Outlined.GridView,
                    title = stringResource(R.string.customize_feed_title),
                    supportingText = stringResource(R.string.customize_feed),
                    isActive = null,
                    onClick = { isOpenDialog = true },
                ),
            )
        )

        SettingsGroup(
            items = listOf(
                SettingsItem(
                    icon = Icons.Outlined.Pinch,
                    title = stringResource(R.string.pinch_to_zoom_title),
                    supportingText = stringResource(R.string.use_two_fingers_to_zoom_in_or_out),
                    isActive = pinchToZoom.value,
                    onClick = { viewModel.setPinchToZoom(!pinchToZoom.value) }
                ),
                SettingsItem(
                    icon = Icons.Outlined.DesktopWindows,
                    title = stringResource(R.string.desktop_layout_title),
                    supportingText = stringResource(R.string.force_desktop_layout_may_not_be_suitable_for_smaller_display),
                    // Large screens are always desktop: shown on and locked.
                    // On phones this is a plain toggle.
                    isActive = effectiveDesktop(desktopLayout.value, isAutoDesktop),
                    onClick = { if (!isAutoDesktop) viewModel.setDesktopLayout(!desktopLayout.value) }
                ),
                SettingsItem(
                    icon = Icons.Outlined.PanoramaWideAngle,
                    title = stringResource(R.string.immersive_mode_title),
                    supportingText = stringResource(R.string.hide_system_bars_for_a_fullscreen_experience),
                    isActive = immersiveMode.value,
                    onClick = { viewModel.setImmersiveMode(!immersiveMode.value) }
                ),
                SettingsItem(
                    icon = Icons.Default.Padding,
                    title = stringResource(R.string.sticky_navbar_title),
                    supportingText = stringResource(R.string.keep_the_navigation_bar_visible_while_scrolling),
                    isActive = stickyNavbar.value,
                    onClick = { viewModel.setStickyNavbar(!stickyNavbar.value) }
                ),
                SettingsItem(
                    icon = Icons.Outlined.PlayCircle,
                    title = stringResource(R.string.reel_controls_title),
                    supportingText = stringResource(R.string.reel_controls_desc),
                    isActive = reelControls.value,
                    onClick = { viewModel.setReelControls(!reelControls.value) }
                ),
                SettingsItem(
                    icon = Icons.Outlined.SwipeVertical,
                    title = stringResource(R.string.collapsing_toolbar_title),
                    supportingText = stringResource(R.string.collapsing_toolbar_desc),
                    isActive = collapsingToolbar.value,
                    onClick = { viewModel.setCollapsingToolbar(!collapsingToolbar.value) }
                ),
                SettingsItem(
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.material_you_title),
                    supportingText = stringResource(R.string.enable_material_you_theming_for_facebook),
                    isActive = materialYou.value,
                    onClick = { viewModel.setMaterialYou(!materialYou.value) }
                ),
                SettingsItem(
                    icon = Icons.Outlined.Circle,
                    title = stringResource(R.string.amoled_black_title),
                    supportingText = stringResource(R.string.enable_pure_black_theme_for_amoled_displays),
                    isActive = amoledBlack.value,
                    onClick = { viewModel.setAmoledBlack(!amoledBlack.value) }
                ),
                SettingsItem(
                    icon = Icons.Outlined.PictureInPictureAlt,
                    title = stringResource(R.string.pip_title),
                    supportingText = stringResource(R.string.shrink_into_a_floating_window_when_leaving_while_a_video_plays),
                    isActive = pipEnabled.value,
                    onClick = { viewModel.setPipEnabled(!pipEnabled.value) }
                )
            )
        )

        // PiP ratio row — only shown while PiP is enabled so it doesn't clutter
        // the settings for users who never use PiP.
        if (pipEnabled.value) {
            val deviceLabel = run {
                val mfr = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
                val model = Build.MODEL
                if (model.startsWith(mfr, ignoreCase = true)) model else "$mfr $model"
            }
            SettingsGroup(
                items = listOf(
                    SettingsItem(
                        icon = Icons.Outlined.AspectRatio,
                        title = stringResource(R.string.pip_ratio_title),
                        supportingText = "$deviceLabel · ${pipPortraitRatio.value}",
                        isActive = null,
                        onClick = { isPipRatioDialog = true }
                    ),
                    // No POST_NOTIFICATIONS prompt here: notifications for a
                    // media session are exempt from that permission on API 33+
                    // (Android notification-permission docs), so the media
                    // notification and lock-screen controls show without it.
                    SettingsItem(
                        icon = Icons.Outlined.Headphones,
                        title = stringResource(R.string.pip_lockscreen_audio_title),
                        supportingText = stringResource(R.string.pip_lockscreen_audio_summary),
                        isActive = pipLockscreenAudio.value,
                        onClick = { viewModel.setPipLockscreenAudio(!pipLockscreenAudio.value) }
                    )
                )
            )
        }

    }

    if (isOpenDialog) {
        HideOptionsDialog(
            viewModel = viewModel,
            onDismiss = {
                @Suppress("AssignedValueIsNeverRead")
                isOpenDialog = false
            }
        )
    }

    if (isPipRatioDialog) {
        PipRatioDialog(
            currentRatio = viewModel.pipPortraitRatio.collectAsState().value,
            onSelect = { ratio ->
                viewModel.setPipPortraitRatio(ratio)
                @Suppress("AssignedValueIsNeverRead")
                isPipRatioDialog = false
            },
            onDismiss = {
                @Suppress("AssignedValueIsNeverRead")
                isPipRatioDialog = false
            }
        )
    }
    if (isMessengerDialog) {
        MessengerPackageDialog(
            current = messengerPackage.value,
            onDismiss = { isMessengerDialog = false },
            onSave = {
                viewModel.setMessengerPackage(it)
                isMessengerDialog = false
            },
        )
    }
}

@Composable
private fun MessengerPackageDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable(current) { mutableStateOf(current) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(color = MaterialTheme.colorScheme.surfaceContainer)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.messenger_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.trim() },
                label = { Text(stringResource(R.string.messenger_package_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    text = com.eepiemi.materialbook.utils.DEFAULT_MESSENGER_PACKAGE
                }) {
                    Text(stringResource(R.string.messenger_package_reset))
                }
                TextButton(onClick = { onSave(text) }) {
                    Text(stringResource(R.string.messenger_package_save))
                }
            }
        }
    }

}

@Composable
private fun HideOptionsDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {

    val hideSuggested = viewModel.hideSuggested.collectAsState()
    val hideReels = viewModel.hideReels.collectAsState()
    val hideStories = viewModel.hideStories.collectAsState()
    val hidePeopleYouMayKnow = viewModel.hidePeopleYouMayKnow.collectAsState()
    val hideGroups = viewModel.hideGroups.collectAsState()

    Dialog(
        onDismissRequest = { onDismiss() }
    ) {

        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(color = MaterialTheme.colorScheme.surfaceContainer)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            HideDialogItem(
                SettingsItem(
                    icon = Icons.Filled.Try,
                    title = stringResource(R.string.suggested_posts),
                    isActive = hideSuggested.value,
                    onClick = { viewModel.setHideSuggested(!hideSuggested.value) }
                )
            )

            HideDialogItem(
                SettingsItem(
                    icon = Icons.Filled.Camera,
                    title = stringResource(R.string.reels),
                    isActive = hideReels.value,
                    onClick = { viewModel.setHideReels(!hideReels.value) }
                )
            )

            HideDialogItem(
                SettingsItem(
                    icon = Icons.Filled.BurstMode,
                    title = stringResource(R.string.stories),
                    isActive = hideStories.value,
                    onClick = { viewModel.setHideStories(!hideStories.value) }
                )
            )

            if (!effectiveDesktop(viewModel.desktopLayout.collectAsState().value, rememberAutoDesktop())) {
                HideDialogItem(
                    SettingsItem(
                        icon = Icons.Filled.EmojiPeople,
                        title = stringResource(R.string.people_you_may_know),
                        isActive = hidePeopleYouMayKnow.value,
                        onClick = { viewModel.setHidePeopleYouMayKnow(!hidePeopleYouMayKnow.value) }
                    )
                )

                HideDialogItem(
                    SettingsItem(
                        icon = Icons.Filled.Diversity1,
                        title = stringResource(R.string.groups),
                        isActive = hideGroups.value,
                        onClick = { viewModel.setHideGroups(!hideGroups.value) }
                    )
                )
            }
        }
    }

}

@Composable
private fun HideDialogItem(item: SettingsItem) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .clickable { item.onClick() }
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            item.icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(0.6F)
        )

        Text(
            text = item.title,
            color = MaterialTheme.colorScheme.onBackground.copy(0.6F),
            fontSize = 18.sp,
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1F)
        )

        item.isActive?.let {
            Switch(
                checked = item.isActive,
                onCheckedChange = { item.onClick() },
                modifier = Modifier
            )
        }
    }
}

private val PIP_RATIO_PRESETS = listOf(
    "4:7",
    "2:3",
    "3:4",
    "9:16",
)

@Composable
private fun PipRatioDialog(
    currentRatio: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.pip_ratio_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            PIP_RATIO_PRESETS.forEach { ratio ->
                val label = when (ratio) {
                    "4:7"  -> stringResource(R.string.pip_ratio_4_7)
                    "2:3"  -> stringResource(R.string.pip_ratio_2_3)
                    "3:4"  -> stringResource(R.string.pip_ratio_3_4)
                    "9:16" -> stringResource(R.string.pip_ratio_9_16)
                    else   -> ratio
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .clickable { onSelect(ratio) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = ratio == currentRatio,
                        onClick = { onSelect(ratio) }
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}