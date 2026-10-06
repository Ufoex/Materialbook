package com.eepiemi.materialbook

import com.eepiemi.materialbook.utils.isLeavingMessages
import com.eepiemi.materialbook.utils.isMessagesWebUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagesDesktopTest {
    @Test
    fun messagesUrls() {
        assertTrue(isMessagesWebUrl("https://m.facebook.com/messages/"))
        assertTrue(isMessagesWebUrl("https://www.facebook.com/messages/t/123"))
        assertFalse(isMessagesWebUrl("https://m.facebook.com/home.php"))
        assertFalse(isMessagesWebUrl("https://l.facebook.com/messages"))
    }

    @Test
    fun leavingMessages() {
        assertTrue(isLeavingMessages("https://www.facebook.com/"))
        assertTrue(isLeavingMessages("https://m.facebook.com/profile.php?id=1"))
        assertFalse(isLeavingMessages("https://www.facebook.com/messages/t/1"))
        assertFalse(isLeavingMessages("https://www.facebook.com/login/"))
        assertFalse(isLeavingMessages("https://example.com/"))
    }
}
