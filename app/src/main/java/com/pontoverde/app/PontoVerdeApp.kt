package com.pontoverde.app

import android.app.Application
import com.pontoverde.app.capture.TelaPretaOverlay
import com.pontoverde.app.data.Preferencias
import com.pontoverde.app.data.Repositorio

class PontoVerdeApp : Application() {

    lateinit var prefs: Preferencias
        private set
    lateinit var repositorio: Repositorio
        private set

    override fun onCreate() {
        super.onCreate()
        instancia = this
        prefs = Preferencias(this)
        repositorio = Repositorio(this)
        // A tela preta pertence ao processo, não à tela do app: assim ela volta
        // ligada depois de um fechamento e vale com o app aberto no Smart Connect.
        TelaPretaOverlay.iniciar(this)
    }

    companion object {
        lateinit var instancia: PontoVerdeApp
            private set
    }
}
