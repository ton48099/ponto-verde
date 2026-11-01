package com.pontoverde.app.data

import android.content.Context
import com.pontoverde.app.capture.Prints
import com.pontoverde.app.core.Avaliacao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Ponte entre o avaliador e o banco: grava, lista e muda decisão. */
class Repositorio(context: Context) {

    private val appContext = context.applicationContext
    private val db = HistoricoDb(appContext)
    private val _registros = MutableStateFlow<List<RegistroOferta>>(emptyList())
    val registros: StateFlow<List<RegistroOferta>> = _registros

    init {
        recarregar()
    }

    fun recarregar() {
        _registros.value = db.listar()
    }

    fun registrar(
        avaliacao: Avaliacao,
        alertas: String,
        textoLido: String?,
        printPath: String? = null,
    ): Long {
        val oferta = avaliacao.oferta
        val id = db.inserir(
            RegistroOferta(
                id = 0,
                criadoEm = System.currentTimeMillis(),
                app = oferta.app,
                valor = oferta.valor,
                kmTrecho = oferta.kmTrecho,
                minTrecho = oferta.minTrecho,
                kmEmbarque = oferta.kmEmbarque,
                minEmbarque = oferta.minEmbarque,
                nota = oferta.nota,
                rsPorKm = oferta.rsPorKm,
                rsPorHora = oferta.rsPorHora,
                rsPorMinuto = oferta.rsPorMinuto,
                corKm = avaliacao.corKm,
                corHora = avaliacao.corHora,
                corNota = avaliacao.corNota,
                corEmbarque = avaliacao.corEmbarque,
                corFinal = avaliacao.corFinal,
                alertas = alertas,
                decisao = Decisao.NENHUMA,
                textoLido = textoLido,
                printPath = printPath,
            ),
        )
        recarregar()
        return id
    }

    fun definirDecisao(id: Long, decisao: Decisao) {
        db.definirDecisao(id, decisao)
        recarregar()
    }

    fun limpar() {
        db.limpar()
        Prints.limpar(appContext)
        recarregar()
    }
}
