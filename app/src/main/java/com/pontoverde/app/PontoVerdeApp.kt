package com.pontoverde.app

import android.app.Application
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
    }

    companion object {
        lateinit var instancia: PontoVerdeApp
            private set
    }
}
