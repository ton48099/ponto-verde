package com.pontoverde.app.core

/**
 * Lê a resposta JSON do corretor de IA e devolve a oferta no mesmo formato do
 * parser local.
 *
 * É Kotlin puro de propósito: roda nos testes da JVM sem Android e sem a
 * biblioteca org.json (que nos testes de unidade é só um stub).
 *
 * O contrato pedido no prompt é:
 * ```
 * {"oferta": true, "valor": 12.34, "km_trecho": 5.2, "min_trecho": 15,
 *  "km_embarque": 1.1, "min_embarque": 4, "nota": 4.9}
 * ```
 * Campos ausentes ou `null` viram `null`. Se `oferta` for `false`, devolve
 * `null` — a IA disse que aquilo não é uma oferta.
 */
object RespostaIa {

    private val reCampo = Regex(
        """["']?([A-Za-z_][A-Za-z0-9_]*)["']?\s*:\s*(?:"([^"]*)"|([^,}\s\]]+))""",
    )

    fun ler(resposta: String?): ParserOferta.Lido? {
        if (resposta.isNullOrBlank()) return null
        val json = extrairObjeto(resposta) ?: return null
        val campos = campos(json)

        if (campos["oferta"]?.equals("false", ignoreCase = true) == true) return null

        val valor = numero(campos["valor"]) ?: return null
        val kmTrecho = numero(campos["km_trecho"] ?: campos["kmTrecho"]) ?: return null
        if (valor <= 0 || kmTrecho <= 0 || kmTrecho > 2000) return null

        val minTrecho = numero(campos["min_trecho"] ?: campos["minTrecho"])
            ?.toInt()?.coerceIn(0, 24 * 60) ?: 0
        val kmEmbarque = numero(campos["km_embarque"] ?: campos["kmEmbarque"])
            ?.takeIf { it > 0 && it <= 2000 }
        val minEmbarque = numero(campos["min_embarque"] ?: campos["minEmbarque"])
            ?.toInt()?.takeIf { it > 0 && it <= 24 * 60 }
        val nota = numero(campos["nota"])?.takeIf { it in 1.0..5.0 }

        return ParserOferta.Lido(
            valor = valor,
            kmTrecho = kmTrecho,
            minTrecho = minTrecho,
            kmEmbarque = kmEmbarque,
            minEmbarque = minEmbarque,
            nota = nota,
            temParadas = booleano(campos["paradas"]),
            passageiroNovo = booleano(campos["passageiro_novo"] ?: campos["passageiroNovo"]),
        )
    }

    /** Pega o primeiro objeto `{...}` da resposta, ignorando cercas de código. */
    private fun extrairObjeto(texto: String): String? {
        val inicio = texto.indexOf('{')
        val fim = texto.lastIndexOf('}')
        if (inicio < 0 || fim <= inicio) return null
        return texto.substring(inicio, fim + 1)
    }

    private fun campos(json: String): Map<String, String> =
        reCampo.findAll(json).associate { m ->
            val chave = m.groupValues[1]
            val valor = m.groupValues[2].ifBlank { m.groupValues[3] }
            chave to valor
        }

    private fun numero(bruto: String?): Double? {
        if (bruto.isNullOrBlank() || bruto.equals("null", ignoreCase = true)) return null
        return ParserOferta.numero(bruto.trim())
    }

    private fun booleano(bruto: String?): Boolean =
        bruto?.equals("true", ignoreCase = true) == true
}
