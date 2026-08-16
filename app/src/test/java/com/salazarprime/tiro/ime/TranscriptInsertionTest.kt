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

    @Test
    fun ignoresADisplayedHintWhenInsertingIntoAnEmptyField() {
        assertEquals(
            TranscriptInsertion.Replacement("Hello", 5),
            TranscriptInsertion.replaceAccessibilityText(
                rawTranscript = "Hello",
                exposedText = "Tap here to type",
                hintText = "Tap here to type",
                isShowingHintText = true,
                selectionStart = 0,
                selectionEnd = 0,
            ),
        )
    }

    @Test
    fun ignoresHintTextFromAProviderWithNoSelectionMetadata() {
        assertEquals(
            TranscriptInsertion.Replacement("Hello", 5),
            TranscriptInsertion.replaceAccessibilityText(
                rawTranscript = "Hello",
                exposedText = "Write a message",
                hintText = "Write a message",
                isShowingHintText = false,
                selectionStart = -1,
                selectionEnd = -1,
            ),
        )
    }

    @Test
    fun ignoresHintTextFromAProviderReportingCursorAtZero() {
        assertEquals(
            TranscriptInsertion.Replacement("Hello", 5),
            TranscriptInsertion.replaceAccessibilityText(
                rawTranscript = "Hello",
                exposedText = "Search Google or type URL",
                hintText = "Search Google or type URL",
                isShowingHintText = false,
                selectionStart = 0,
                selectionEnd = 0,
            ),
        )
    }

    @Test
    fun preservesRealTextEvenWhenItMatchesTheFieldHint() {
        assertEquals(
            TranscriptInsertion.Replacement("Tap here to type Hello", 22),
            TranscriptInsertion.replaceAccessibilityText(
                rawTranscript = "Hello",
                exposedText = "Tap here to type",
                hintText = "Tap here to type",
                isShowingHintText = false,
                selectionStart = 16,
                selectionEnd = 16,
            ),
        )
    }
}
