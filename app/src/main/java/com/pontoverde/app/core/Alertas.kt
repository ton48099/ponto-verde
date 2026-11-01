package com.pontoverde.app.core

import java.text.Normalizer

/**
 * Alertas textuais do cartão. Rótulos próprios, ordem fixa, separador único.
 *
 * Regra de supressão: se o embarque está num local bloqueado, os alertas
 * numéricos calam — não adianta discutir valor numa oferta que já seria
 * recusada pelo local. Os alertas de parada e de passageiro novo continuam
 * valendo, mas só quando são verdadeiros.
 */
object Alertas {

    const val SEPARADOR = " · "

    fun montar(avaliacao: Avaliacao, config: ConfigAlertas, alertasLigados: Boolean = true): String {
        if (!alertasLigados) return ""
        val oferta = avaliacao.oferta

        val origemBloqueada = casaLocal(oferta.textoEmbarque, config.locaisBloqueados)
        val destinoBloqueado = casaLocal(oferta.textoDestino, config.destinosBloqueados)

        val partes = mutableListOf<String>()

        if (config.mostrarParada && oferta.temParadas) partes += "com paradas"

        if (config.mostrarLocalBloqueado) {
            when {
                origemBloqueada && destinoBloqueado -> partes += "origem e destino na lista bloqueada"
                origemBloqueada -> partes += "origem na lista bloqueada"
                destinoBloqueado -> partes += "destino na lista bloqueada"
            }
        }

        if (config.mostrarLocalBloqueado && origemBloqueada) {
            // Divergência consciente: o app de referência mostra "passageiro novo"
            // mesmo quando a oferta não sinaliza passageiro novo. Aqui o alerta só
            // aparece se for verdade.
            if (config.mostrarPassageiroNovo && oferta.passageiroNovo) partes += "passageiro novo"
            return partes.joinToString(SEPARADOR)
        }

        if (config.mostrarValorMinimo && config.valorMinimo > 0 &&
            oferta.valor < config.valorMinimo
        ) partes += "valor abaixo do mínimo"

        if (config.mostrarEmbarqueLonge && config.embarqueMaxKm > 0 &&
            oferta.kmEmbarque != null && oferta.kmEmbarque > config.embarqueMaxKm
        ) partes += "embarque longe"

        if (config.mostrarEmbarqueDemorado && config.embarqueMaxMin > 0 &&
            oferta.minEmbarque != null && oferta.minEmbarque > config.embarqueMaxMin
        ) partes += "demora para embarcar"

        if (config.mostrarTrechoLongo && config.trechoMaxKm > 0 &&
            oferta.kmTrecho > config.trechoMaxKm
        ) partes += "trecho longo"

        if (config.mostrarViagemDemorada && config.trechoMaxMin > 0 &&
            oferta.minTrecho > config.trechoMaxMin
        ) partes += "viagem demorada"

        if (config.mostrarPassageiroNovo && oferta.passageiroNovo) partes += "passageiro novo"

        return partes.joinToString(SEPARADOR)
    }

    /** Compara ignorando caixa e acentos; lista vazia nunca casa. */
    fun casaLocal(texto: String?, bloqueados: Set<String>): Boolean {
        if (texto.isNullOrBlank() || bloqueados.isEmpty()) return false
        val alvo = normalizar(texto)
        return bloqueados.any { termo ->
            val limpo = normalizar(termo)
            limpo.isNotEmpty() && alvo.contains(limpo)
        }
    }

    fun normalizar(texto: String): String =
        Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .uppercase()
}
