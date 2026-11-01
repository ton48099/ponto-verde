package com.pontoverde.app.data

import android.content.Context
import com.pontoverde.app.core.Custos

data class GastoDia(val descricao: String, val valor: Double)

/**
 * Quanto falta para a meta, já considerando o custo de rodar os km que ainda
 * faltam. `brutoFaltante` é o que precisa entrar de faturamento, não o buraco
 * líquido: para ganhar R$ 1 líquido com margem de R$ 1,16/km, é preciso faturar
 * R$ 2,00/km.
 */
data class ProgressoMeta(
    val meta: Double,
    val liquido: Double,
    val percentual: Double,
    val faltaLiquido: Double,
    val brutoFaltante: Double,
    val brutoAlvo: Double,
    val kmFaltantes: Double,
    val margemPorKm: Double,
    val estimavel: Boolean,
    val bateu: Boolean,
)

/** Fechamento do dia: quanto entrou por app, km, horas, gastos e meta. */
data class DiaResumo(
    val data: String,
    val uber: Double = 0.0,
    val app99: Double = 0.0,
    val outros: Double = 0.0,
    val km: Double = 0.0,
    val minutos: Int = 0,
    val gastos: List<GastoDia> = emptyList(),
    /** Meta própria do dia. Zero significa "usar a meta dos custos". */
    val meta: Double = 0.0,
    val inicioMin: Int? = null,
    val fimMin: Int? = null,
    val online: Boolean = false,
) {
    val bruto: Double get() = uber + app99 + outros

    fun despesas(custos: Custos): Double =
        custos.custoPorKm * km + custos.outrosPorDia + gastos.sumOf { it.valor }

    fun liquido(custos: Custos): Double = bruto - despesas(custos)

    fun metaEfetiva(custos: Custos): Double = if (meta > 0) meta else custos.metaDiaria

    val porKm: Double get() = if (km > 0) bruto / km else 0.0

    /**
     * Projeção da meta. A média de faturamento por km (`porKm`) menos o custo
     * por km dá a margem real por km. O que falta de líquido é convertido em
     * km e em faturamento bruto — por isso o valor de "faltam" sobe quando o
     * custo por km sobe.
     */
    fun progressoMeta(custos: Custos): ProgressoMeta {
        val meta = metaEfetiva(custos)
        val liquido = liquido(custos)
        if (meta <= 0) {
            return ProgressoMeta(
                meta = 0.0,
                liquido = liquido,
                percentual = 0.0,
                faltaLiquido = 0.0,
                brutoFaltante = 0.0,
                brutoAlvo = bruto,
                kmFaltantes = 0.0,
                margemPorKm = 0.0,
                estimavel = false,
                bateu = false,
            )
        }

        val percentual = (liquido / meta * 100.0).coerceIn(0.0, 100.0)
        val faltaLiquido = (meta - liquido).coerceAtLeast(0.0)
        val margemPorKm = porKm - custos.custoPorKm
        val bateu = faltaLiquido <= 0.0
        val estimavel = !bateu && porKm > 0.0 && margemPorKm > 0.0
        val kmFaltantes = if (estimavel) faltaLiquido / margemPorKm else 0.0
        val brutoFaltante = if (estimavel) {
            faltaLiquido + custos.custoPorKm * kmFaltantes
        } else {
            faltaLiquido
        }

        return ProgressoMeta(
            meta = meta,
            liquido = liquido,
            percentual = percentual,
            faltaLiquido = faltaLiquido,
            brutoFaltante = brutoFaltante,
            brutoAlvo = bruto + brutoFaltante,
            kmFaltantes = kmFaltantes,
            margemPorKm = margemPorKm,
            estimavel = estimavel,
            bateu = bateu,
        )
    }

    fun porHora(agoraMin: Int): Double {
        val m = minutosEfetivos(agoraMin)
        return if (m > 0) bruto * 60.0 / m else 0.0
    }

    /** Minutos trabalhados: usa início/fim quando existem; senão o campo livre. */
    fun minutosEfetivos(agoraMin: Int): Int {
        val inicio = inicioMin
        if (inicio != null && online) {
            return ((agoraMin - inicio) + 24 * 60) % (24 * 60)
        }
        if (inicio != null && fimMin != null) {
            return ((fimMin - inicio) + 24 * 60) % (24 * 60)
        }
        return minutos
    }
}

class DiaStore(context: Context) {

    private val sp = context.getSharedPreferences("dias", Context.MODE_PRIVATE)

    fun carregar(data: String): DiaResumo = DiaResumo(
        data = data,
        uber = sp.getFloat("${data}_uber", 0f).toDouble(),
        app99 = sp.getFloat("${data}_99", 0f).toDouble(),
        outros = sp.getFloat("${data}_outros", 0f).toDouble(),
        km = sp.getFloat("${data}_km", 0f).toDouble(),
        minutos = sp.getInt("${data}_min", 0),
        gastos = lerGastos(sp.getString("${data}_gastos", "") ?: ""),
        meta = sp.getFloat("${data}_meta", 0f).toDouble(),
        inicioMin = sp.getInt("${data}_inicio", -1).takeIf { it >= 0 },
        fimMin = sp.getInt("${data}_fim", -1).takeIf { it >= 0 },
        online = sp.getBoolean("${data}_online", false),
    )

    fun salvar(dia: DiaResumo) {
        sp.edit()
            .putFloat("${dia.data}_uber", dia.uber.toFloat())
            .putFloat("${dia.data}_99", dia.app99.toFloat())
            .putFloat("${dia.data}_outros", dia.outros.toFloat())
            .putFloat("${dia.data}_km", dia.km.toFloat())
            .putInt("${dia.data}_min", dia.minutos)
            .putString("${dia.data}_gastos", gravarGastos(dia.gastos))
            .putFloat("${dia.data}_meta", dia.meta.toFloat())
            .putInt("${dia.data}_inicio", dia.inicioMin ?: -1)
            .putInt("${dia.data}_fim", dia.fimMin ?: -1)
            .putBoolean("${dia.data}_online", dia.online)
            .apply()
    }

    fun diasSalvos(): List<String> =
        (sp.getStringSet("dias_salvos", emptySet()) ?: emptySet()).sortedDescending()

    fun salvarDia(data: String) {
        val atuais = sp.getStringSet("dias_salvos", emptySet())?.toMutableSet() ?: mutableSetOf()
        atuais.add(data)
        sp.edit().putStringSet("dias_salvos", atuais).apply()
    }

    fun removerDiaSalvo(data: String) {
        val atuais = sp.getStringSet("dias_salvos", emptySet())?.toMutableSet() ?: mutableSetOf()
        atuais.remove(data)
        sp.edit().putStringSet("dias_salvos", atuais).apply()
    }

    private fun gravarGastos(gastos: List<GastoDia>): String =
        gastos.joinToString("|") {
            it.descricao.replace("|", " ").replace("§", " ") + "§" + it.valor
        }

    private fun lerGastos(bruto: String): List<GastoDia> {
        if (bruto.isBlank()) return emptyList()
        return bruto.split("|").mapNotNull { item ->
            val partes = item.split("§")
            if (partes.size != 2) return@mapNotNull null
            val valor = partes[1].toDoubleOrNull() ?: return@mapNotNull null
            GastoDia(partes[0].ifBlank { "gasto" }, valor)
        }
    }
}
