package com.danngalann.livecaption.asr

class TranscriptReconciler {
    fun removeCommittedOverlap(committed: String, replacement: String): String {
        val candidate = replacement.trim()
        if (candidate.isEmpty() || committed.isBlank()) return candidate

        val committedWords = words(committed)
        val candidateWords = words(candidate)
        val maxOverlap = minOf(committedWords.size, candidateWords.size, 12)
        for (count in maxOverlap downTo 1) {
            if (committedWords.takeLast(count) == candidateWords.take(count)) {
                return candidateWords.drop(count).joinToString(" ")
            }
        }
        return candidate
    }

    private fun words(value: String): List<String> =
        value.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .map { it.lowercase().trim('.', ',', ';', ':', '!', '¿', '?', '¡') }
}

