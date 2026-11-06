package com.pontoverde.app.capture

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.pontoverde.app.MainActivity
import com.pontoverde.app.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Vigia do copiloto, em duas camadas:
 *
 * 1. **Notificação permanente** com a hora da última oferta lida e um cronômetro
 *    ("há 3 min"). O cronômetro é desenhado pelo próprio sistema a partir do
 *    horário gravado, então ele **continua contando mesmo se o Android matar o
 *    app** — a notificação fica na gaveta com o tempo certo. Não dá para sozinho
 *    emitir som depois da morte do processo.
 * 2. **Alarme** marcado para [LIMITE_MS] depois de cada leitura. Ele acorda o app
 *    (mesmo morto) e [VigiaRecebedor] publica o aviso sonoro quando a última
 *    leitura passou do limite. Cada leitura nova remarca o alarme.
 *
 * Como o copiloto é uma ferramenta de trabalho, o aviso é do tipo "parou de
 * funcionar", não um lembrete: usa canal de importância alta, com som e vibração.
 */
object VigiaDoLeitor {

    /** Depois de quanto tempo sem ler a oferta o leitor é dado como parado. */
    const val LIMITE_MS: Long = 10 * 60_000L

    val LIMITE_MINUTOS: Long get() = LIMITE_MS / 60_000L

    private const val TAG = "PontoVerde"
    private const val CANAL_ATIVO = "vigia_ativo"
    private const val CANAL_ALERTA = "vigia_alerta"
    private const val ID_ATIVO = 101
    private const val ID_ALERTA = 102
    private const val PREFS = "vigia"
    private const val CHAVE_ULTIMA = "ultima"
    private const val PEDIDO_ALARME = 55
    private const val PEDIDO_ABRIR = 56
    private val HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Minutos parados, quando já passaram do limite. Nulo enquanto está tudo bem
     * ou quando nunca houve leitura. Puro, para o teste não precisar de Android.
     */
    fun minutosParado(agora: Long, ultima: Long?): Long? {
        if (ultima == null || ultima <= 0L) return null
        if (agora < ultima) return null
        val minutos = (agora - ultima) / 60_000L
        return if (agora - ultima >= LIMITE_MS) minutos else null
    }

    fun podeNotificar(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun ultimaLeitura(context: Context): Long? =
        prefs(context).getLong(CHAVE_ULTIMA, 0L).takeIf { it > 0L }

    /** Chamado quando o serviço conecta e quando o copiloto é ligado. */
    fun ligar(context: Context) {
        criarCanais(context)
        atualizarAtiva(context)
        val ultima = ultimaLeitura(context) ?: return
        agendar(context, ultima + LIMITE_MS)
    }

    /** Chamado a cada oferta lida: atualiza a notificação e remarca o alarme. */
    fun registrarLeitura(context: Context) {
        val agora = System.currentTimeMillis()
        prefs(context).edit().putLong(CHAVE_ULTIMA, agora).apply()
        atualizarAtiva(context)
        gerenciador(context).cancel(ID_ALERTA)
        agendar(context, agora + LIMITE_MS)
    }

    /** Copiloto desligado ou serviço encerrado: a vigia não tem mais o que vigiar. */
    fun desligar(context: Context) {
        cancelarAlarme(context)
        val nm = gerenciador(context)
        nm.cancel(ID_ATIVO)
        nm.cancel(ID_ALERTA)
    }

    fun avisarParada(context: Context, minutos: Long) {
        if (!podeNotificar(context)) return
        criarCanais(context)
        val texto = "Nenhuma oferta lida há $minutos min. Confira se o copiloto " +
            "continua conectado."
        val aviso = Notification.Builder(context, CANAL_ALERTA)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("O copiloto parou de ler")
            .setContentText(texto)
            .setStyle(Notification.BigTextStyle().bigText(texto))
            .setContentIntent(abrirApp(context))
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        gerenciador(context).notify(ID_ALERTA, aviso)
        Log.i(TAG, "vigia: aviso de leitor parado há $minutos min")
    }

    private fun atualizarAtiva(context: Context) {
        if (!podeNotificar(context)) return
        criarCanais(context)
        val ultima = ultimaLeitura(context)
        val texto = if (ultima == null) {
            "Ainda não leu nenhuma oferta"
        } else {
            val hora = Instant.ofEpochMilli(ultima).atZone(ZoneId.systemDefault()).format(HORA)
            "Última oferta lida às $hora"
        }
        val construtor = Notification.Builder(context, CANAL_ATIVO)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Copiloto ativo")
            .setContentText(texto)
            .setContentIntent(abrirApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
        if (ultima != null) {
            // O cronômetro é do sistema: segue correndo mesmo sem o app vivo.
            construtor.setWhen(ultima).setShowWhen(true).setUsesChronometer(true)
        } else {
            construtor.setShowWhen(false)
        }
        gerenciador(context).notify(ID_ATIVO, construtor.build())
    }

    private fun criarCanais(context: Context) {
        val nm = gerenciador(context)
        val ativo = NotificationChannel(
            CANAL_ATIVO,
            "Copiloto ativo",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Hora da última oferta lida, sempre visível"
            setShowBadge(false)
        }
        val alerta = NotificationChannel(
            CANAL_ALERTA,
            "Leitor parado",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Avisa quando o copiloto fica sem ler ofertas"
        }
        nm.createNotificationChannel(ativo)
        nm.createNotificationChannel(alerta)
    }

    private fun agendar(context: Context, quando: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        // Inexato de propósito: não pede SCHEDULE_EXACT_ALARM e fura o Doze,
        // que é justamente onde o app costuma ser congelado.
        val marcado = runCatching {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, alarme(context))
        }.isSuccess
        Log.i(TAG, "vigia: alarme em ${(quando - System.currentTimeMillis()) / 60_000L} min (marcado=$marcado)")
    }

    private fun cancelarAlarme(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(alarme(context))
    }

    private fun alarme(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            PEDIDO_ALARME,
            Intent(context, VigiaRecebedor::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun abrirApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            PEDIDO_ABRIR,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun gerenciador(context: Context) =
        context.getSystemService(NotificationManager::class.java)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * Acorda para conferir a vigia. Recebe o alarme marcado em cada leitura e é a
 * única parte que ainda funciona depois de o Android matar o processo do app.
 */
class VigiaRecebedor : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ultima = VigiaDoLeitor.ultimaLeitura(context) ?: return
        val minutos = VigiaDoLeitor.minutosParado(System.currentTimeMillis(), ultima) ?: return
        VigiaDoLeitor.avisarParada(context, minutos)
    }
}
