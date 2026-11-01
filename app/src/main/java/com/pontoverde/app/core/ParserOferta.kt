package com.pontoverde.app.core

/**
 * Extrai a oferta do texto reconhecido na tela.
 *
 * Os cards reais vêm em três layouts:
 *  - Uber claro:   "4 min (1.6 km)" ... "58 minutos (28.1 km)"
 *  - 99 escuro:    "6 min (1,5 km)" ... "7 min (3,3 km)"
 *  - Comfort:      "9 min • 4.4 km" ... "5 minutos • 2.0 km"
 *
 * Em todos, o **embarque vem primeiro** e o trecho depois. Por isso a extração é
 * por pares ordenados (duração + distância na mesma linha); ordenar por tamanho
 * erra em ofertas como a do Comfort, em que o embarque é maior que o trecho.
 *
 * Regra de ouro: na dúvida, não classifica. Sem valor e sem distância de trecho,
 * devolve null e o app avisa que não conseguiu ler.
 */
object ParserOferta {

    /** Duração seguida de distância em km, na mesma linha/frase. */
    private val rePar = Regex(
        """(?:(?:(\d{1,2})\s*h(?:\s*e)?\s*(\d{1,2})?)\s*(?:min(?:utos)?)?|(\d{1,3})\s*min(?:utos)?)""" +
            """[^\d]{0,10}?(\d+(?:[.,]\d+)?)\s*km""",
        RegexOption.IGNORE_CASE,
    )

