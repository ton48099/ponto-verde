package com.pontoverde.app.capture

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.pontoverde.app.PontoVerdeApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tela preta OLED. Cobre de preto a tela do **celular** (display principal) para
 * evitar aquecimento quando um app insiste em deixar a tela ligada.
 *
 * A janela é criada em duas camadas possíveis, nesta ordem:
 *
 * 1. **Janela do serviço de acessibilidade** (`TYPE_ACCESSIBILITY_OVERLAY`).
 *    Essa camada fica acima da barra de status e da de navegação — é o único
 *    jeito de um app comum cobrir a tela inteira. Só existe com o serviço
 *    conectado.
 * 2. **Sobreposição comum** (`TYPE_APPLICATION_OVERLAY`), usada quando o serviço
 *    está parado. Cobre o conteúdo dos apps, mas as barras do sistema são
 *    desenhadas por cima delas — e elas não podem ser escondidas por uma janela
 *    de sobreposição, então nesse modo as barras continuam à mostra.
 *
 * Antes a janela era criada com o contexto da activity, então ela ia para o
 * display em que o app estava desenhado: com o app aberto no Smart Connect a
 * tela preta aparecia no monitor (ou em lugar nenhum) e o celular continuava
 * aceso. Agora ela é uma janela de sistema do processo, criada num contexto do
 * display principal — vale com o app aberto em qualquer tela.
 *
 * O estado de verdade fica em [ativa]: é o que a interface mostra na aba
 * Copiloto, para conferir do Smart Connect se o celular está coberto.
 *
 * Requer a permissão SYSTEM_ALERT_WINDOW (já declarada no manifesto) para o
 * modo 2; o modo 1 vem do próprio serviço de acessibilidade.
 */
object TelaPretaOverlay {

    private const val TAG = "PontoVerde"

    private var aplicacao: Context? = null
    private var gerenciador: WindowManager? = null
    private var janela: View? = null

    private val _ativa = MutableStateFlow(false)

    /** Verdadeiro enquanto o preto está realmente cobrindo a tela principal. */
    val ativa: StateFlow<Boolean> = _ativa

    /**
     * Chamado uma vez no início do processo. Se a tela preta estava ligada quando
     * o app foi fechado, ela volta — o celular não fica aceso porque o processo
     * foi recriado.
     */
    fun iniciar(contexto: Context) {
        aplicacao = contexto.applicationContext
        val estavaLigada = runCatching {
            PontoVerdeApp.instancia.prefs.estado.value.telaPreta
        }.getOrDefault(false)
        if (estavaLigada) mostrar()
    }

    /** Liga a tela preta. Devolve falso se o sistema não deixou. */
    fun mostrar(): Boolean {
        if (janela != null) return true
        val contexto = aplicacao ?: return false

        if (adicionarComoAcessibilidade(contexto)) {
            _ativa.value = true
            salvarPref(ligada = true)
            Log.i(TAG, "tela preta ligada cobrindo as barras do sistema")
            return true
        }

        if (!adicionarComoSobreposicao(contexto)) {
            _ativa.value = false
            return false
        }
        _ativa.value = true
        salvarPref(ligada = true)
        Log.i(TAG, "tela preta ligada só sobre os apps (barras do sistema à mostra)")
        return true
    }

    /** Desliga a tela preta. */
    fun esconder() {
        removerJanela()
        if (_ativa.value) {
            _ativa.value = false
            salvarPref(ligada = false)
            Log.i(TAG, "tela preta desligada")
        }
    }

    /** Liga se estiver desligada, desliga se estiver ligada. */
    fun alternar(): Boolean {
        if (_ativa.value) {
            esconder()
            return false
        }
        return mostrar()
    }

    /**
     * Refaz a janela na camada mais alta disponível agora. Chamado quando o
     * serviço de acessibilidade conecta: a camada de acessibilidade só existe
     * com ele ligado, então a tela preta "sobe" quando o serviço volta.
     */
    fun reajustar() {
        if (janela == null) return
        removerJanela()
        if (!mostrar()) {
            _ativa.value = false
            salvarPref(ligada = false)
        }
    }

    private fun adicionarComoAcessibilidade(contexto: Context): Boolean {
        val servico = EstadoLeitor.servico ?: return false
        val wm = runCatching {
            servico.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }.getOrNull() ?: return false
        val raiz = montarRaiz(servico)
        val lp = parametros(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        if (!runCatching { wm.addView(raiz, lp) }.isSuccess) {
            Log.w(TAG, "tela preta: o serviço de acessibilidade recusou a janela")
            return false
        }
        janela = raiz
        gerenciador = wm
        return true
    }

    private fun adicionarComoSobreposicao(contexto: Context): Boolean {
        if (!Settings.canDrawOverlays(contexto)) {
            Log.w(TAG, "tela preta: sem permissão para sobrepor outros apps")
            return false
        }
        val wm = gerenciadorDaTelaPrincipal(contexto)
        if (wm == null) {
            Log.w(TAG, "tela preta: não consegui chegar na tela principal")
            return false
        }
        val raiz = montarRaiz(contexto)
        val lp = parametros(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        if (!runCatching { wm.addView(raiz, lp) }.isSuccess) {
            Log.w(TAG, "tela preta: o sistema recusou a janela")
            return false
        }
        janela = raiz
        gerenciador = wm
        return true
    }

    private fun parametros(tipo: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        tipo,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        PixelFormat.OPAQUE,
    ).apply {
        gravity = Gravity.FILL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Cobre também o recorte da câmera (o "notch").
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    private fun removerJanela() {
        janela?.let { vista -> runCatching { gerenciador?.removeView(vista) } }
        janela = null
        gerenciador = null
    }

    /**
     * WindowManager do display principal, para a janela cair sempre na tela do
     * celular — mesmo que a activity esteja desenhando no monitor externo.
     */
    private fun gerenciadorDaTelaPrincipal(contexto: Context): WindowManager? {
        val doDisplay = runCatching {
            val gerenciadorDeDisplay =
                contexto.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val tela = gerenciadorDeDisplay.getDisplay(Display.DEFAULT_DISPLAY)
                ?: return@runCatching null
            val contextoDaTela = contexto.createDisplayContext(tela)
            contextoDaTela.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }.getOrNull()
        return doDisplay
            ?: runCatching {
                contexto.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            }.getOrNull()
    }

    private fun montarRaiz(contexto: Context): View {
        val raiz = FrameLayout(contexto).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.BLACK)
            // Consome os toques para o app de baixo não receber nada por engano.
            isClickable = true
        }
        val sair = TextView(contexto).apply {
            text = "X"
            setTextSize(22f)
            setTextColor(Color.parseColor("#9E9E9E")) // cinza médio
            setPadding(24, 24, 24, 24)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            )
            setOnClickListener { esconder() }
        }
        raiz.addView(sair)
        return raiz
    }

    private fun salvarPref(ligada: Boolean) {
        runCatching {
            val prefs = PontoVerdeApp.instancia.prefs
            if (prefs.estado.value.telaPreta != ligada) {
                prefs.atualizar { it.copy(telaPreta = ligada) }
            }
        }
    }
}
