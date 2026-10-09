package com.eepiemi.materialbook

import com.eepiemi.materialbook.utils.parseCounts
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPollTest {
    @Test
    fun countsComeFromTheTabLabels() {
        val html = """<div role="tablist"><div role="tab" aria-label="feed, 1.800 new, 1 of 6"></div>
            <div role="tab" aria-label="friends, 2 of 6"></div><div role="tab" aria-label="messages, 3 new, 3 of 6"></div>
            <div role="tab" aria-label="notifications, 15+ new, 5 of 6"></div></div>"""
        assertEquals(listOf(3, 15, 0), parseCounts(html))
    }

    @Test
    fun noLabelsMeansNoCounts() {
        assertEquals(listOf(0, 0, 0), parseCounts("<html></html>"))
    }
}
