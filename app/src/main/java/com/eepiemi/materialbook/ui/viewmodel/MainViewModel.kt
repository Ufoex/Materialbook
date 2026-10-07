package com.eepiemi.materialbook.ui.viewmodel

import android.content.res.Resources
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eepiemi.materialbook.R
import com.eepiemi.materialbook.utils.Script
import com.eepiemi.materialbook.utils.fetchScripts
import kotlinx.coroutines.launch


class MainViewModel(
    resources: Resources,
    settings: SettingsViewModel
): ViewModel() {

    private val _themeColor = mutableStateOf(Color.Transparent)
    val themeColor: State<Color> = _themeColor

    private val _scripts = mutableStateOf<String?>(null)
    val scripts: State<String?> = _scripts

    init {
        loadScripts(
            resources,
            settings
        )
    }

    fun setThemeColor(color: Color) {
        _themeColor.value = color
    }

    private fun loadScripts(
        resources: Resources,
        settings: SettingsViewModel
    ) {
        val scripts = listOf(
            Script(true, R.raw.scripts, "scripts.js"), // always apply
            Script(settings.messagesDesktop.value, R.raw.messages_tab, "messages_tab.js"),
            Script(settings.removeAds.value, R.raw.adblock, "adblock.js"),
            Script(settings.enableDownloadContent.value, R.raw.download_content, "download_content.js"),
            Script(settings.enableCopyToClipboard.value, R.raw.copy_to_clipboard, "copy_to_clipboard.js"),
            Script(true, R.raw.copy_post_text, "copy_post_text.js"),
            Script(settings.stickyNavbar.value, R.raw.sticky_navbar, "sticky_navbar.js"),
            Script(settings.reelControls.value, R.raw.reel_controls, "reel_controls.js"),
            Script(settings.autoScrollReels.value, R.raw.autoscroll_reels, "autoscroll_reels.js"),
            // Needs the bars pinned by the sticky navbar script.
            Script(settings.stickyNavbar.value && settings.collapsingToolbar.value, R.raw.collapsing_toolbar, "collapsing_toolbar.js"),
            Script(!settings.pinchToZoom.value, R.raw.pinch_to_zoom, "pinch_to_zoom.js"),
            Script(settings.materialYou.value, R.raw.material_you, "material_you.js"),
            Script(settings.amoledBlack.value, R.raw.amoled_black, "amoled_black.js"),
            Script(settings.hideSuggested.value, R.raw.hide_suggested, "hide_suggested.js"),
            Script(settings.hideReels.value, R.raw.hide_reels, "hide_reels.js"),
            Script(settings.hideStories.value, R.raw.hide_stories, "hide_stories.js"),
            Script(settings.hidePeopleYouMayKnow.value, R.raw.hide_pymk, "hide_pymk.js"),
            Script(settings.hideGroups.value, R.raw.hide_groups, "hide_groups.js")
            // pip_video_detector.js deliberately NOT included here - it's core
            // PiP infrastructure, not a cosmetic feature, and needs to run as
            // early as possible. Bundling it with the rest means it doesn't
            // start running any sooner than the slowest script in this list,
            // even with fetchScripts' per-script timeout+concurrency, since
            // they're all evaluated together as one combined blob once EVERY
            // entry has resolved. Loaded separately, directly from the bundled
            // resource, in MaterialbookWV.kt instead.
        )

        viewModelScope.launch {
            _scripts.value =
                fetchScripts(
                    scripts = scripts,
                    fallbackContent = { resId ->
                        resources.openRawResource(resId).bufferedReader()
                            .use { it.readText() }
                    }
                )
        }
    }

    fun refresh(
        resources: Resources,
        settings: SettingsViewModel
    ) {
        clearScripts()
        loadScripts(resources, settings)
    }

    private fun clearScripts() {
        _scripts.value = null
    }
}