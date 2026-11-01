package com.pontoverde.app.capture

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

/**
 * Bolha flutuante que aparece enquanto o leitor está ativo. Tocar faz uma
 * leitura forçada da tela; arrastar muda a posição, que fica salva.
 */
class BolhaFlutuante(private val context: Context, private val aoClicar: () -> Unit) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = context.getSharedPreferences("bolha", Context.MODE_PRIVATE)
    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null

    fun mostrar() {
        if (view != null) return
        if (!Settings.canDrawOverlays(context)) return

        val tamanho = dp(50)
        val bolha = TextView(context).apply {
            text = "PV"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            alpha = 0.92f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1B7F4B"))
                setStroke(dp(2), Color.parseColor("#E8F1E8"))
            }
        }

        val lp = WindowManager.LayoutParams(
            tamanho,
            tamanho,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", context.resources.displayMetrics.widthPixels - tamanho - dp(10))
            y = prefs.getInt("y", (context.resources.displayMetrics.heightPixels * 0.72f).toInt())
        }

        var inicioX = 0
        var inicioY = 0
        var toqueX = 0f
        var toqueY = 0f

        bolha.setOnTouchListener { _, evento ->
            when (evento.action) {
                MotionEvent.ACTION_DOWN -> {
                    inicioX = lp.x
                    inicioY = lp.y
                    toqueX = evento.rawX
                    toqueY = evento.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = inicioX + (evento.rawX - toqueX).toInt()
                    lp.y = inicioY + (evento.rawY - toqueY).toInt()
                    runCatching { wm.updateViewLayout(bolha, lp) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val mexeu = abs(evento.rawX - toqueX) > dp(8) || abs(evento.rawY - toqueY) > dp(8)
                    if (mexeu) {
                        prefs.edit().putInt("x", lp.x).putInt("y", lp.y).apply()
                    } else {
                        aoClicar()
                    }
                    true
                }
                else -> false
            }
        }

        runCatching { wm.addView(bolha, lp) }.onFailure { return }
        view = bolha
        params = lp
    }

    fun esconder() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        params = null
    }

    private fun dp(valor: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            valor.toFloat(),
            context.resources.displayMetrics,
        ).toInt()
}
