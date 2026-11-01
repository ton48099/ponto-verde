package com.pontoverde.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertasTest {

    private val limiares = Limiares(AppOrigem.UBER)

    private fun avaliacao(
        valor: Double = 30.0,
        km: Double = 15.0,
        min: Int = 30,
        embarque: Double? = 1.0,
        nota: Double? = 4.9,
        paradas: Boolean = false,
        passageiroNovo: Boolean = false,
        origem: String? = null,
        destino: String? = null,
    ) = Avaliador.avaliar(
        Oferta(
            app = AppOrigem.UBER,
            valor = valor,
            kmTrecho = km,
            minTrecho = min,
            kmEmbarque = embarque,
            nota = nota,
            temParadas = paradas,
            passageiroNovo = passageiroNovo,
            textoEmbarque = origem,
            textoDestino = destino,
        ),
        limiares,
    )

    @Test
    fun `ordem e separador`() {
        val texto = Alertas.montar(
            avaliacao(paradas = true, passageiroNovo = true),
            ConfigAlertas(valorMinimo = 50.0),
        )
        assertEquals("com paradas · valor abaixo do mínimo · passageiro novo", texto)
    }

    @Test
    fun `limite zero desliga o alerta`() {
        val texto = Alertas.montar(avaliacao(valor = 5.0), ConfigAlertas(valorMinimo = 0.0))
        assertEquals("", texto)
    }

    @Test
    fun `local bloqueado casa sem acento e sem caixa`() {
        val texto = Alertas.montar(
            avaliacao(origem = "Rua São João"),
            ConfigAlertas(locaisBloqueados = setOf("sao joao")),
        )
        assertEquals("origem na lista bloqueada", texto)
    }

    @Test
    fun `embarque bloqueado suprime alertas numericos`() {
        val texto = Alertas.montar(
            avaliacao(valor = 2.0, origem = "Terminal Central", paradas = true),
            ConfigAlertas(
                valorMinimo = 50.0,
                locaisBloqueados = setOf("terminal central"),
            ),
        )
        assertEquals("com paradas · origem na lista bloqueada", texto)
    }

    @Test
    fun `alertas desligados devolvem vazio`() {
        val texto = Alertas.montar(
            avaliacao(paradas = true),
            ConfigAlertas(valorMinimo = 50.0),
            alertasLigados = false,
        )
        assertEquals("", texto)
    }

    @Test
    fun `cada interruptor esconde o seu alerta`() {
        val config = ConfigAlertas(
            valorMinimo = 50.0,
            trechoMaxKm = 10.0,
            mostrarValorMinimo = false,
        )
        val texto = Alertas.montar(avaliacao(valor = 30.0, km = 15.0), config)
        assertEquals("trecho longo", texto)
    }
}
