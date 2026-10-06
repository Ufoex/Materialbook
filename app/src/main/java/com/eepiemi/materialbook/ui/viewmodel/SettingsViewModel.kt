package com.eepiemi.materialbook.ui.viewmodel

import android.app.Application
import android.util.Rational
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eepiemi.materialbook.data.local.SettingsDataStore
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.AMOLED_BLACK
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.DESKTOP_LAYOUT
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.ENABLE_COPY_TO_CLIPBOARD
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.ENABLE_DOWNLOAD_CONTENT
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.BRAVE_BLOCK_LIST
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.HIDE_GROUPS
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.HIDE_PEOPLE_YOU_MAY_KNOW
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.HIDE_REELS
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.HIDE_STORIES
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.HIDE_SUGGESTED
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.IMMERSIVE_MODE
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.MATERIAL_YOU
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.PINCH_TO_ZOOM
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.PIP_ENABLED
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.PIP_LOCKSCREEN_AUDIO
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.PIP_PORTRAIT_RATIO
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.REMOVE_ADS
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.STICKY_NAVBAR
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.MESSENGER_PACKAGE
import com.eepiemi.materialbook.utils.DEFAULT_MESSENGER_PACKAGE
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val dataStore: SettingsDataStore = SettingsDataStore(application)

    // Migrate before the first read so desktopLayout's initial value already
    // reflects the unstuck state on installs hit by the old auto-desktop logic.
    private val initialPrefs = runBlocking {
        dataStore.migrateLegacyAutoDesktop()
        dataStore.prefs.first()
    }

    val removeAds = dataStore.removeAds.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[REMOVE_ADS] ?: true,
        started = SharingStarted.WhileSubscribed()
    )
    val enableDownloadContent = dataStore.enableDownloadContent.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[ENABLE_DOWNLOAD_CONTENT] ?: true,
        started = SharingStarted.WhileSubscribed()
    )
    val enableCopyToClipboard = dataStore.enableCopyToClipboard.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[ENABLE_COPY_TO_CLIPBOARD] ?: true,
        started = SharingStarted.WhileSubscribed()
    )
    val desktopLayout = dataStore.desktopLayout.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[DESKTOP_LAYOUT] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val immersiveMode = dataStore.immersiveMode.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[IMMERSIVE_MODE] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val stickyNavbar = dataStore.stickyNavbar.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[STICKY_NAVBAR] ?: true,
        started = SharingStarted.WhileSubscribed()
    )
    val pinchToZoom = dataStore.pinchToZoom.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[PINCH_TO_ZOOM] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val materialYou = dataStore.materialYou.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[MATERIAL_YOU] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val amoledBlack = dataStore.amoledBlack.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[AMOLED_BLACK] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val hideSuggested = dataStore.hideSuggested.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[HIDE_SUGGESTED] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val hideReels = dataStore.hideReels.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[HIDE_REELS] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val hideStories = dataStore.hideStories.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[HIDE_STORIES] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val hidePeopleYouMayKnow = dataStore.hidePeopleYouMayKnow.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[HIDE_PEOPLE_YOU_MAY_KNOW] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val hideGroups = dataStore.hideGroups.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[HIDE_GROUPS] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val braveBlockList = dataStore.braveBlockList.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[BRAVE_BLOCK_LIST] ?: true,
        started = SharingStarted.WhileSubscribed()
    )
    val messengerPackage = dataStore.messengerPackage.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[MESSENGER_PACKAGE] ?: DEFAULT_MESSENGER_PACKAGE,
        started = SharingStarted.WhileSubscribed()
    )
    val pipEnabled = dataStore.pipEnabled.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[PIP_ENABLED] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val pipLockscreenAudio = dataStore.pipLockscreenAudio.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[PIP_LOCKSCREEN_AUDIO] ?: false,
        started = SharingStarted.WhileSubscribed()
    )
    val pipPortraitRatio = dataStore.pipPortraitRatio.stateIn(
        scope = viewModelScope,
        initialValue = initialPrefs[PIP_PORTRAIT_RATIO] ?: "4:7",
        started = SharingStarted.WhileSubscribed()
    )

    fun setRemoveAds(removeAds: Boolean) {
        viewModelScope.launch {
            dataStore.setRemoveAds(removeAds)
        }
    }

    fun setEnableDownloadContent(enableDownloadContent: Boolean) {
        viewModelScope.launch {
            dataStore.setEnableDownloadContent(enableDownloadContent)
        }
    }

    fun setEnableCopyToClipboard(enableCopyToClipboard: Boolean) {
        viewModelScope.launch {
            dataStore.setEnableCopyToClipboard(enableCopyToClipboard)
        }
    }

    fun setDesktopLayout(desktopLayout: Boolean) {
        viewModelScope.launch {
            dataStore.setDesktopLayout(desktopLayout)
        }
    }

    fun setImmersiveMode(immersiveMode: Boolean) {
        viewModelScope.launch {
            dataStore.setImmersiveMode(immersiveMode)
        }
    }

    fun setStickyNavbar(stickyNavbar: Boolean) {
        viewModelScope.launch {
            dataStore.setStickyNavbar(stickyNavbar)
        }
    }

    fun setPinchToZoom(pinchToZoom: Boolean) {
        viewModelScope.launch {
            dataStore.setPinchToZoom(pinchToZoom)
        }
    }

    fun setMaterialYou(materialYou: Boolean) {
        viewModelScope.launch {
            dataStore.setMaterialYou(materialYou)
        }
    }

    fun setAmoledBlack(amoledBlack: Boolean) {
        viewModelScope.launch {
            dataStore.setAmoledBlack(amoledBlack)
        }
    }

    fun setHideSuggested(hideSuggested: Boolean) {
        viewModelScope.launch {
            dataStore.setHideSuggested(hideSuggested)
        }
    }

    fun setHideReels(hideReels: Boolean) {
        viewModelScope.launch {
            dataStore.setHideReels(hideReels)
        }
    }

    fun setHideStories(hideStories: Boolean) {
        viewModelScope.launch {
            dataStore.setHideStories(hideStories)
        }
    }

    fun setHidePeopleYouMayKnow(hidePeopleYouMayKnow: Boolean) {
        viewModelScope.launch {
            dataStore.setHidePeopleYouMayKnow(hidePeopleYouMayKnow)
        }
    }

    fun setHideGroups(hideGroups: Boolean) {
        viewModelScope.launch {
            dataStore.setHideGroups(hideGroups)
        }
    }

    fun setMessengerPackage(messengerPackage: String) {
        viewModelScope.launch {
            dataStore.setMessengerPackage(messengerPackage.ifBlank { DEFAULT_MESSENGER_PACKAGE })
        }
    }

    fun setBraveBlockList(braveBlockList: Boolean) {
        viewModelScope.launch {
            dataStore.setBraveBlockList(braveBlockList)
        }
    }

    fun setPipEnabled(pipEnabled: Boolean) {
        viewModelScope.launch {
            dataStore.setPipEnabled(pipEnabled)
        }
    }

    fun setPipLockscreenAudio(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setPipLockscreenAudio(enabled)
        }
    }

    fun setPipPortraitRatio(ratio: String) {
        viewModelScope.launch {
            dataStore.setPipPortraitRatio(ratio)
        }
    }

    fun parsedPipRational(): Rational = parsedPipRational(pipPortraitRatio.value)

    fun pipRationalForVideo(videoWidth: Int, videoHeight: Int): Rational =
        calculatePipRational(videoWidth, videoHeight, parsedPipRational())

    companion object {
        private val MIN_PIP_ASPECT_RATIO = 100f / 239f
        private val MAX_PIP_ASPECT_RATIO = 239f / 100f

        /**
         * Converts a stored "W:H" string to a [Rational] suitable for
         * [android.app.PictureInPictureParams.Builder.setAspectRatio].
         * Falls back to Rational(4, 7) on any parse error so a corrupt pref
         * can never crash the app.
         */
        fun parsedPipRational(stored: String): Rational {
            return try {
                val parts = stored.split(":")
                Rational(parts[0].trim().toInt(), parts[1].trim().toInt())
            } catch (_: Exception) {
                Rational(4, 7)
            }
        }

        /**
         * Uses the selected ratio for portrait video and the detected source
         * ratio for landscape video. Extreme landscape ratios are clamped to
         * Android's documented PiP range; unknown dimensions use 16:9.
         */
        fun calculatePipRational(
            videoWidth: Int,
            videoHeight: Int,
            portraitRatio: Rational,
        ): Rational {
            if (videoWidth <= 0 || videoHeight <= 0) {
                return Rational(16, 9)
            }
            if (videoHeight > videoWidth) {
                return portraitRatio
            }

            val sourceRatio = videoWidth.toFloat() / videoHeight.toFloat()
            return when {
                sourceRatio < MIN_PIP_ASPECT_RATIO -> Rational(100, 239)
                sourceRatio > MAX_PIP_ASPECT_RATIO -> Rational(239, 100)
                else -> reducedRational(videoWidth, videoHeight)
            }
        }

        private fun reducedRational(numerator: Int, denominator: Int): Rational {
            var a = numerator
            var b = denominator
            while (b != 0) {
                val remainder = a % b
                a = b
                b = remainder
            }
            return Rational(numerator / a, denominator / a)
        }
    }
}