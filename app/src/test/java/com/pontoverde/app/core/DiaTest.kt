package com.pontoverde.app.core

import com.pontoverde.app.data.DiaResumo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaTest {

    /** Combustível 5,00/L a 10 km/L = 0,50 por km, sem manutenção. */
    private val custos = Custos(
        precoLitro = 5.0,
        kmPorLitro = 10.0,
        manutencaoPorKm = 0.0,
        outrosPorDia = 0.0,
        metaDiaria = 300.0,
    )

    @Test
    fun `faltam considera o custo dos km que ainda faltam`() {
        val dia = DiaResumo(data = "2026-11-01", uber = 100.0, km = 50.0)

        val p = dia.progressoMeta(custos)

        // bruto 100 − custo 25 = 75 líquido; faltam 225 líquidos.
        assertEquals(75.0, p.liquido, 0.001)
        assertEquals(225.0, p.faltaLiquido, 0.001)
        assertEquals(25.0, p.percentual, 0.001)
        // média 2,00/km − custo 0,50/km = margem 1,50/km.
        assertEquals(2.0, dia.porKm, 0.001)
        assertEquals(1.5, p.margemPorKm, 0.001)
        // 225 / 1,50 = 150 km; 150 km custam 75, então o bruto que falta é 300.
        assertEquals(150.0, p.kmFaltantes, 0.001)
        assertEquals(300.0, p.brutoFaltante, 0.001)
        assertEquals(400.0, p.brutoAlvo, 0.001)
        assertTrue(p.estimavel)
        assertFalse(p.bateu)
    }

    @Test
    fun `meta batida zera o que falta`() {
        val dia = DiaResumo(data = "2026-11-01", uber = 400.0, km = 50.0)

        val p = dia.progressoMeta(custos)

        assertEquals(375.0, p.liquido, 0.001)
        assertEquals(100.0, p.percentual, 0.001)
        assertEquals(0.0, p.faltaLiquido, 0.001)
        assertEquals(0.0, p.brutoFaltante, 0.001)
        assertTrue(p.bateu)
    }

    @Test
    fun `sem km nao inventa projecao`() {
        val dia = DiaResumo(data = "2026-11-01", uber = 100.0, km = 0.0)

        val p = dia.progressoMeta(custos)

        assertEquals(0.0, dia.porKm, 0.001)
        // Sem km não há custo variável a estimar: o que falta de bruto é o próprio líquido.
        assertEquals(200.0, p.brutoFaltante, 0.001)
        assertFalse(p.estimavel)
    }

    @Test
    fun `margem negativa nao fecha a meta rodando mais`() {
        // Média 1,00/km e custo 1,20/km: cada km novo aumenta o prejuízo.
        val dia = DiaResumo(data = "2026-11-01", uber = 100.0, km = 100.0)
        val caro = custos.copy(precoLitro = 12.0, kmPorLitro = 10.0)

        val p = dia.progressoMeta(caro)

        assertEquals(-0.2, p.margemPorKm, 0.001)
        assertFalse(p.estimavel)
        assertEquals(p.faltaLiquido, p.brutoFaltante, 0.001)
    }

    @Test
    fun `meta do dia vazia usa a meta dos custos`() {
        val dia = DiaResumo(data = "2026-11-01", uber = 100.0, km = 50.0, meta = 0.0)

        val p = dia.progressoMeta(custos)

        assertEquals(300.0, p.meta, 0.001)
    }
}