    private val reValor = Regex("""R\$\s*([0-9][0-9.\s]*,[0-9]{2})""")
    private val reValorSolto = Regex("""\b([0-9]{1,4},[0-9]{2})\b""")
    private val reKm = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*km\b""", RegexOption.IGNORE_CASE)
    private val reMin = Regex("""([0-9]{1,3})\s*min(?:utos)?\b""", RegexOption.IGNORE_CASE)
    private val reHoraMin = Regex("""([0-9]{1,2})\s*h(?:\s*e)?\s*([0-9]{1,2})?""", RegexOption.IGNORE_CASE)
    private val reNota = Regex("""\b([45][.,][0-9]{1,2})\b""")

    data class Lido(
        val valor: Double,
        val kmTrecho: Double,
        val minTrecho: Int,
        val kmEmbarque: Double? = null,
        val minEmbarque: Int? = null,
        val nota: Double? = null,
        val temParadas: Boolean = false,
        val passageiroNovo: Boolean = false,
    )

    /** Duração + distância de um trecho do card, na ordem em que aparecem. */
    private data class Par(val minutos: Int, val km: Double)

    fun ler(texto: String?): Lido? {
        if (texto.isNullOrBlank()) return null
        val limpo = corrigirHorasOcr(texto.replace('\u00A0', ' '))

        val valor = extrairValor(limpo) ?: return null
        val pares = extrairPares(limpo)

        val kmTrecho: Double
        val minTrecho: Int
        var kmEmbarque: Double? = null
        var minEmbarque: Int? = null

        when {
            pares.size >= 2 -> {
                minEmbarque = pares[0].minutos
                kmEmbarque = pares[0].km
                minTrecho = pares[1].minutos
                kmTrecho = pares[1].km
            }
            pares.size == 1 -> {
                minTrecho = pares[0].minutos
                kmTrecho = pares[0].km
            }
            else -> {
                val kms = extrairKms(limpo)
                if (kms.isEmpty()) return null
                kmTrecho = kms.max()
                kmEmbarque = kms.filter { it != kmTrecho }.maxOrNull()
                minTrecho = extrairMinutos(limpo) ?: 0
            }
        }

        if (kmTrecho <= 0) return null

        return Lido(
            valor = valor,
            kmTrecho = kmTrecho,
            minTrecho = minTrecho,
            kmEmbarque = kmEmbarque,
            minEmbarque = minEmbarque,
            nota = extrairNota(limpo),
            temParadas = temParadas(limpo),
            passageiroNovo = temPassageiroNovo(limpo),
        )
    }

    fun paraOferta(lido: Lido, app: AppOrigem): Oferta = Oferta(
        app = app,
        valor = lido.valor,
        kmTrecho = lido.kmTrecho,
        minTrecho = lido.minTrecho,
        kmEmbarque = lido.kmEmbarque,
        minEmbarque = lido.minEmbarque,
        nota = lido.nota,
        temParadas = lido.temParadas,
        passageiroNovo = lido.passageiroNovo,
    )

    /**
     * O OCR às vezes lê "1 h e 55 min" como "lhe55 min" (ele troca o 1 por l e
     * cola no h). Sem isso, a hora desaparece e sobra só "55 min".
     */
    private fun corrigirHorasOcr(texto: String): String =
        Regex("""(?<![0-9A-Za-zÀ-ÿ])[lI]\s*h\s*e?\s*(\d{1,2})""").replace(texto) {
            "1 h e ${it.groupValues[1]}"
        }

    private fun extrairPares(texto: String): List<Par> =
        rePar.findAll(texto).mapNotNull { m ->
            val horas = m.groupValues[1].toIntOrNull()
            val minutosDaHora = m.groupValues[2].toIntOrNull()
            val minutos = when {
                horas != null -> horas * 60 + (minutosDaHora ?: 0)
                else -> m.groupValues[3].toIntOrNull()
            } ?: return@mapNotNull null
            val km = numero(m.groupValues[4]) ?: return@mapNotNull null
            if (km <= 0 || km > 2000 || minutos <= 0 || minutos > 24 * 60) return@mapNotNull null
            Par(minutos, km)
        }.toList()

    private fun extrairValor(texto: String): Double? {
        for (m in reValor.findAll(texto)) {
            val antes = texto.substring((m.range.first - 12).coerceAtLeast(0), m.range.first)
            val depois = texto.substring(m.range.last + 1, (m.range.last + 6).coerceAtMost(texto.length))
            if (antes.contains("Est.", ignoreCase = true)) continue
            if (depois.trimStart().startsWith("/km")) continue
            return numero(m.groupValues[1])
        }
        return reValorSolto.find(texto)?.let { numero(it.groupValues[1]) }
    }

    private fun extrairKms(texto: String): List<Double> =
        reKm.findAll(texto).mapNotNull { numero(it.groupValues[1]) }
            .filter { it > 0 && it < 2000 }.toList()

    private fun extrairMinutos(texto: String): Int? {
        reHoraMin.find(texto)?.let { m ->
            val horas = m.groupValues[1].toIntOrNull() ?: return@let
            val minutos = m.groupValues[2].toIntOrNull() ?: 0
            if (horas in 0..23 && minutos in 0..59) return horas * 60 + minutos
        }
        return reMin.findAll(texto).mapNotNull { it.groupValues[1].toIntOrNull() }
            .filter { it in 1..1440 }.maxOrNull()
    }

    private fun extrairNota(texto: String): Double? {
        reNota.findAll(texto).forEach { m ->
            val antes = texto.substring((m.range.first - 3).coerceAtLeast(0), m.range.first)
            val depois = texto.substring(m.range.last + 1, (m.range.last + 8).coerceAtMost(texto.length))
                .trimStart()
            if (antes.contains("R$")) return@forEach
            // "4.4 km" e "4 min" são distância e duração, não nota
            if (Regex("""^(km|min)\b""", RegexOption.IGNORE_CASE).containsMatchIn(depois)) return@forEach
            numero(m.groupValues[1])?.let { if (it in 1.0..5.0) return it }
        }
        return null
    }

    private fun temParadas(texto: String): Boolean {
        val tem = Regex("""\bparadas?\b""", RegexOption.IGNORE_CASE).containsMatchIn(texto) ||
            Regex("""parada\(s\)""", RegexOption.IGNORE_CASE).containsMatchIn(texto)
        val sem = Regex("""sem\s+paradas?""", RegexOption.IGNORE_CASE).containsMatchIn(texto)
        return tem && !sem
    }

    private fun temPassageiroNovo(texto: String): Boolean =
        Regex("""passageiro\s+novo|novo\s+passageiro""", RegexOption.IGNORE_CASE).containsMatchIn(texto)

    /** Converte "1.234,56", "1234,56" e "12.5" para Double. */
    fun numero(bruto: String?): Double? {
        if (bruto.isNullOrBlank()) return null
        val limpo = bruto.replace(" ", "")
        val normalizado = if (limpo.contains(',')) {
            limpo.replace(".", "").replace(',', '.')
        } else {
            limpo
        }
        return normalizado.toDoubleOrNull()
    }
}
