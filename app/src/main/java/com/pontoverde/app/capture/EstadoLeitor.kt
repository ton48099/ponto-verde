package com.pontoverde.app.capture

import android.accessibilityservice.AccessibilityService
import com.pontoverde.app.core.Cor
import kotlinx.coroutines.flow.MutableStateFlow

/** Estado observável do serviço, para a tela inicial mostrar a verdade do sistema. */
object EstadoLeitor {
    val ativo = MutableStateFlow(false)
    val ultimaCor = MutableStateFlow<Cor?>(null)

    /** Quando foi a última oferta lida com sucesso, para ver se o leitor parou. */
    val ultimaLeitura = MutableStateFlow<Long?>(null)

    /**
     * Verdadeiro enquanto a tela principal está à frente. A bolha escuta isso:
     * dentro do app ela some, fora ela aparece.
     */
    val appAberto = MutableStateFlow(false)

    /**
     * Serviço conectado, quando existe. A janela dele fica acima da barra de
     * status e da de navegação — é por ela que a tela preta cobre tudo.
     */
    var servico: AccessibilityService? = null
}
