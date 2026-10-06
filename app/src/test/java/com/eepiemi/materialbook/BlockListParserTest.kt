package com.eepiemi.materialbook

import com.eepiemi.materialbook.utils.BraveBlockList
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockListParserTest {

    private fun load(vararg lines: String) {
        BraveBlockList.setEnabled(true)
        BraveBlockList.loadFromText(lines.joinToString("\n"))
    }

    @Test
    fun blocksTrackersAndFacebookTrackers() {
        load(
            "! comment",
            "[Adblock Plus 2.0]",
            "||doubleclick.net^",
            "||facebook.com/tr^",
            "||ads.example.com/banner^"
        )
        assertTrue(BraveBlockList.shouldBlock("https://stats.doubleclick.net/pagead/x", false))
        assertTrue(BraveBlockList.shouldBlock("https://www.facebook.com/tr?id=1&ev=pageview", false))
        assertTrue(BraveBlockList.shouldBlock("https://ads.example.com/banner/123", false))
    }

    @Test
    fun respectsExceptions() {
        load(
            "||facebook.com/ajax/mercury/^",
            "@@||facebook.com/ajax/mercury/^"
        )
        assertFalse(BraveBlockList.shouldBlock("https://www.facebook.com/ajax/mercury/send/", false))
    }

    @Test
    fun neverBlocksMainFacebookNavigation() {
        load("||facebook.com/tr^", "||connect.facebook.net^")
        assertFalse(BraveBlockList.shouldBlock("https://www.facebook.com/", true))
        // path-based first-party tracker still blocked as a sub-resource
        assertTrue(BraveBlockList.shouldBlock("https://www.facebook.com/tr?x=1", false))
    }

    @Test
    fun skipsWholeDomainBlocksOfFirstPartyFacebookHosts() {
        load("||connect.facebook.net^")
        // connect.facebook.net is first-party-ish and a whole-domain rule -> skipped (safe)
        assertFalse(BraveBlockList.shouldBlock("https://connect.facebook.net/en_US/sdk.js", false))
    }

    @Test
    fun ignoresCosmeticScriptletAndSiteSpecificRules() {
        load(
            "##.ad-banner",
            "#@#.sponsored",
            "#%#//scriptlet(...)",
            "||tracker.com^\$domain=example.org",
            "example.com##.ads"
        )
        assertFalse(BraveBlockList.shouldBlock("https://tracker.com/x", false))
        assertFalse(BraveBlockList.shouldBlock("https://example.com/page", false))
    }

    @Test
    fun disabledBlocksNothing() {
        load("||doubleclick.net^")
        BraveBlockList.setEnabled(false)
        assertFalse(BraveBlockList.shouldBlock("https://doubleclick.net/x", false))
    }
}
