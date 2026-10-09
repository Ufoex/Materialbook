package com.eepiemi.materialbook

import com.eepiemi.materialbook.utils.NoteLine
import com.eepiemi.materialbook.utils.parseNotes
import com.eepiemi.materialbook.utils.releaseNotes
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateNotesTest {
    private val body = """
        ## Update check

        - **Install** it from the app, see `Settings`.
        - Second point

        Includes everything from v1.1.14.

        **Full Changelog**: https://github.com/x/y/compare/a...b
    """.trimIndent()

    @Test
    fun boilerplateLinesAreDropped() {
        assertEquals(
            "## Update check\n\n- **Install** it from the app, see `Settings`.\n- Second point",
            releaseNotes(body)
        )
    }

    @Test
    fun notesParseIntoHeadingAndBullets() {
        assertEquals(
            listOf(
                NoteLine.Heading("Update check"),
                NoteLine.Bullet("**Install** it from the app, see `Settings`."),
                NoteLine.Bullet("Second point"),
            ),
            parseNotes(releaseNotes(body))
        )
    }
}
