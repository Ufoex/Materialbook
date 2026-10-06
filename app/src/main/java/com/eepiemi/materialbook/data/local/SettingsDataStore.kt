package com.eepiemi.materialbook.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.eepiemi.materialbook.utils.DEFAULT_MESSENGER_PACKAGE

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "materialbook_prefs")

class SettingsDataStore(private val context: Context) {
    companion object {
        val REMOVE_ADS = booleanPreferencesKey("remove_ads")
        val ENABLE_DOWNLOAD_CONTENT = booleanPreferencesKey("enable_download_content")
        val ENABLE_COPY_TO_CLIPBOARD = booleanPreferencesKey("enable_copy_to_clipboard")
        val DESKTOP_LAYOUT = booleanPreferencesKey("desktop_layout")
        val IMMERSIVE_MODE = booleanPreferencesKey("immersive_mode")
        val STICKY_NAVBAR = booleanPreferencesKey("sticky_navbar")
        val PINCH_TO_ZOOM = booleanPreferencesKey("pinch_to_zoom")
        val MATERIAL_YOU = booleanPreferencesKey("material_you")
        val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
        val HIDE_SUGGESTED = booleanPreferencesKey("hide_suggestion")
        val HIDE_REELS = booleanPreferencesKey("hide_reels")
        val HIDE_STORIES = booleanPreferencesKey("hide_stories")
        val HIDE_PEOPLE_YOU_MAY_KNOW = booleanPreferencesKey("hide_people_you_may_know")
        val HIDE_GROUPS = booleanPreferencesKey("hide_groups")
        val BRAVE_BLOCK_LIST = booleanPreferencesKey("brave_block_list")
        val PIP_ENABLED = booleanPreferencesKey("pip_enabled")
        val PIP_PORTRAIT_RATIO = stringPreferencesKey("pip_portrait_ratio")
        val PIP_LOCKSCREEN_AUDIO = booleanPreferencesKey("pip_lockscreen_audio")

        // Legacy: set by the old auto-desktop logic when it wrote desktop_layout
        // on the user's behalf. Only read by the migration below.
        val LEGACY_REVERT_DESKTOP = booleanPreferencesKey("is_revert_desktop")

        /**
         * If the legacy revert flag is set, desktop_layout was written by the
         * old auto logic, not the user: reset it and drop the flag. A
         * desktop_layout without the flag is the user's own choice and stays.
         */
        fun migrateLegacyAutoDesktop(prefs: MutablePreferences) {
            if (prefs[LEGACY_REVERT_DESKTOP] == true) {
                prefs[DESKTOP_LAYOUT] = false
            }
            prefs.remove(LEGACY_REVERT_DESKTOP)
        }
        val MESSENGER_PACKAGE = stringPreferencesKey("messenger_package")
    }

    val prefs = context.dataStore.data

    suspend fun migrateLegacyAutoDesktop() {
        if (prefs.first().contains(LEGACY_REVERT_DESKTOP)) {
            context.dataStore.edit { migrateLegacyAutoDesktop(it) }
        }
    }

    val removeAds = context.dataStore.data.map { it[REMOVE_ADS] ?: true }
    suspend fun setRemoveAds(removeAds: Boolean) {
        context.dataStore.edit { it[REMOVE_ADS] = removeAds }
    }

    val enableDownloadContent = context.dataStore.data.map { it[ENABLE_DOWNLOAD_CONTENT] ?: true }
    suspend fun setEnableDownloadContent(enableDownloadContent: Boolean) {
        context.dataStore.edit { it[ENABLE_DOWNLOAD_CONTENT] = enableDownloadContent }
    }

    val enableCopyToClipboard = context.dataStore.data.map { it[ENABLE_COPY_TO_CLIPBOARD] ?: true }
    suspend fun setEnableCopyToClipboard(enableCopyToClipboard: Boolean) {
        context.dataStore.edit { it[ENABLE_COPY_TO_CLIPBOARD] = enableCopyToClipboard }
    }

    val desktopLayout = context.dataStore.data.map { it[DESKTOP_LAYOUT] ?: false }
    suspend fun setDesktopLayout(desktopLayout: Boolean) {
        context.dataStore.edit { it[DESKTOP_LAYOUT] = desktopLayout }
    }

    val immersiveMode = context.dataStore.data.map { it[IMMERSIVE_MODE] ?: false}
    suspend fun setImmersiveMode(immersiveMode: Boolean) {
        context.dataStore.edit { it[IMMERSIVE_MODE] = immersiveMode }
    }

