package com.salazarprime.tiro.ime

internal object TranscriptInsertion {
    data class Replacement(val text: String, val caret: Int)

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

    fun replaceSelection(
        rawTranscript: String,
        currentText: String,
        selectionStart: Int,
        selectionEnd: Int,
    ): Replacement? {
        val lower = minOf(selectionStart, selectionEnd).coerceIn(0, currentText.length)
        val upper = maxOf(selectionStart, selectionEnd).coerceIn(lower, currentText.length)
        val insertion = prepare(rawTranscript, currentText.substring(0, lower))
        if (insertion.isEmpty()) return null

        val updated = currentText.replaceRange(lower, upper, insertion)
        return Replacement(text = updated, caret = lower + insertion.length)
    }

    private val NO_LEADING_SPACE_BEFORE = setOf('.', ',', '!', '?', ':', ';', ')', ']', '}')
}
