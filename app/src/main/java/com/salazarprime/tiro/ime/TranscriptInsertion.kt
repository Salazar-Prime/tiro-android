package com.salazarprime.tiro.ime

internal object TranscriptInsertion {
    fun prepare(rawTranscript: String, textBeforeCursor: CharSequence?): String {
        val transcript = rawTranscript.trim()
        if (transcript.isEmpty()) return ""

        val previous = textBeforeCursor?.lastOrNull()
        val first = transcript.first()
        val shouldInsertLeadingSpace = previous != null &&
            !previous.isWhitespace() &&
            first !in NO_LEADING_SPACE_BEFORE

        return if (shouldInsertLeadingSpace) " $transcript" else transcript
    }

    private val NO_LEADING_SPACE_BEFORE = setOf('.', ',', '!', '?', ':', ';', ')', ']', '}')
}

