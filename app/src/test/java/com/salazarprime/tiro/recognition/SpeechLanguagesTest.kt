package com.salazarprime.tiro.recognition

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechLanguagesTest {
    @Test
    fun britishEnglishIsTheDefault() {
        assertEquals("en-GB", SpeechLanguages.options.first().tag)
        assertEquals("British English", SpeechLanguages.resolve("en-GB").label)
    }

    @Test
    fun unknownLanguageFallsBackToBritishEnglish() {
        assertEquals("en-GB", SpeechLanguages.resolve("unknown").tag)
    }
}
