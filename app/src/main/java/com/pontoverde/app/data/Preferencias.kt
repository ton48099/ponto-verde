package com.pontoverde.app.data

import android.content.Context
import com.pontoverde.app.core.AppOrigem
import com.pontoverde.app.core.ConfigAlertas
import com.pontoverde.app.core.Custos
import com.pontoverde.app.core.Limiares
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class Tema { CLARO, ESCURO, SISTEMA }

enum class PosicaoCartao { ESQUERDA, CENTRO, DIREITA }

/** Linhas que podem aparecer no cartão. */
enum class CampoCartao(val rotulo: String) {
    KM("R$/km total"),
    HORA("R$/h total"),
    MINUTO("R$/min"),
    SO_TRECHO("R$/km só trecho"),
    EMBARQUE("km de embarque"),
    NOTA("nota do passageiro"),
    LUCRO("lucro real (R$)"),
    TEMPO("tempo total"),
}

enum class TamanhoCartao(val rotulo: String, val escala: Float) {
    PEQUENO("Pequeno", 0.85f),
    MEDIO("Médio", 1.0f),
    GRANDE("Grande", 1.3f),
    EXTRA("Extra grande", 1.75f),
}

val CAMPOS_PADRAO: Set<CampoCartao> = setOf(
    CampoCartao.KM,
    CampoCartao.HORA,
    CampoCartao.MINUTO,
    CampoCartao.EMBARQUE,
    CampoCartao.NOTA,
    CampoCartao.TEMPO,
)

/** Tudo o que é ajustável, num único objeto imutável. */
data class Configuracao(
    val tema: Tema = Tema.SISTEMA,
    val capturaLigada: Boolean = true,
    val turbo: Boolean = false,
    val cooldownMs: Int = 2000,
    val cartaoSegundos: Int = 8,
    val cartaoOpacidade: Int = 100,
    val posicaoCartao: PosicaoCartao = PosicaoCartao.ESQUERDA,
    val tamanhoCartao: TamanhoCartao = TamanhoCartao.MEDIO,
    val camposCartao: Set<CampoCartao> = CAMPOS_PADRAO,
    val modoTablet: Boolean = false,
    val avisarFalha: Boolean = false,
    val custos: Custos = Custos(),
    val vozLigada: Boolean = false,
    val salvarTexto: Boolean = false,
    val salvarPrint: Boolean = false,
    val modoTeste: Boolean = false,
    /** Tela preta OLED: cobre a tela principal de preto para evitar aquecimento quando o 99
     *  insiste em ligar a tela. Sai ao tocar no X do canto direito. */
    val telaPreta: Boolean = false,
    /** Recusa automática (experimental): recusa ofertas vermelhas após 5 s se o usuário não
     *  tocar no card. Pode falhar no Uber se o X não for exposto pela acessibilidade. */
    val recusaAutomatica: Boolean = false,
    val alertas: ConfigAlertas = ConfigAlertas(),
    /** Regra única, aplicada a Uber e 99. */
    val limiaresGlobais: Limiares = Limiares(AppOrigem.UBER),
    val appsLigados: Set<AppOrigem> = AppOrigem.reais.toSet(),
) {
    /** Regra única para todos os apps. */
    fun limiaresDe(app: AppOrigem): Limiares = limiaresGlobais
}

/**
 * Persistência de preferências. Sem dependência de terceiros: SharedPreferences
 * com um StateFlow em cima, para a interface reagir à mudança sem recarregar.
 */
class Preferencias(context: Context) {

    private val sp = context.getSharedPreferences("ponto_verde", Context.MODE_PRIVATE)
    private val _estado = MutableStateFlow(ler())
    val estado: StateFlow<Configuracao> = _estado

    fun atualizar(bloco: (Configuracao) -> Configuracao) {
        val novo = bloco(_estado.value)
        gravar(novo)
        _estado.value = novo
    }

