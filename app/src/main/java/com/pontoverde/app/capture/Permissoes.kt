package com.pontoverde.app.capture

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.pontoverde.app.MainActivity

/** Leitura do estado real das permissões, sem cache. */
object Permissoes {

    fun acessibilidadeAtiva(context: Context): Boolean {
        val esperado = ComponentName(context, LeitorDeOfertasService::class.java)
        // O serviço roda neste processo: conectado é prova direta de que está ligado.
        if (EstadoLeitor.ativo.value) return true
        val gerenciador = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        if (!gerenciador.isEnabled) return false
        val pelaLista = gerenciador
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                val info = it.resolveInfo.serviceInfo
                info.packageName == esperado.packageName && info.name == esperado.className
            }
        if (pelaLista) return true
        // Em alguns aparelhos a lista chega vazia para apps de terceiros, então o
        // ajuste do sistema (legível sem permissão) é a última palavra.
        val ativos = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        return ativos
            ?.split(':')
            ?.any { ComponentName.unflattenFromString(it) == esperado } == true
    }

    fun sobreposicaoAtiva(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun notificacoesAtivas(context: Context): Boolean =
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()

    fun abrirAcessibilidade(context: Context) =
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

    fun abrirSobreposicao(context: Context) =
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

    fun abrirNotificacoes(context: Context) =
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

    fun abrirApp(context: Context) =
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
}
