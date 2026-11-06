package com.pontoverde.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.pontoverde.app.data.Tema
import com.pontoverde.app.ui.PontoVerdeRoot
import com.pontoverde.app.ui.PontoVerdeTema

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            val config by PontoVerdeApp.instancia.prefs.estado.collectAsState()
            val sistemaEscuro = isSystemInDarkTheme()
            val escuro = when (config.tema) {
                Tema.CLARO -> false
                Tema.ESCURO -> true
                Tema.SISTEMA -> sistemaEscuro
            }
            val view = LocalView.current
            LaunchedEffect(escuro) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !escuro
                    isAppearanceLightNavigationBars = !escuro
                }
            }
            PontoVerdeTema(escuro) {
                PontoVerdeRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // O serviço escuta este fluxo e é dono de mostrar/esconder a bolha.
        com.pontoverde.app.capture.EstadoLeitor.appAberto.value = true
        runCatching { PontoVerdeApp.instancia.repositorio.recarregar() }
    }

    override fun onPause() {
        super.onPause()
        com.pontoverde.app.capture.EstadoLeitor.appAberto.value = false
    }
}
