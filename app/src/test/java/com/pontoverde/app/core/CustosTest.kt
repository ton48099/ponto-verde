package com.pontoverde.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CustosTest {

    @Test
    fun `custo por km soma combustivel e manutencao`() {
        val custos = Custos(precoLitro = 5.89, kmPorLitro = 10.0, manutencaoPorKm = 0.25)
        assertEquals(0.589, custos.custoCombustivelPorKm, 0.0001)
        assertEquals(0.839, custos.custoPorKm, 0.0001)
    }

    @Test
    fun `sem consumo nao divide por zero`() {
        val custos = Custos(precoLitro = 5.89, kmPorLitro = 0.0, manutencaoPorKm = 0.20)
        assertEquals(0.0, custos.custoCombustivelPorKm, 0.0001)
        assertEquals(0.20, custos.custoPorKm, 0.0001)
    }

    @Test
    fun `lucro desconta o custo de todos os km rodados`() {
        val custos = Custos(precoLitro = 5.89, kmPorLitro = 10.0, manutencaoPorKm = 0.25)
        val oferta = Oferta(
            app = AppOrigem.UBER,
            valor = 14.0,
            kmTrecho = 2.0,
            minTrecho = 10,
            kmEmbarque = 5.0,
        )
        // 7 km rodados × 0,839 = 5,873 de custo
        assertEquals(8.127, oferta.lucro(custos.custoPorKm), 0.001)
    }

    @Test
    fun `custos zerados nao configuram`() {
        assertEquals(false, Custos(precoLitro = 0.0, kmPorLitro = 0.0, manutencaoPorKm = 0.0).configurado)
        assertEquals(true, Custos().configurado)
        assertEquals(true, Custos(precoLitro = 5.89, kmPorLitro = 10.0).configurado)
    }
}