    private fun ler(): Configuracao {
        val alertas = ConfigAlertas(
            valorMinimo = sp.getFloat("alerta_valorMinimo", 0f).toDouble(),
            embarqueMaxKm = sp.getFloat("alerta_embarqueKm", 0f).toDouble(),
            embarqueMaxMin = sp.getInt("alerta_embarqueMin", 0),
            trechoMaxKm = sp.getFloat("alerta_trechoKm", 0f).toDouble(),
            trechoMaxMin = sp.getInt("alerta_trechoMin", 0),
            mostrarParada = sp.getBoolean("alerta_parada", true),
            mostrarLocalBloqueado = sp.getBoolean("alerta_local", true),
            mostrarPassageiroNovo = sp.getBoolean("alerta_novo", true),
            mostrarValorMinimo = sp.getBoolean("alerta_mostrarValor", true),
            mostrarEmbarqueLonge = sp.getBoolean("alerta_mostrarEmbarqueKm", true),
            mostrarEmbarqueDemorado = sp.getBoolean("alerta_mostrarEmbarqueMin", true),
            mostrarTrechoLongo = sp.getBoolean("alerta_mostrarTrecho", true),
            mostrarViagemDemorada = sp.getBoolean("alerta_mostrarViagem", true),
            locaisBloqueados = lerLista("lista_origem"),
            destinosBloqueados = lerLista("lista_destino"),
        )
        val global = Limiares(
            app = AppOrigem.UBER,
            kmRuim = sp.getFloat("g_kmRuim", sp.getFloat("UBER_kmRuim", 1.4f)).toDouble(),
            kmBom = sp.getFloat("g_kmBom", sp.getFloat("UBER_kmBom", 1.8f)).toDouble(),
            horaRuim = sp.getFloat("g_horaRuim", sp.getFloat("UBER_horaRuim", 30f)).toDouble(),
            horaBom = sp.getFloat("g_horaBom", sp.getFloat("UBER_horaBom", 40f)).toDouble(),
            notaRuim = sp.getFloat("g_notaRuim", sp.getFloat("UBER_notaRuim", 4.7f)).toDouble(),
            notaBom = sp.getFloat("g_notaBom", sp.getFloat("UBER_notaBom", 4.9f)).toDouble(),
        )
        return Configuracao(
            tema = runCatching {
                Tema.valueOf(sp.getString("tema", "SISTEMA")!!)
            }.getOrDefault(Tema.SISTEMA),
            capturaLigada = sp.getBoolean("captura_ligada", true),
            turbo = sp.getBoolean("turbo", false),
            cooldownMs = sp.getInt("cooldown_ms", 2000),
            cartaoSegundos = sp.getInt("cartao_segundos", 8),
            cartaoOpacidade = sp.getInt("cartao_opacidade", 100),
            posicaoCartao = runCatching {
                PosicaoCartao.valueOf(sp.getString("posicao_cartao", "ESQUERDA")!!)
            }.getOrDefault(PosicaoCartao.ESQUERDA),
            tamanhoCartao = runCatching {
                TamanhoCartao.valueOf(sp.getString("tamanho_cartao", "MEDIO")!!)
            }.getOrDefault(TamanhoCartao.MEDIO),
            camposCartao = (sp.getStringSet("campos_cartao", null) ?: CAMPOS_PADRAO.map { it.name }.toSet())
                .mapNotNull { runCatching { CampoCartao.valueOf(it) }.getOrNull() }
                .toSet(),
            modoTablet = sp.getBoolean("modo_tablet", false),
            avisarFalha = sp.getBoolean("avisar_falha", false),
            custos = Custos(
                precoLitro = sp.getFloat("custo_preco_litro", 4.19f).toDouble(),
                kmPorLitro = sp.getFloat("custo_km_por_litro", 9f).toDouble(),
                manutencaoPorKm = sp.getFloat("custo_manutencao_km", 0.25f).toDouble(),
                outrosPorDia = sp.getFloat("custo_outros_dia", 0f).toDouble(),
                metaDiaria = sp.getFloat("custo_meta_dia", 300f).toDouble(),
            ),
            vozLigada = sp.getBoolean("voz_ligada", false),
            salvarTexto = sp.getBoolean("salvar_texto", false),
            salvarPrint = sp.getBoolean("salvar_print", false),
            modoTeste = sp.getBoolean("modo_teste", false),
            telaPreta = sp.getBoolean("tela_preta", false),
            recusaAutomatica = sp.getBoolean("recusa_automatica", false),
            alertas = alertas,
            limiaresGlobais = global,
            appsLigados = AppOrigem.reais.filter { sp.getBoolean("app_${it.name}", true) }.toSet(),
        )
    }

