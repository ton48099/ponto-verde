package com.pontoverde.app.capture

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

/** Leitura do estado real das permissões, sem cache. */
object Permissoes {

    /**
     * Serviço de acessibilidade ligado nas configurações do sistema.
     *
     * Isso **não** significa que ele está rodando: o Android pode matar o
     * processo do app e deixar o serviço ligado porém parado. Quem sabe da
     * conexão de verdade é [EstadoLeitor.ativo], marcado no onServiceConnected.
     */
    fun acessibilidadeHabilitada(context: Context): Boolean {
        val esperado = ComponentName(context, LeitorDeOfertasService::class.java)
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

    fun abrirAcessibilidade(context: Context) =
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

    /**
     * Verdadeiro quando o Android ainda pode matar o app para economizar bateria.
     * Nesse estado o sistema derruba o processo (e com ele a acessibilidade e o
     * copiloto) sem avisar.
     */
    fun otimizacaoDeBateriaLigada(context: Context): Boolean {
        val energia = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return !energia.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun pedirIgnorarOtimizacao(context: Context) {
        val direto = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val abriu = runCatching { context.startActivity(direto) }.isSuccess
        if (!abriu) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    fun abrirSobreposicao(context: Context) =
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
}