    val stickyNavbar = context.dataStore.data.map { it[STICKY_NAVBAR] ?: true }
    suspend fun setStickyNavbar(stickyNavbar: Boolean) {
        context.dataStore.edit { it[STICKY_NAVBAR] = stickyNavbar }
    }

    val pinchToZoom = context.dataStore.data.map { it[PINCH_TO_ZOOM] ?: false }
    suspend fun setPinchToZoom(pinchToZoom: Boolean) {
        context.dataStore.edit { it[PINCH_TO_ZOOM] = pinchToZoom }
    }

    val materialYou = context.dataStore.data.map { it[MATERIAL_YOU] ?: false }
    suspend fun setMaterialYou(materialYou: Boolean) {
        context.dataStore.edit { it[MATERIAL_YOU] = materialYou }
    }

    val amoledBlack = context.dataStore.data.map { it[AMOLED_BLACK] ?: false }
    suspend fun setAmoledBlack(amoledBlack: Boolean) {
        context.dataStore.edit { it[AMOLED_BLACK] = amoledBlack }
    }

    val hideSuggested = context.dataStore.data.map { it[HIDE_SUGGESTED] ?: false }
    suspend fun setHideSuggested(hideSuggestion: Boolean) {
        context.dataStore.edit { it[HIDE_SUGGESTED] = hideSuggestion }
    }

    val hideReels = context.dataStore.data.map { it[HIDE_REELS] ?: false }
    suspend fun setHideReels(hideReels: Boolean) {
        context.dataStore.edit { it[HIDE_REELS] = hideReels }
    }

    val hideStories = context.dataStore.data.map { it[HIDE_STORIES] ?: false }
    suspend fun setHideStories(hideStories: Boolean) {
        context.dataStore.edit { it[HIDE_STORIES] = hideStories }
    }

    val hidePeopleYouMayKnow = context.dataStore.data.map { it[HIDE_PEOPLE_YOU_MAY_KNOW] ?: false }
    suspend fun setHidePeopleYouMayKnow(hidePeopleYouMayKnow: Boolean) {
        context.dataStore.edit { it[HIDE_PEOPLE_YOU_MAY_KNOW] = hidePeopleYouMayKnow }
    }

    val hideGroups = context.dataStore.data.map { it[HIDE_GROUPS] ?: false }
    suspend fun setHideGroups(hideGroups: Boolean) {
        context.dataStore.edit { it[HIDE_GROUPS] = hideGroups }
    }

    // Off by default: entering a floating window unexpectedly is surprising
    // behavior, same reasoning as materialYou/amoledBlack defaulting off.
    val pipEnabled = context.dataStore.data.map { it[PIP_ENABLED] ?: false }
    suspend fun setPipEnabled(pipEnabled: Boolean) {
        context.dataStore.edit { it[PIP_ENABLED] = pipEnabled }
    }

    // Off by default, like PiP itself: opt-in to keep playing audio natively
    // (with a media notification) after the screen locks during PiP.
    val pipLockscreenAudio = context.dataStore.data.map { it[PIP_LOCKSCREEN_AUDIO] ?: false }
    suspend fun setPipLockscreenAudio(enabled: Boolean) {
        context.dataStore.edit { it[PIP_LOCKSCREEN_AUDIO] = enabled }
    }

    // Default "4:7" is the empirically safe portrait ratio on Samsung A56 (and similar OEMs)
    // where requesting true 9:16 causes the PiP window to overflow past the screen edge.
    val pipPortraitRatio = context.dataStore.data.map { it[PIP_PORTRAIT_RATIO] ?: "4:7" }
    suspend fun setPipPortraitRatio(ratio: String) {
        context.dataStore.edit { it[PIP_PORTRAIT_RATIO] = ratio }
    }

    val braveBlockList = context.dataStore.data.map { it[BRAVE_BLOCK_LIST] ?: true }
    suspend fun setBraveBlockList(braveBlockList: Boolean) {
        context.dataStore.edit { it[BRAVE_BLOCK_LIST] = braveBlockList }
    }

    val messengerPackage = context.dataStore.data.map { it[MESSENGER_PACKAGE] ?: DEFAULT_MESSENGER_PACKAGE }
    suspend fun setMessengerPackage(messengerPackage: String) {
        context.dataStore.edit { it[MESSENGER_PACKAGE] = messengerPackage }
    }
}