package com.pontoverde.app.core

/** Apps de origem que o leitor observa. */
enum class AppOrigem(val rotulo: String, val pacote: String) {
    UBER("Uber", "com.ubercab.driver"),
    APP99("99", "com.app99.driver"),
    INDRIVE("inDrive", "sinet.startup.indriver"),
    IFOOD("iFood Entregador", "br.com.ifood.driver.app"),
    TAXI("Taxi Machine", "taxi.drivermachine"),
    REGIONAL("Regional", "br.com.tourmobilidade.prestador"),
    /** Usado só no modo de teste, quando a leitura vale para qualquer app. */
    OUTRO("Outro app", "");

    companion object {
        /** Apps com configuração e monitoramento (o app é focado em Uber e 99). */
        val reais: List<AppOrigem> get() = listOf(UBER, APP99)

        fun porPacote(pacote: String?): AppOrigem? =
            entries.firstOrNull { it.pacote.isNotEmpty() && it.pacote == pacote }
    }
}

/** Três estados do farol. */
enum class Cor { VERDE, AMARELO, VERMELHO }

val OrdemCor = mapOf(Cor.VERDE to 0, Cor.AMARELO to 1, Cor.VERMELHO to 2)

/**
 * Limiares de um app. Em distância de embarque, menor é melhor: os campos
 * `embarqueRecusa` e `embarqueAtencao` são, respectivamente, o limite que
 * pinta de vermelho e o que pinta de amarelo.
 */
data class Limiares(
    val app: AppOrigem,
    val kmRuim: Double = 1.4,
    val kmBom: Double = 1.8,
    val horaRuim: Double = 30.0,
    val horaBom: Double = 40.0,
    val notaRuim: Double = 4.7,
    val notaBom: Double = 4.9,
    val embarqueRecusa: Double = 2.0,
    val embarqueAtencao: Double = 1.0,
)

/** Custos do veículo, para calcular lucro real em vez de faturamento. */
data class Custos(
    val precoLitro: Double = 4.19,
    val kmPorLitro: Double = 9.0,
    val manutencaoPorKm: Double = 0.25,
    val outrosPorDia: Double = 0.0,
    val metaDiaria: Double = 300.0,
) {
    val custoCombustivelPorKm: Double
        get() = if (kmPorLitro > 0) precoLitro / kmPorLitro else 0.0

    /** Custo por km rodado: combustível + reserva de manutenção. */
    val custoPorKm: Double get() = custoCombustivelPorKm + manutencaoPorKm

    val configurado: Boolean get() = custoPorKm > 0
}

/** Oferta como lida da tela. Campos opcionais ficam nulos quando não aparecem. */
data class Oferta(
    val app: AppOrigem,
    val valor: Double,
    val kmTrecho: Double,
    val minTrecho: Int,
    val kmEmbarque: Double? = null,
    val minEmbarque: Int? = null,
    val nota: Double? = null,
    val temParadas: Boolean = false,
    val passageiroNovo: Boolean = false,
    val textoEmbarque: String? = null,
    val textoDestino: String? = null,
) {
    /** Distância total rodada: trecho + deslocamento até o embarque. */
    val kmTotal: Double get() = kmTrecho + (kmEmbarque ?: 0.0)

    /** Tempo total: trecho + deslocamento até o embarque. */
    val minutosTotais: Int get() = minTrecho + (minEmbarque ?: 0)

    /** Rentabilidade real: o que entra dividido por tudo o que se roda. */
    val rsPorKm: Double get() = if (kmTotal > 0) valor / kmTotal else 0.0
    val rsPorHora: Double get() = if (minutosTotais > 0) valor * 60.0 / minutosTotais else 0.0
    val rsPorMinuto: Double get() = if (minutosTotais > 0) valor / minutosTotais else 0.0

    /** Só o trecho, sem o deslocamento — informativo. */
    val rsPorKmTrecho: Double get() = if (kmTrecho > 0) valor / kmTrecho else 0.0

    /** Lucro real: valor menos o custo de rodar todos os km (trecho + embarque). */
    fun lucro(custoPorKm: Double): Double = valor - custoPorKm * kmTotal
}

/** Oferta já classificada. */
data class Avaliacao(
    val oferta: Oferta,
    val corKm: Cor,
    val corHora: Cor,
    val corNota: Cor?,
    val corEmbarque: Cor?,
    val corFinal: Cor,
)

/** Preferências que alimentam os alertas textuais. */
data class ConfigAlertas(
    val valorMinimo: Double = 0.0,
    val embarqueMaxKm: Double = 0.0,
    val embarqueMaxMin: Int = 0,
    val trechoMaxKm: Double = 0.0,
    val trechoMaxMin: Int = 0,
    val mostrarParada: Boolean = true,
    val mostrarLocalBloqueado: Boolean = true,
    val mostrarPassageiroNovo: Boolean = true,
    val mostrarValorMinimo: Boolean = true,
    val mostrarEmbarqueLonge: Boolean = true,
    val mostrarEmbarqueDemorado: Boolean = true,
    val mostrarTrechoLongo: Boolean = true,
    val mostrarViagemDemorada: Boolean = true,
    val locaisBloqueados: Set<String> = emptySet(),
    val destinosBloqueados: Set<String> = emptySet(),
)
