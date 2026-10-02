package com.eepiemi.materialbook

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.eepiemi.materialbook.audio.LockScreenAudioService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// Framework internal bitflag for android:supportsPictureInPicture
private const val FLAG_SUPPORTS_PICTURE_IN_PICTURE = 0x00400000

/**
 * Guards android:supportsPictureInPicture on MainActivity from silently
 * regressing; nothing else in the suite would catch a future manifest
 * edit dropping it; PiP would just stop working with no obvious signal why.
 */
@RunWith(AndroidJUnit4::class)
class PipManifestTest {

    @Test
    fun mainActivitySupportsPictureInPicture() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val componentName = ComponentName(context, MainActivity::class.java)
        val activityInfo = context.packageManager.getActivityInfo(componentName, 0)
        val supportsPip = (activityInfo.flags and FLAG_SUPPORTS_PICTURE_IN_PICTURE) != 0

        assertTrue(
            "MainActivity must declare android:supportsPictureInPicture",
            supportsPip
        )
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q) // foregroundServiceType is API 29+
    fun lockScreenAudioServiceIsMediaPlaybackForegroundService() {
        // Android 14+ requires the mediaPlayback type for the service to be
        // promoted to the foreground; without it lock-screen audio would
        // crash at the first handoff rather than fail visibly here.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val componentName = ComponentName(context, LockScreenAudioService::class.java)
        val serviceInfo = context.packageManager.getServiceInfo(componentName, 0)

        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            serviceInfo.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        assertTrue("Media3 controllers must be able to bind", serviceInfo.exported)
    }
}