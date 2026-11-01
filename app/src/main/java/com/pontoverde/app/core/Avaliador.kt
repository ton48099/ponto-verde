package com.pontoverde.app.core

/**
 * As quatro classificações. As bordas são assimétricas de propósito: os limites
 * "bons" incluem o próprio valor (>=), os "ruins" excluem (>). Mudar isso muda a
 * cor exatamente em cima do limiar.
 */
object Avaliador {

    fun corPorKm(limiares: Limiares, rsPorKm: Double): Cor = when {
        rsPorKm >= limiares.kmBom -> Cor.VERDE
        rsPorKm > limiares.kmRuim -> Cor.AMARELO
        else -> Cor.VERMELHO
    }

    fun corPorHora(limiares: Limiares, rsPorHora: Double): Cor = when {
        rsPorHora >= limiares.horaBom -> Cor.VERDE
        rsPorHora <= 0.0 -> Cor.VERDE
        rsPorHora > limiares.horaRuim -> Cor.AMARELO
        else -> Cor.VERMELHO
    }

    fun corPorEmbarque(limiares: Limiares, km: Double): Cor = when {
        km >= limiares.embarqueRecusa -> Cor.VERMELHO
        km > limiares.embarqueAtencao -> Cor.AMARELO
        else -> Cor.VERDE
    }

    fun corPorNota(limiares: Limiares, nota: Double): Cor = when {
        nota >= limiares.notaBom -> Cor.VERDE
        nota > limiares.notaRuim -> Cor.AMARELO
        else -> Cor.VERMELHO
    }

    fun pior(vararg cores: Cor?): Cor =
        cores.filterNotNull().maxByOrNull { OrdemCor.getValue(it) } ?: Cor.VERDE

    fun avaliar(oferta: Oferta, limiares: Limiares): Avaliacao {
        val corKm = corPorKm(limiares, oferta.rsPorKm)
        val corHora = corPorHora(limiares, oferta.rsPorHora)
        val corNota = oferta.nota?.let { corPorNota(limiares, it) }
        // O embarque não tem cor própria: ele já entra no R$/km e no R$/h pelo
        // total rodado. Limite de embarque é alerta, não critério de cor.
        return Avaliacao(
            oferta = oferta,
            corKm = corKm,
            corHora = corHora,
            corNota = corNota,
            corEmbarque = null,
            corFinal = pior(corKm, corHora, corNota),
        )
    }
}
