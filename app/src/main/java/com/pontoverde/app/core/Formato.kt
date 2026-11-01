package com.pontoverde.app.core

import java.util.Locale

/** Formatação de números para a interface. Sempre vírgula decimal, 2 casas. */
object Formato {

    fun dinheiro(valor: Double): String = "R$ " + numero(valor)

    fun numero(valor: Double): String = String.format(Locale.US, "%.2f", valor).replace('.', ',')

    fun km(valor: Double): String = numero(valor) + " km"

    fun minutos(valor: Int): String = if (valor >= 60) {
        val h = valor / 60
        val m = valor % 60
        if (m == 0) "${h}h" else "${h}h${m.toString().padStart(2, '0')}"
    } else {
        "$valor min"
    }

    fun rsPorKm(valor: Double): String = numero(valor) + "/km"

    fun rsPorHora(valor: Double): String = numero(valor) + "/h"

    fun rsPorMinuto(valor: Double): String = numero(valor) + "/min"

    fun nota(valor: Double): String = String.format(Locale.US, "%.2f", valor).replace('.', ',')

    fun corTexto(cor: Cor): String = when (cor) {
        Cor.VERDE -> "verde"
        Cor.AMARELO -> "amarela"
        Cor.VERMELHO -> "vermelha"
    }
}
