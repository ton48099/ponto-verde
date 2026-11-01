package com.pontoverde.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ParserTest {

    @Test
    fun `le oferta completa`() {
        val lido = ParserOferta.ler("R$ 37,03\n28,1 km\n58 min\n4,88")
        assertNotNull(lido)
        assertEquals(37.03, lido!!.valor, 0.001)
        assertEquals(28.1, lido.kmTrecho, 0.001)
        assertEquals(58, lido.minTrecho)
        assertEquals(4.88, lido.nota!!, 0.001)
    }

    @Test
    fun `corrige hora lida como lhe`() {
        // texto real do OCR do card de R$ 204,93: "1 h e 55 min" virou "lhe55 min"
        val lido = ParserOferta.ler("R$ 204,93\n3 min (0.8 km)\nlhe55 min (109.9 km)")
        assertEquals(115, lido!!.minTrecho)
        assertEquals(3, lido.minEmbarque)
        assertEquals(118, lido.minTrecho + lido.minEmbarque!!)
    }

    @Test
    fun `embarque vem primeiro e trecho depois`() {
        val lido = ParserOferta.ler("R$ 10,20\n9 min (2,5 km)\n15 min (5,5 km)\n4,88")
        assertEquals(5.5, lido!!.kmTrecho, 0.001)
        assertEquals(2.5, lido.kmEmbarque!!, 0.001)
    }

    @Test
    fun `sem valor nao classifica`() {
        assertNull(ParserOferta.ler("28,1 km\n58 min"))
    }

    @Test
    fun `sem distancia nao classifica`() {
        assertNull(ParserOferta.ler("R$ 37,03\n58 min"))
    }

    @Test
    fun `converte hora e minuto`() {
        val lido = ParserOferta.ler("R$ 118,89\n71,7 km\n1h19")
        assertEquals(79, lido!!.minTrecho)
    }

    @Test
    fun `aceita ponto de milhar`() {
        val lido = ParserOferta.ler("R$ 1.234,56\n500 km\n600 min")
        assertEquals(1234.56, lido!!.valor, 0.001)
        assertEquals(500.0, lido.kmTrecho, 0.001)
    }

    @Test
    fun `nao confunde dinheiro com nota`() {
        val lido = ParserOferta.ler("R$ 4,88\n10 km\n20 min")
        assertNull(lido!!.nota)
    }

    @Test
    fun `detecta paradas e passageiro novo`() {
        val lido = ParserOferta.ler("R$ 37,03\n28,1 km\n58 min\n2 paradas\nPassageiro novo")
        assertEquals(true, lido!!.temParadas)
        assertEquals(true, lido.passageiroNovo)
    }

    @Test
    fun `ignora texto vazio`() {
        assertNull(ParserOferta.ler(""))
        assertNull(ParserOferta.ler(null))
    }
}
