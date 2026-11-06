package com.pontoverde.app.capture

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import kotlin.math.roundToInt

/**
 * Temperatura do aparelho, para a bolha flutuante mostrar se o celular está
 * esquentando.
 *
 * O sistema Android só entrega a **temperatura da bateria** para apps comuns —
 * a do processador exige permissão de sistema (`DEVICE_POWER`) e os arquivos de
 * `/sys/class/thermal` são bloqueados. A da bateria é a leitura que o próprio
 * aparelho usa para proteger contra aquecimento, e é a que aparece aqui.
 */
object Temperatura {

    /** Graus Celsius, ou nulo quando o sistema não informa. */
    fun celsius(context: Context): Double? {
        val bateria = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return null
        val decimos = bateria.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        if (decimos <= 0) return null
        return decimos / 10.0
    }

    /** "38°" ou "—" quando não há leitura. */
    fun texto(celsius: Double?): String =
        celsius?.let { "${it.roundToInt()}°" } ?: "—"

    /** Branco no normal, âmbar acima de 40 °C e vermelho acima de 44 °C. */
    fun cor(celsius: Double?): Int = when {
        celsius == null -> Color.parseColor("#E8F1E8")
        celsius >= 44.0 -> Color.parseColor("#FF8A80")
        celsius >= 40.0 -> Color.parseColor("#FFD180")
        else -> Color.WHITE
    }
}
