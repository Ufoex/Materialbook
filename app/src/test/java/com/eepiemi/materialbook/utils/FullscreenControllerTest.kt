package com.eepiemi.materialbook.utils

import android.view.View
import android.webkit.WebChromeClient.CustomViewCallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FullscreenControllerTest {

    private class FakeHost : FullscreenController.Host {
        val attached = mutableListOf<View>()
        val detached = mutableListOf<View>()
        override fun attach(view: View) { attached += view }
        override fun detach(view: View) { detached += view }
    }

    private class CountingCallback : CustomViewCallback {
        var hiddenCount = 0
        override fun onCustomViewHidden() { hiddenCount++ }
    }

    private fun newView() = View(RuntimeEnvironment.getApplication())

    @Test
    fun show_attachesViewAndEntersFullscreen() {
        val host = FakeHost()
        val controller = FullscreenController(host)
        val view = newView()
        val callback = CountingCallback()

        assertTrue(controller.show(view, callback))
        assertTrue(controller.isFullscreen)
        assertEquals(listOf(view), host.attached)
        assertEquals(0, callback.hiddenCount)
    }

    @Test
    fun doubleShow_dismissesSecondViewAndKeepsFirst() {
        val host = FakeHost()
        val controller = FullscreenController(host)
        val first = newView()
        val firstCallback = CountingCallback()
        val secondCallback = CountingCallback()
        controller.show(first, firstCallback)

        assertFalse(controller.show(newView(), secondCallback))
        assertEquals(1, secondCallback.hiddenCount)
        assertEquals(0, firstCallback.hiddenCount)
        assertEquals(listOf(first), host.attached)

        controller.hide()
        assertSame(first, host.detached.single())
    }

    @Test
    fun hideWithoutShow_isNoOp() {
        val host = FakeHost()
        val controller = FullscreenController(host)

        assertFalse(controller.hide())
        assertTrue(host.detached.isEmpty())
        assertFalse(controller.isFullscreen)
    }

    @Test
    fun hide_detachesAndCallsCallbackExactlyOnce() {
        val host = FakeHost()
        val controller = FullscreenController(host)
        val view = newView()
        val callback = CountingCallback()
        controller.show(view, callback)

        // Back press, then WebView's own onHideCustomView, then disposal.
        assertTrue(controller.hide())
        assertFalse(controller.hide())
        assertFalse(controller.hide())

        assertEquals(1, callback.hiddenCount)
        assertEquals(listOf(view), host.detached)
        assertFalse(controller.isFullscreen)
    }

    @Test
    fun callbackReenteringHide_isSafe() {
        // WebView may call onHideCustomView synchronously from onCustomViewHidden.
        val host = FakeHost()
        val controller = FullscreenController(host)
        var reentrantResult: Boolean? = null
        val callback = object : CustomViewCallback {
            override fun onCustomViewHidden() { reentrantResult = controller.hide() }
        }
        controller.show(newView(), callback)

        controller.hide()

        assertEquals(false, reentrantResult)
        assertEquals(1, host.detached.size)
    }

    @Test
    fun canShowAgainAfterHide() {
        val host = FakeHost()
        val controller = FullscreenController(host)
        controller.show(newView(), CountingCallback())
        controller.hide()

        assertTrue(controller.show(newView(), CountingCallback()))
        assertEquals(2, host.attached.size)
    }
}
