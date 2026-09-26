package com.danngalann.livecaption.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptionStabilizerTest {
    @Test
    fun `new words are visible immediately`() {
        val stabilizer = CaptionStabilizer()
        assertEquals("Hola, buenos días", stabilizer.update("Hola, buenos días"))
        assertEquals(
            "Hola, buenos días a todos",
            stabilizer.update("Hola, buenos días a todos")
        )
    }

    @Test
    fun `one unstable revision of previously read words is held`() {
        val stabilizer = CaptionStabilizer()
        stabilizer.update("Mañana iremos todos al parque a jugar")
        assertEquals(
            "Mañana iremos todos al parque a jugar",
            stabilizer.update("Mañana iríamos todos al parque a jugar")
        )
        assertEquals(
            "Mañana iríamos todos al parque a jugar con ellos",
            stabilizer.update("Mañana iríamos todos al parque a jugar con ellos")
        )
    }

    @Test
    fun `the live suffix can change and a final resets the buffer`() {
        val stabilizer = CaptionStabilizer()
        stabilizer.update("Mañana iremos todos al parque a jugar")
        assertEquals(
            "Mañana iremos todos al parque para jugar",
            stabilizer.update("Mañana iremos todos al parque para jugar")
        )
        stabilizer.reset()
        assertEquals("Otra frase", stabilizer.update("Otra frase"))
        assertEquals("", stabilizer.update(""))
    }
}
