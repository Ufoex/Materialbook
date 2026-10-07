package com.eepiemi.materialbook

import com.eepiemi.materialbook.utils.MessagesLayerRoute
import com.eepiemi.materialbook.utils.isDesktopMessagesUrl
import com.eepiemi.materialbook.utils.isMessagesLink
import com.eepiemi.materialbook.utils.messagesDesktopUrl
import com.eepiemi.materialbook.utils.messagesLayerRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagesDesktopTest {
    @Test
    fun messagesLinks() {
        assertTrue(isMessagesLink("https://m.facebook.com/messages/"))
        assertTrue(isMessagesLink("https://www.facebook.com/messages/t/123"))
        assertTrue(isMessagesLink("https://m.me/someone"))
        assertTrue(isMessagesLink("https://www.messenger.com/"))
        assertTrue(isMessagesLink("fb-messenger://threads"))
        assertTrue(isMessagesLink("intent://x#Intent;scheme=fb-messenger;package=com.facebook.orca;end"))
        assertFalse(isMessagesLink("https://m.facebook.com/home.php"))
        assertFalse(isMessagesLink("https://l.facebook.com/messages"))
        assertFalse(isMessagesLink("https://example.com/messages"))
    }

    @Test
    fun layerRoutes() {
        val inbox = "https://www.facebook.com/messages/"
        // The desktop Messages page itself, and what it may bounce through, stay in the layer.
        assertEquals(MessagesLayerRoute.Allow, messagesLayerRoute("https://www.facebook.com/messages/e2ee/t/1", true))
        assertEquals(MessagesLayerRoute.Allow, messagesLayerRoute("https://www.facebook.com/login/?next=x", true))
        assertEquals(MessagesLayerRoute.Allow, messagesLayerRoute("https://www.facebook.com/checkpoint/1", true))
        // Subframes are never routed.
        assertEquals(MessagesLayerRoute.Allow, messagesLayerRoute("https://www.fbsbx.com/maw_proxy_page/", false))
        // Other forms of a Messages link open their desktop equivalent in the layer.
        assertEquals(MessagesLayerRoute.Remap("https://www.facebook.com/messages/t/someone"), messagesLayerRoute("https://m.me/someone", true))
        assertEquals(MessagesLayerRoute.Remap(inbox), messagesLayerRoute("https://m.facebook.com/messages/", true))
        // What the m.facebook.com Messages tab actually sends.
        assertEquals(MessagesLayerRoute.Remap(inbox), messagesLayerRoute("fb-messenger://threads?vcuid=1&entry_point=jewel", true))
        // Regular Facebook pages go back to the main (mobile) view.
        assertEquals(MessagesLayerRoute.OpenInMain("https://www.facebook.com/"), messagesLayerRoute("https://www.facebook.com/", true))
        assertEquals(MessagesLayerRoute.OpenInMain("https://www.facebook.com/profile.php?id=1"), messagesLayerRoute("https://www.facebook.com/profile.php?id=1", true))
        // Everything else is external, outbound l.facebook.com redirects included.
        assertEquals(MessagesLayerRoute.External("https://example.com/"), messagesLayerRoute("https://example.com/", true))
        assertEquals(MessagesLayerRoute.External("https://l.facebook.com/l.php?u=x"), messagesLayerRoute("https://l.facebook.com/l.php?u=x", true))
        assertEquals(MessagesLayerRoute.External("tel:123"), messagesLayerRoute("tel:123", true))
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
