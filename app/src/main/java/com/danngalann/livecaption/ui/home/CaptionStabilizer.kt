package com.danngalann.livecaption.ui.home

class CaptionStabilizer {
    private var displayed = ""
    private var pendingCorrection: String? = null

    fun update(hypothesis: String): String {
        if (hypothesis.isBlank()) {
            reset()
            return ""
        }
        if (displayed.isBlank()) {
            displayed = hypothesis
            return displayed
        }
        val stablePrefix = displayed.trimEnd().split(Regex("\\s+"))
            .dropLast(3).joinToString(" ")
        if (stablePrefix.isNotEmpty() &&
            hypothesis != stablePrefix &&
            !hypothesis.startsWith("$stablePrefix ")
        ) {
            // Wait for a correction to recur before moving text the reader has already read.
            val pending = pendingCorrection
            val confirmed = pending != null && (
                hypothesis == pending ||
                    hypothesis.startsWith("$pending ") ||
                    pending.split(Regex("\\s+")).dropLast(2).joinToString(" ")
                        .takeIf { it.isNotEmpty() }
                        ?.let { hypothesis.startsWith("$it ") } == true
                )
            if (confirmed) {
                displayed = hypothesis
                pendingCorrection = null
            } else {
                pendingCorrection = hypothesis
            }
        } else {
            displayed = hypothesis
            pendingCorrection = null
        }
        return displayed
    }

    fun reset() {
        displayed = ""
        pendingCorrection = null
    }
}
