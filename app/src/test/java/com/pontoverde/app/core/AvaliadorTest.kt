package com.pontoverde.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvaliadorTest {

    private val limiares = Limiares(app = AppOrigem.UBER)

    private fun oferta(
        valor: Double,
        km: Double,
        min: Int,
        embarque: Double? = null,
        nota: Double? = null,
        minEmbarque: Int? = null,
    ) = Oferta(
        app = AppOrigem.UBER,
        valor = valor,
        kmTrecho = km,
        minTrecho = min,
        kmEmbarque = embarque,
        minEmbarque = minEmbarque,
        nota = nota,
    )

    @Test
    fun `bordas do km sao assimetricas`() {
        assertEquals(Cor.VERMELHO, Avaliador.corPorKm(limiares, 1.3999))
        assertEquals(Cor.VERMELHO, Avaliador.corPorKm(limiares, 1.40))
        assertEquals(Cor.AMARELO, Avaliador.corPorKm(limiares, 1.4001))
        assertEquals(Cor.AMARELO, Avaliador.corPorKm(limiares, 1.7999))
        assertEquals(Cor.VERDE, Avaliador.corPorKm(limiares, 1.80))
    }

    @Test
    fun `bordas da hora sao assimetricas`() {
        assertEquals(Cor.VERMELHO, Avaliador.corPorHora(limiares, 30.0))
        assertEquals(Cor.AMARELO, Avaliador.corPorHora(limiares, 30.01))
        assertEquals(Cor.AMARELO, Avaliador.corPorHora(limiares, 39.99))
        assertEquals(Cor.VERDE, Avaliador.corPorHora(limiares, 40.0))
    }

    @Test
    fun `hora zero ou negativa e verde`() {
        assertEquals(Cor.VERDE, Avaliador.corPorHora(limiares, 0.0))
        assertEquals(Cor.VERDE, Avaliador.corPorHora(limiares, -5.0))
    }

    @Test
    fun `embarque e invertido`() {
        assertEquals(Cor.VERDE, Avaliador.corPorEmbarque(limiares, 1.0))
        assertEquals(Cor.AMARELO, Avaliador.corPorEmbarque(limiares, 1.0001))
        assertEquals(Cor.AMARELO, Avaliador.corPorEmbarque(limiares, 1.9999))
        assertEquals(Cor.VERMELHO, Avaliador.corPorEmbarque(limiares, 2.0))
    }

    @Test
    fun `nota`() {
        assertEquals(Cor.VERMELHO, Avaliador.corPorNota(limiares, 4.70))
        assertEquals(Cor.AMARELO, Avaliador.corPorNota(limiares, 4.71))
        assertEquals(Cor.AMARELO, Avaliador.corPorNota(limiares, 4.89))
        assertEquals(Cor.VERDE, Avaliador.corPorNota(limiares, 4.90))
    }

    @Test
    fun `sem nota e sem embarque nao votam`() {
        val avaliacao = Avaliador.avaliar(oferta(90.0, 30.0, 60), limiares)
        assertNull(avaliacao.corNota)
        assertNull(avaliacao.corEmbarque)
        assertEquals(Cor.VERDE, avaliacao.corFinal)
    }

    @Test
    fun `embarque entra na conta do km e do tempo`() {
        // 5 km para buscar + 2 km de trecho, R$ 14: total 7 km → 2,00/km, vale a pena.
        val vale = Avaliador.avaliar(oferta(14.0, 2.0, 10, 5.0, 4.9, 10), limiares)
        assertEquals(2.0, 14.0 / 7.0, 0.0001)
        assertEquals(Cor.VERDE, vale.corFinal)

        // 4 km para buscar + 4 km de trecho, R$ 8: total 8 km → 1,00/km, não vale.
        val naoVale = Avaliador.avaliar(oferta(8.0, 4.0, 10, 4.0, 4.9, 12), limiares)
        assertEquals(Cor.VERMELHO, naoVale.corFinal)
    }

    @Test
    fun `oferta 2 fica vermelha pelo total rodado`() {
        // antes o embarque era critério próprio; agora ele entra no denominador
        val avaliacao = Avaliador.avaliar(oferta(10.20, 5.5, 15, 2.5, 4.88, 9), limiares)
        assertEquals(Cor.VERMELHO, avaliacao.corKm)
        assertNull(avaliacao.corEmbarque)
        assertEquals(Cor.VERMELHO, avaliacao.corFinal)
    }

    @Test
    fun `rs por km usa trecho mais embarque`() {
        assertEquals(7.46 / (2.0 + 4.4), oferta(7.46, 2.0, 5, 4.4).rsPorKm, 0.0001)
        assertEquals(37.03 / (28.1 + 1.6), oferta(37.03, 28.1, 58, 1.6).rsPorKm, 0.0001)
        assertEquals(7.46 / 2.0, oferta(7.46, 2.0, 5, 4.4).rsPorKmTrecho, 0.0001)
    }

    @Test
    fun `sem embarque o km total e o do trecho`() {
        assertEquals(2.0, oferta(30.0, 15.0, 30).rsPorKm, 0.0001)
        assertEquals(15.0, oferta(30.0, 15.0, 30, 0.0).kmTotal, 0.0001)
    }

    @Test
    fun `ofertas reais do registro`() {
        val casos = listOf(
            // valor, km, min, embarque, nota, pior esperado (com embarque no total)
            listOf(37.03, 28.1, 58.0, 1.6, 4.88, Cor.VERMELHO),
            listOf(10.20, 5.5, 15.0, 2.5, 4.88, Cor.VERMELHO),
            listOf(22.00, 15.7, 31.0, 1.6, 4.82, Cor.VERMELHO),
            listOf(118.89, 71.7, 79.0, 1.3, 4.92, Cor.AMARELO),
            listOf(21.00, 14.8, 29.0, null, null, Cor.AMARELO),
            listOf(20.00, 14.8, 29.0, null, null, Cor.VERMELHO),
        )
        casos.forEach { c ->
            val avaliacao = Avaliador.avaliar(
                oferta(
                    valor = c[0] as Double,
                    km = c[1] as Double,
                    min = (c[2] as Double).toInt(),
                    embarque = c[3] as Double?,
                    nota = c[4] as Double?,
                ),
                limiares,
            )
            assertEquals("oferta $c", c[5], avaliacao.corFinal)
        }
    }
}
