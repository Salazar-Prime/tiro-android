package com.salazarprime.tiro.recognition

internal data class SpeechLanguage(
    val tag: String,
    val label: String,
)

internal object SpeechLanguages {
    val options = listOf(
        SpeechLanguage("en-GB", "British English"),
        SpeechLanguage("en-US", "American English"),
        SpeechLanguage("en-AU", "Australian English"),
        SpeechLanguage("en-CA", "Canadian English"),
        SpeechLanguage("en-IN", "Indian English"),
    )

    fun resolve(tag: String): SpeechLanguage =
        options.firstOrNull { language -> language.tag.equals(tag, ignoreCase = true) }
            ?: options.first()
}
