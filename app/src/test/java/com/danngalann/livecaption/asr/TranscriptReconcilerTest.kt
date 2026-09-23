package com.danngalann.livecaption.asr

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptReconcilerTest {
    private val reconciler = TranscriptReconciler()

    @Test
    fun `removes committed suffix repeated by replacement provider`() {
        assertEquals(
            "mañana por la tarde",
            reconciler.removeCommittedOverlap(
                committed = "Podemos quedar",
                replacement = "quedar mañana por la tarde"
            )
        )
    }

    @Test
    fun `does not rewrite unrelated replacement text`() {
        assertEquals(
            "La reunión empieza ahora",
            reconciler.removeCommittedOverlap(
                committed = "Podemos quedar mañana",
                replacement = "La reunión empieza ahora"
            )
        )
    }
}

