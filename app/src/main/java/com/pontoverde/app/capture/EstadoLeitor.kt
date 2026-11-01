package com.pontoverde.app.capture

import com.pontoverde.app.core.Cor
import kotlinx.coroutines.flow.MutableStateFlow

/** Estado observável do serviço, para a tela inicial mostrar a verdade do sistema. */
object EstadoLeitor {
    val ativo = MutableStateFlow(false)
    val ultimaCor = MutableStateFlow<Cor?>(null)

    /** Referência para a activity esconder/mostrar a bolha ao entrar e sair. */
    @Volatile
    var bolha: BolhaFlutuante? = null

    /** Verdadeiro enquanto a tela principal está à frente. */
    @Volatile
    var appAberto: Boolean = false
}
