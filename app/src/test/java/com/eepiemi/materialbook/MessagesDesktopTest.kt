package com.eepiemi.materialbook

import com.eepiemi.materialbook.utils.isDesktopMessagesUrl
import com.eepiemi.materialbook.utils.isLeavingMessages
import com.eepiemi.materialbook.utils.isMessagesWebUrl
import com.eepiemi.materialbook.utils.messagesDesktopUrl
import org.junit.Assert.assertEquals
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

    @Test
    fun deepLinksKeepTheirConversation() {
        val inbox = "https://www.facebook.com/messages/"
        assertEquals("https://www.facebook.com/messages/t/12345", messagesDesktopUrl("https://www.facebook.com/messages/t/12345"))
        assertEquals("https://www.facebook.com/messages/t/12345", messagesDesktopUrl("https://m.facebook.com/messages/t/12345/?ref=notif"))
        assertEquals("https://www.facebook.com/messages/e2ee/t/777", messagesDesktopUrl("https://www.facebook.com/messages/e2ee/t/777/"))
        assertEquals("https://www.facebook.com/messages/t/someone", messagesDesktopUrl("https://m.me/someone"))
        assertEquals("https://www.facebook.com/messages/t/987", messagesDesktopUrl("https://www.messenger.com/t/987/"))
        assertEquals("https://www.facebook.com/messages/t/42", messagesDesktopUrl("fb-messenger://user/42"))
        assertEquals(inbox, messagesDesktopUrl("https://m.me/j/abcdef"))
        assertEquals(inbox, messagesDesktopUrl("https://m.me/"))
        assertEquals(inbox, messagesDesktopUrl("https://m.facebook.com/messages/"))
        assertEquals(inbox, messagesDesktopUrl("fb-messenger://threads"))
        assertEquals(inbox, messagesDesktopUrl("intent://x#Intent;package=com.facebook.orca;end"))
    }

    @Test
    fun desktopMessagesPage() {
        assertTrue(isDesktopMessagesUrl("https://www.facebook.com/messages/t/1"))
        assertFalse(isDesktopMessagesUrl("https://m.facebook.com/messages/t/1"))
        assertFalse(isDesktopMessagesUrl("https://www.facebook.com/home.php"))
    }
}
