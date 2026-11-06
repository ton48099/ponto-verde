package com.pontoverde.app.capture

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.view.Display
import com.pontoverde.app.core.AppOrigem
import com.pontoverde.app.core.Avaliacao
import com.pontoverde.app.PontoVerdeApp
import com.pontoverde.app.data.Configuracao

/**
 * Dono dos cartões. No modo normal existe um único cartão, no topo da tela.
 * No modo tablet/DeX existe um cartão por janela (app + display), permitindo
 * Uber e 99 ao mesmo tempo, cada um com o seu.
 */
class Cartoes(private val servico: AccessibilityService) {

    private val unico = CartaoFlutuante(servico)
    private val porJanela = mutableMapOf<String, CartaoFlutuante>()
    private val gerenciadorDeDisplay =
        servico.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    fun mostrar(
        pacote: String,
        rotulo: String?,
        displayId: Int,
        area: Rect?,
        avaliacao: Avaliacao,
        alertas: String,
        cfg: Configuracao,
        aoClicar: (() -> Unit)? = null,
        segundosRestantesRecusa: Int = 0,
        aoEsconder: (() -> Unit)? = null,
    ) {
        if (!cfg.modoTablet) {
            // Ao voltar para o modo normal, não deixa cartões de janela pendurados.
            if (porJanela.isNotEmpty()) {
                porJanela.values.forEach { it.esconder() }
                porJanela.clear()
            }
            unico.mostrar(avaliacao, alertas, cfg, null, null, aoClicar, segundosRestantesRecusa, aoEsconder)
            return
        }
        unico.esconder()
        cartao(pacote, displayId).mostrar(avaliacao, alertas, cfg, area, rotulo, aoClicar, segundosRestantesRecusa, aoEsconder)
    }

    fun erro(pacote: String, rotulo: String?, displayId: Int, area: Rect?, cfg: Configuracao) {
        if (!cfg.modoTablet) {
            unico.mostrarErro(rotulo ?: pacote, cfg)
            return
        }
        cartao(pacote, displayId).mostrarErro(rotulo ?: pacote, cfg, area)
    }

    fun esconderTudo() {
        unico.esconder()
        porJanela.values.forEach { it.esconder() }
        porJanela.clear()
    }

    /**
     * O cartão daquela janela está na tela? O leitor usa isso para não capturar
     * de novo enquanto o cartão aparece: o card da Uber pisca e, sem a guarda,
     * cada evento escondia e reescrevia o nosso cartão.
     */
    fun visivel(pacote: String, displayId: Int): Boolean {
        if (!PontoVerdeApp.instancia.prefs.estado.value.modoTablet) return unico.visivel
        return porJanela["$pacote@$displayId"]?.visivel == true
    }

    /** Esconde só o cartão daquela janela, antes de capturar a tela. */
    fun esconder(pacote: String, displayId: Int) {
        if (!PontoVerdeApp.instancia.prefs.estado.value.modoTablet) {
            unico.esconder()
            return
        }
        porJanela.remove("$pacote@$displayId")?.esconder()
    }

    private fun cartao(pacote: String, displayId: Int): CartaoFlutuante {
        val chave = "$pacote@$displayId"
        return porJanela.getOrPut(chave) { CartaoFlutuante(contextoDe(displayId)) }
    }

    private fun contextoDe(displayId: Int): Context {
        if (displayId == Display.DEFAULT_DISPLAY) return servico
        return runCatching {
            servico.createDisplayContext(gerenciadorDeDisplay.getDisplay(displayId))
        }.getOrDefault(servico)
    }
}
