package com.pontoverde.app.capture

import android.content.Context
import android.speech.tts.TextToSpeech
import com.pontoverde.app.core.Cor
import com.pontoverde.app.core.Formato
import java.util.Locale

/** Narra só a cor final, com a voz padrão do sistema. */
class Narrador(context: Context) {

    private var tts: TextToSpeech? = null
    private var pronto = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val idioma = runCatching { tts?.setLanguage(Locale("pt", "BR")) }.getOrNull()
                pronto = idioma != TextToSpeech.LANG_MISSING_DATA &&
                    idioma != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    fun falarCor(cor: Cor) {
        if (!pronto) return
        tts?.speak(
            "Oferta ${Formato.corTexto(cor)}",
            TextToSpeech.QUEUE_FLUSH,
            null,
            "ponto-verde",
        )
    }

    fun fechar() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        pronto = false
    }
}