    private fun gravar(c: Configuracao) {
        sp.edit().apply {
            putString("tema", c.tema.name)
            putFloat("g_kmRuim", c.limiaresGlobais.kmRuim.toFloat())
            putFloat("g_kmBom", c.limiaresGlobais.kmBom.toFloat())
            putFloat("g_horaRuim", c.limiaresGlobais.horaRuim.toFloat())
            putFloat("g_horaBom", c.limiaresGlobais.horaBom.toFloat())
            putFloat("g_notaRuim", c.limiaresGlobais.notaRuim.toFloat())
            putFloat("g_notaBom", c.limiaresGlobais.notaBom.toFloat())
            putBoolean("captura_ligada", c.capturaLigada)
            putBoolean("turbo", c.turbo)
            putInt("cooldown_ms", c.cooldownMs)
            putInt("cartao_segundos", c.cartaoSegundos)
            putInt("cartao_opacidade", c.cartaoOpacidade)
            putString("posicao_cartao", c.posicaoCartao.name)
            putString("tamanho_cartao", c.tamanhoCartao.name)
            putStringSet("campos_cartao", c.camposCartao.map { it.name }.toSet())
            putBoolean("modo_tablet", c.modoTablet)
            putBoolean("avisar_falha", c.avisarFalha)
            putFloat("custo_preco_litro", c.custos.precoLitro.toFloat())
            putFloat("custo_km_por_litro", c.custos.kmPorLitro.toFloat())
            putFloat("custo_manutencao_km", c.custos.manutencaoPorKm.toFloat())
            putFloat("custo_outros_dia", c.custos.outrosPorDia.toFloat())
            putFloat("custo_meta_dia", c.custos.metaDiaria.toFloat())
            putBoolean("voz_ligada", c.vozLigada)
            putBoolean("salvar_texto", c.salvarTexto)
            putBoolean("salvar_print", c.salvarPrint)
            putBoolean("modo_teste", c.modoTeste)
            putBoolean("tela_preta", c.telaPreta)
            putBoolean("recusa_automatica", c.recusaAutomatica)
            putFloat("alerta_valorMinimo", c.alertas.valorMinimo.toFloat())
            putFloat("alerta_embarqueKm", c.alertas.embarqueMaxKm.toFloat())
            putInt("alerta_embarqueMin", c.alertas.embarqueMaxMin)
            putFloat("alerta_trechoKm", c.alertas.trechoMaxKm.toFloat())
            putInt("alerta_trechoMin", c.alertas.trechoMaxMin)
            putBoolean("alerta_parada", c.alertas.mostrarParada)
            putBoolean("alerta_local", c.alertas.mostrarLocalBloqueado)
            putBoolean("alerta_novo", c.alertas.mostrarPassageiroNovo)
            putBoolean("alerta_mostrarValor", c.alertas.mostrarValorMinimo)
            putBoolean("alerta_mostrarEmbarqueKm", c.alertas.mostrarEmbarqueLonge)
            putBoolean("alerta_mostrarEmbarqueMin", c.alertas.mostrarEmbarqueDemorado)
            putBoolean("alerta_mostrarTrecho", c.alertas.mostrarTrechoLongo)
            putBoolean("alerta_mostrarViagem", c.alertas.mostrarViagemDemorada)
            putString("lista_origem", c.alertas.locaisBloqueados.joinToString("|"))
            putString("lista_destino", c.alertas.destinosBloqueados.joinToString("|"))
            AppOrigem.reais.forEach { app ->
                putBoolean("app_${app.name}", app in c.appsLigados)
            }
        }.apply()
    }

    private fun lerLista(chave: String): Set<String> =
        (sp.getString(chave, "") ?: "")
            .split('|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
}
