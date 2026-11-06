package com.pontoverde.app.capture

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.pontoverde.app.core.Avaliacao
import com.pontoverde.app.core.Cor
import com.pontoverde.app.core.Formato
import com.pontoverde.app.data.CampoCartao as Campo
import com.pontoverde.app.data.Configuracao
import com.pontoverde.app.data.PosicaoCartao
import kotlin.math.abs
import kotlin.math.min

/**
 * Cartão flutuante em Views. Mostra só os campos escolhidos em Ajustes, escala
 * conforme o tamanho configurado e, no modo tablet, fica dentro da área da
 * janela do app que gerou a oferta.
 */
class CartaoFlutuante(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var raiz: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val esconderRunnable = Runnable { esconder() }

    /** Verdadeiro enquanto o cartão está na tela (entre mostrar e esconder). */
    val visivel: Boolean get() = raiz != null

    /** Chamado quando o usuário toca no card (sem arrastar). */
    var aoClicar: (() -> Unit)? = null
    /** Chamado quando o card some da tela (tempo esgotado ou toque). */
    var aoEsconder: (() -> Unit)? = null

    fun mostrar(
        a: Avaliacao,
        alertas: String,
        cfg: Configuracao,
        area: Rect? = null,
        rotuloOrigem: String? = null,
        aoClicar: (() -> Unit)? = null,
        segundosRestantesRecusa: Int = 0,
        aoEsconder: (() -> Unit)? = null,
    ) {
        this.aoClicar = aoClicar
        this.aoEsconder = aoEsconder
        val escala = cfg.tamanhoCartao.escala
        val campos = cfg.camposCartao
        val nome = rotuloOrigem ?: a.oferta.app.rotulo

        val conteudo = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f * escala), dp(11f * escala), dp(16f * escala), dp(13f * escala))
            background = GradientDrawable().apply {
                cornerRadius = dp(18f * escala).toFloat()
                setColor(corDe(a.corFinal))
            }
        }

        conteudo.addView(texto("$nome · ${Formato.corTexto(a.corFinal)}", 12f, escala, 0.88f))
        conteudo.addView(texto(Formato.dinheiro(a.oferta.valor), 26f, escala, 1f).apply {
            setPadding(0, dp(2f), 0, dp(6f * escala))
        })

        if (segundosRestantesRecusa > 0) {
            conteudo.addView(texto(
                "Recusa automática em ${segundosRestantesRecusa}s",
                11f, escala, 1f
            ).apply {
                setTextColor(Color.parseColor("#FFFFFF"))
                setPadding(0, dp(6f * escala), 0, 0)
            })
        }

        if (Campo.KM in campos) {
            conteudo.addView(linha("R$/km", Formato.numero(a.oferta.rsPorKm), a.corKm, escala))
        }
        if (Campo.HORA in campos) {
            conteudo.addView(linha("R$/h", Formato.numero(a.oferta.rsPorHora), a.corHora, escala))
        }
        if (Campo.MINUTO in campos) {
            conteudo.addView(linhaNeutra("R$/min", Formato.numero(a.oferta.rsPorMinuto), escala))
        }
        if (Campo.SO_TRECHO in campos) {
            conteudo.addView(linhaNeutra("só trecho", Formato.numero(a.oferta.rsPorKmTrecho) + "/km", escala))
        }

        val lucro = a.oferta.lucro(cfg.custos.custoPorKm)
        if (Campo.LUCRO in campos) {
            if (cfg.custos.configurado) {
                val corLucro = if (lucro >= 0) Cor.VERDE else Cor.VERMELHO
                conteudo.addView(linha("lucro", Formato.dinheiro(lucro), corLucro, escala))
            } else {
                conteudo.addView(linhaNeutra("lucro", "configure os custos", escala))
            }
        }

        if (Campo.EMBARQUE in campos) {
            a.oferta.kmEmbarque?.let {
                conteudo.addView(linhaNeutra("embarque", Formato.km(it), escala))
            }
        }
        if (Campo.NOTA in campos) {
            a.oferta.nota?.let {
                conteudo.addView(linha("nota", Formato.nota(it), a.corNota ?: Cor.VERDE, escala))
            }
        }
        if (Campo.TEMPO in campos) {
            val tempo = if (a.oferta.minEmbarque != null) {
                Formato.minutos(a.oferta.minutosTotais) + " total"
            } else {
                Formato.minutos(a.oferta.minTrecho) + " de viagem"
            }
            conteudo.addView(texto(tempo, 11f, escala, 0.7f).apply {
                setPadding(0, dp(6f * escala), 0, 0)
            })
        }
        if (alertas.isNotBlank()) {
            conteudo.addView(texto(alertas, 12f, escala, 0.95f).apply {
                setPadding(0, dp(8f * escala), 0, 0)
            })
        }

        exibir(conteudo, cfg, area, escala)
    }

    fun mostrarErro(rotulo: String, cfg: Configuracao, area: Rect? = null) {
        val escala = cfg.tamanhoCartao.escala
        val conteudo = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f * escala), dp(11f * escala), dp(16f * escala), dp(11f * escala))
            background = GradientDrawable().apply {
                cornerRadius = dp(18f * escala).toFloat()
                setColor(Color.parseColor("#2B2B2B"))
            }
        }
        conteudo.addView(texto(rotulo, 13f, escala, 1f))
        conteudo.addView(texto("não consegui ler esta oferta", 12f, escala, 0.75f))
        exibir(conteudo, cfg, area, escala)
    }

    private fun exibir(
        conteudo: LinearLayout,
        cfg: Configuracao,
        area: Rect?,
        escala: Float,
    ) {
        remover()
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            android.graphics.PixelFormat.TRANSLUCENT,
        )

        val margem = dp(8f * escala)
        if (area != null && area.width() > 0 && area.height() > 0) {
            // Modo tablet/DeX: o cartão mora dentro da janela do app de origem.
            val larguraMax = dp(360f * escala)
            val largura = min(area.width() - 2 * margem, larguraMax).coerceAtLeast(dp(150f))
            lp.width = largura
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = when (cfg.posicaoCartao) {
                PosicaoCartao.ESQUERDA -> area.left + margem
                PosicaoCartao.CENTRO -> area.left + (area.width() - largura) / 2
                PosicaoCartao.DIREITA -> area.right - largura - margem
            }
            lp.y = area.top + margem
        } else {
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT
            lp.gravity = when (cfg.posicaoCartao) {
                PosicaoCartao.ESQUERDA -> Gravity.TOP or Gravity.START
                PosicaoCartao.CENTRO -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
                PosicaoCartao.DIREITA -> Gravity.TOP or Gravity.END
            }
            lp.x = if (cfg.posicaoCartao == PosicaoCartao.CENTRO) 0 else dp(12f)
            lp.y = alturaBarraStatus() + dp(6f)
        }

        conteudo.alpha = (cfg.cartaoOpacidade.coerceIn(20, 100)) / 100f
        arrastavel(conteudo, lp)

        runCatching { wm.addView(conteudo, lp) }.onFailure { return }
        raiz = conteudo
        params = lp
        handler.removeCallbacks(esconderRunnable)
        handler.postDelayed(esconderRunnable, cfg.cartaoSegundos.coerceAtLeast(2) * 1000L)
    }

    private fun arrastavel(view: View, lp: WindowManager.LayoutParams) {
        var inicialX = 0
        var inicialY = 0
        var toqueX = 0f
        var toqueY = 0f
        view.setOnTouchListener { _, evento ->
            when (evento.action) {
                MotionEvent.ACTION_DOWN -> {
                    inicialX = lp.x
                    inicialY = lp.y
                    toqueX = evento.rawX
                    toqueY = evento.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = inicialX + (evento.rawX - toqueX).toInt()
                    lp.y = inicialY + (evento.rawY - toqueY).toInt()
                    runCatching { wm.updateViewLayout(view, lp) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val mexeu = abs(evento.rawX - toqueX) > dp(8f) ||
                        abs(evento.rawY - toqueY) > dp(8f)
                    if (!mexeu) {
                        aoClicar?.invoke()
                    }
                    esconder()
                    true
                }
                else -> false
            }
        }
    }

    fun esconder() {
        handler.removeCallbacks(esconderRunnable)
        aoEsconder?.invoke()
        remover()
    }

    private fun remover() {
        raiz?.let { runCatching { wm.removeView(it) } }
        raiz = null
        params = null
    }

    private fun linha(rotulo: String, valor: String, cor: Cor, escala: Float): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2f * escala), 0, dp(2f * escala))
            addView(texto(rotulo, 12f, escala, 0.8f).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = valor
                setTextColor(corDe(cor))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f * escala)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(8f * escala), dp(1f), dp(8f * escala), dp(1f))
                background = GradientDrawable().apply {
                    cornerRadius = dp(6f * escala).toFloat()
                    setColor(Color.parseColor("#F7F7F2"))
                }
            })
        }

    private fun linhaNeutra(rotulo: String, valor: String, escala: Float): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2f * escala), 0, dp(2f * escala))
            addView(texto(rotulo, 11f, escala, 0.65f).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = valor
                setTextColor(Color.parseColor("#4A4A45"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f * escala)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(6f * escala), dp(1f), dp(6f * escala), dp(1f))
                background = GradientDrawable().apply {
                    cornerRadius = dp(6f * escala).toFloat()
                    setColor(Color.parseColor("#EDEDE6"))
                }
            })
        }

    private fun texto(conteudo: String, tamanho: Float, escala: Float, alfa: Float): TextView =
        TextView(context).apply {
            text = conteudo
            setTextColor(Color.WHITE)
            alpha = alfa
            setTextSize(TypedValue.COMPLEX_UNIT_SP, tamanho * escala)
        }

    private fun corDe(cor: Cor): Int = Color.parseColor(
        when (cor) {
            Cor.VERDE -> "#1B7F4B"
            Cor.AMARELO -> "#B7791F"
            Cor.VERMELHO -> "#B3261E"
        },
    )

    private fun alturaBarraStatus(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(24f)
    }

    private fun dp(valor: Float): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            valor,
            context.resources.displayMetrics,
        ).toInt()
}
