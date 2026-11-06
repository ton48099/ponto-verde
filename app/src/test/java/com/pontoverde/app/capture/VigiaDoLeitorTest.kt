package com.pontoverde.app.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A decisão de avisar é pura: só o disparo do alarme precisa de Android. */
class VigiaDoLeitorTest {

    private val agora = 1_800_000_000_000L
    private fun min(m: Long) = m * 60_000L

    @Test
    fun `nunca leu nada nao avisa`() {
        assertNull(VigiaDoLeitor.minutosParado(agora, null))
        assertNull(VigiaDoLeitor.minutosParado(agora, 0L))
    }

    @Test
    fun `leitura recente nao avisa`() {
        assertNull(VigiaDoLeitor.minutosParado(agora, agora - min(3)))
        assertNull(VigiaDoLeitor.minutosParado(agora, agora - min(9)))
    }

    @Test
    fun `no limite exato ja avisa`() {
        assertEquals(10L, VigiaDoLeitor.minutosParado(agora, agora - min(10)))
    }

    @Test
    fun `parado ha muito tempo informa os minutos`() {
        assertEquals(47L, VigiaDoLeitor.minutosParado(agora, agora - min(47)))
    }

    @Test
    fun `relogio voltando nao vira aviso`() {
        assertNull(VigiaDoLeitor.minutosParado(agora, agora + min(5)))
    }
}
