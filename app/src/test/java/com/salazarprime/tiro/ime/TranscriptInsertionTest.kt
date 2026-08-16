package com.salazarprime.tiro.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptInsertionTest {
    @Test
    fun trimsAndInsertsAtAnEmptyField() {
        assertEquals("Hello world", TranscriptInsertion.prepare("  Hello world  ", ""))
    }

    @Test
    fun addsSpaceAfterExistingWord() {
        assertEquals(" next thought", TranscriptInsertion.prepare("next thought", "Hello"))
    }

    @Test
    fun doesNotDuplicateExistingWhitespace() {
        assertEquals("next thought", TranscriptInsertion.prepare("next thought", "Hello "))
    }

    @Test
    fun doesNotAddSpaceBeforePunctuation() {
        assertEquals("!", TranscriptInsertion.prepare("!", "Hello"))
    }

    @Test
    fun ignoresBlankRecognitionResults() {
        assertEquals("", TranscriptInsertion.prepare("   ", "Hello"))
    }

    @Test
    fun replacesTheCurrentSelectionAndReturnsNewCaret() {
        assertEquals(
            TranscriptInsertion.Replacement("Hello brave world", 11),
            TranscriptInsertion.replaceSelection("brave", "Hello old world", 6, 9),
        )
    }

    @Test
    fun clampsAStaleSelectionToTheAvailableText() {
        assertEquals(
            TranscriptInsertion.Replacement("Hello again", 11),
            TranscriptInsertion.replaceSelection("again", "Hello", 99, 99),
        )
    }
}
