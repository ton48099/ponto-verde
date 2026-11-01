package com.pontoverde.app.capture

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.FrameLayout
import android.widget.TextView
import com.pontoverde.app.data.Configuracao

/**
 * Overlay de tela preta OLED. Quando ativado em Ajustes → Copiloto, cobre a tela
 * principal de preto para evitar aquecimento quando apps (ex.: 99) insistem em
 * ligar a tela. Sai ao tocar no "X" cinza no canto direito.
 *
 * Tenta cobrir tudo (status bar, navigation bar e notificações) para ficar
 * o mais escuro possível em OLED.
 *
 * Requer a permissão SYSTEM_ALERT_WINDOW (já declarada no manifesto).
 */
class TelaPretaOverlay(private val contexto: Context) {

    private val wm = contexto.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var raiz: View? = null
    private var params: WindowManager.LayoutParams? = null

    /** Mostra o overlay preto, se ainda não estiver mostrando. */
    fun mostrar() {
        if (raiz != null) return
        val container = FrameLayout(contexto).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(Color.BLACK)
            // Tenta esconder navegação e status bar dentro do overlay.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val controller = windowInsetsController
                if (controller != null) {
                    controller.hide(WindowInsets.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE
                }
            } else {
                systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            }
        }
        val sair = TextView(contexto).apply {
            text = "X"
            setTextSize(22f)
            setTextColor(Color.parseColor("#9E9E9E")) // cinza médio
            setPadding(16, 16, 16, 16)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END,
            )
            setOnClickListener { esconder() }
        }
        container.addView(sair)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.FILL

        wm.addView(container, lp)
        raiz = container
        params = lp
    }

    /** Esconde o overlay preto, se estiver mostrando. */
    fun esconder() {
        raiz?.let { runCatching { wm.removeView(it) } }
        raiz = null
        params = null
    }

    /** Verdadeiro enquanto o overlay está visível. */
    val estaVisível: Boolean get() = raiz != null
}
