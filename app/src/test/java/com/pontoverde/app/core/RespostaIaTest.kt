package com.pontoverde.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RespostaIaTest {

    @Test
    fun `le json completo`() {
        val lido = RespostaIa.ler(
            """{"oferta": true, "valor": 18.5, "km_trecho": 7.4, "min_trecho": 22,
               "km_embarque": 1.2, "min_embarque": 4, "nota": 4.9,
               "paradas": false, "passageiro_novo": true}""",
        )
        assertEquals(18.5, lido!!.valor, 0.001)
        assertEquals(7.4, lido.kmTrecho, 0.001)
        assertEquals(22, lido.minTrecho)
        assertEquals(1.2, lido.kmEmbarque!!, 0.001)
        assertEquals(4, lido.minEmbarque)
        assertEquals(4.9, lido.nota!!, 0.001)
        assertEquals(true, lido.passageiroNovo)
    }

    @Test
    fun `aceita cercas de codigo e texto em volta`() {
        val lido = RespostaIa.ler(
            "Claro! Aqui está:\n```json\n{\"oferta\": true, \"valor\": 12, \"km_trecho\": 3}\n```\n",
        )
        assertEquals(12.0, lido!!.valor, 0.001)
        assertEquals(3.0, lido.kmTrecho, 0.001)
        assertEquals(0, lido.minTrecho)
    }

    @Test
    fun `aceita virgula decimal`() {
        val lido = RespostaIa.ler("""{"oferta": true, "valor": "18,50", "km_trecho": "7,4"}""")
        assertEquals(18.5, lido!!.valor, 0.001)
        assertEquals(7.4, lido.kmTrecho, 0.001)
    }

    @Test
    fun `oferta false devolve null`() {
        assertNull(RespostaIa.ler("""{"oferta": false}"""))
    }

    @Test
    fun `sem valor ou sem trecho devolve null`() {
        assertNull(RespostaIa.ler("""{"oferta": true, "km_trecho": 3}"""))
        assertNull(RespostaIa.ler("""{"oferta": true, "valor": 12}"""))
        assertNull(RespostaIa.ler("""{"oferta": true, "valor": null, "km_trecho": null}"""))
    }

    @Test
    fun `texto sem json devolve null`() {
        assertNull(RespostaIa.ler("não consegui entender a oferta"))
        assertNull(RespostaIa.ler(""))
        assertNull(RespostaIa.ler(null))
    }

    @Test
    fun `nota fora da faixa e descartada`() {
        val lido = RespostaIa.ler("""{"oferta": true, "valor": 12, "km_trecho": 3, "nota": 9.9}""")
        assertNull(lido!!.nota)
    }
}
