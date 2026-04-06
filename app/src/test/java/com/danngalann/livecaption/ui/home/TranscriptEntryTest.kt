package com.danngalann.livecaption.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptEntryTest {

    @Test
    fun `does not start a new paragraph without a previous transcript time`() {
        assertFalse(shouldStartNewParagraph(null, 5_000L))
    }

    @Test
    fun `does not start a new paragraph before the five second threshold`() {
        assertFalse(shouldStartNewParagraph(1_000L, 5_999L))
    }

    @Test
    fun `starts a new paragraph at or after the five second threshold`() {
        assertTrue(shouldStartNewParagraph(1_000L, 6_000L))
    }
}

