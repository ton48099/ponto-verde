package com.pontoverde.app.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.GestureDescription
import android.view.accessibility.AccessibilityWindowInfo
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pontoverde.app.PontoVerdeApp
import com.pontoverde.app.core.Alertas
import com.pontoverde.app.core.AppOrigem
import com.pontoverde.app.core.Avaliador
import com.pontoverde.app.core.Avaliacao
import com.pontoverde.app.core.Cor
import com.pontoverde.app.core.ParserOferta
import com.pontoverde.app.data.Configuracao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@SuppressLint("NewApi")
class LeitorDeOfertasService : AccessibilityService() {

    private val pacotesIgnorados = setOf(
        "com.android.systemui",
        "com.motorola.launcher3",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.motorola.timeweatherwidget",
        "com.motorola.desktop",
        "com.motorola.moto",
    )

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ultimoEvento = AtomicLong(0L)
    private val ultimaCaptura = AtomicLong(0L)
    private val ocupado = AtomicBoolean(false)
    private val recusaHandler = Handler(Looper.getMainLooper())
    private val vigia = Handler(Looper.getMainLooper())
    private var vigiaRunnable: Runnable? = null
    private var recusaRunnable: Runnable? = null
    private val contadorRecusaHandler = Handler(Looper.getMainLooper())
    private var contadorRecusaRunnable: Runnable? = null
    private var segundosRestantesRecusa = 0
    private var ultimaAvaliacao: Avaliacao? = null
    private var ultimosAlertas = ""
    private var ultimoCfg: Configuracao? = null
    private var ultimoPacote = ""
    private var ultimoRotulo: String? = null
    private var ultimoDisplayId = Display.DEFAULT_DISPLAY
    private var ultimaArea: Rect? = null
    private var reconhecedor: TextRecognizer? = null
    private var narrador: Narrador? = null
    private var cartoes: Cartoes? = null
    private val bolhas = mutableMapOf<Int, BolhaFlutuante>()
    private var gerenciadorDeDisplay: android.hardware.display.DisplayManager? = null

    private val ouvinteDeDisplay = object : android.hardware.display.DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = sincronizarBolhas()
        override fun onDisplayRemoved(displayId: Int) {
            bolhas.remove(displayId)?.esconder()
        }
        override fun onDisplayChanged(displayId: Int) = Unit
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        EstadoLeitor.servico = this
        reconhecedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        narrador = Narrador(this)
        cartoes = Cartoes(this)
        gerenciadorDeDisplay =
            getSystemService(android.content.Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        gerenciadorDeDisplay?.registerDisplayListener(ouvinteDeDisplay, null)
        sincronizarBolhas()
        // Janelas só podem ser criadas/removidas na thread principal: sem o
        // withContext abaixo, estas mudanças de ajuste falhavam caladas e a
        // bolha não aparecia.
        escopo.launch {
            PontoVerdeApp.instancia.prefs.estado.collect { cfg ->
                withContext(Dispatchers.Main) {
                    sincronizarBolhas()
                    if (!cfg.capturaLigada) cartoes?.esconderTudo()
                }
                // A vigia acompanha o copiloto: sem ele não há o que vigiar.
                if (cfg.capturaLigada) VigiaDoLeitor.ligar(this@LeitorDeOfertasService)
                else VigiaDoLeitor.desligar(this@LeitorDeOfertasService)
            }
        }
        escopo.launch {
            EstadoLeitor.appAberto.collect {
                withContext(Dispatchers.Main) { sincronizarBolhas() }
            }
        }
        EstadoLeitor.ativo.value = true
        // A janela da tela preta sobe para a camada da acessibilidade agora que
        // ela existe; sem o serviço ela só consegue ficar sobre os apps.
        TelaPretaOverlay.reajustar()
        // A vigia é ligada pelo coletor de preferências abaixo, que já emite o
        // estado atual na primeira coleta — ligar aqui também só duplicava o alarme.
    }

    override fun onDestroy() {
        EstadoLeitor.ativo.value = false
        EstadoLeitor.servico = null
        bolhas.values.forEach { it.esconder() }
        bolhas.clear()
        gerenciadorDeDisplay?.unregisterDisplayListener(ouvinteDeDisplay)
        cartoes?.esconderTudo()
        narrador?.fechar()
        reconhecedor?.close()
        reconhecedor = null
        VigiaDoLeitor.desligar(this)
        escopo.cancel()
        super.onDestroy()
    }

    private fun sincronizarBolhas() {
        val gerenciador = gerenciadorDeDisplay ?: return
        val cfg = PontoVerdeApp.instancia.prefs.estado.value
        val desejados = when {
            !cfg.capturaLigada -> emptyList()
            cfg.modoTablet -> gerenciador.displays.map { it.displayId }
            else -> listOf(Display.DEFAULT_DISPLAY)
        }

        bolhas.keys.filter { it !in desejados }.forEach { id ->
            bolhas.remove(id)?.esconder()
        }

        desejados.forEach { id ->
            val existente = bolhas[id]
            if (existente == null) {
                val contexto = if (id == Display.DEFAULT_DISPLAY) {
                    this
                } else {
                    runCatching {
                        createDisplayContext(gerenciador.getDisplay(id))
                    }.getOrDefault(this)
                }
                val bolha = BolhaFlutuante(contexto) {
                    capturarForcada()
                }
                bolhas[id] = bolha
                if (deveMostrar(id)) bolha.mostrar()
            } else {
                // A bolha some dentro do app e volta quando ele sai de frente.
                if (deveMostrar(id)) existente.mostrar() else existente.esconder()
            }
        }
    }

    private fun deveMostrar(displayId: Int): Boolean =
        displayId != Display.DEFAULT_DISPLAY || !EstadoLeitor.appAberto.value

    override fun onInterrupt() {
        ocupado.set(false)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pacote = event?.packageName?.toString() ?: return
        if (pacote == packageName) return

        val cfg = PontoVerdeApp.instancia.prefs.estado.value
        sincronizarBolhas()
        if (!cfg.capturaLigada) return

        val conhecido = AppOrigem.porPacote(pacote)
        val app = conhecido ?: if (cfg.modoTeste) AppOrigem.OUTRO else return
        if (conhecido != null && conhecido !in cfg.appsLigados) return
        if (app == AppOrigem.OUTRO && pacote in pacotesIgnorados) return

        val displayDoEvento = runCatching {
            event?.displayId ?: Display.DEFAULT_DISPLAY
        }.getOrDefault(Display.DEFAULT_DISPLAY)

        val janela = janelaDe(app, emTodasAsTelas = cfg.modoTablet, displayDoEvento)
            ?: janelaDoEvento(event)
            ?: if (cfg.modoTablet) janelaDoDisplay(displayDoEvento) else null

        if (cfg.modoTablet && janela == null) {
            Log.i(TAG, "sem janela para $pacote no display $displayDoEvento; ignorando")
            return
        }

        val pacoteAtivo = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
        if (pacoteAtivo == packageName) return
        if (cfg.modoTeste && !cfg.modoTablet && pacoteAtivo != null && pacoteAtivo != pacote) return

        val agora = SystemClock.elapsedRealtime()
        ultimoEvento.set(agora)

        val intervalo = if (cfg.turbo) TURBO_MS else cfg.cooldownMs.toLong()
        if (!reservar(agora, intervalo)) return
        if (!ocupar()) return

        Log.i(
            TAG,
            "lendo $pacote (${app.name}) display=${janela?.displayId} " +
                "area=${janela?.area} tablet=${cfg.modoTablet}",
        )
        cartoes?.esconder(pacote, janela?.displayId ?: displayDoEvento)
        capturar(app, cfg, janela, pacote)
    }

    private data class Janela(val area: Rect, val displayId: Int)

    private fun janelaDoEvento(event: AccessibilityEvent?): Janela? {
        val no = runCatching { event?.source }.getOrNull() ?: return null
        return try {
            val janela = runCatching { no.window }.getOrNull() ?: return null
            val area = Rect()
            janela.getBoundsInScreen(area)
            val displayId = runCatching { janela.displayId }
                .getOrDefault(Display.DEFAULT_DISPLAY)
            if (area.width() <= 0 || area.height() <= 0) {
                null
            } else {
                Log.i(TAG, "janela via evento: display=$displayId area=$area")
                Janela(area, displayId)
            }
        } finally {
            runCatching { no.recycle() }
        }
    }

    private fun janelaDe(
        app: AppOrigem,
        emTodasAsTelas: Boolean,
        displayDoEvento: Int,
    ): Janela? {
        runCatching {
            if (emTodasAsTelas) {
                val porDisplay = windowsOnAllDisplays
                for (i in 0 until porDisplay.size()) {
                    val displayId = porDisplay.keyAt(i)
                    val lista = porDisplay.valueAt(i) ?: continue
                    lista.firstOrNull { it.root?.packageName == app.pacote }?.let { janela ->
                        val area = Rect()
                        janela.getBoundsInScreen(area)
                        return Janela(area, displayId)
                    }
                }
            } else {
                windows.firstOrNull { it.root?.packageName == app.pacote }?.let { janela ->
                    val area = Rect()
                    janela.getBoundsInScreen(area)
                    return Janela(area, Display.DEFAULT_DISPLAY)
                }
            }
        }
        return null
    }

    private fun janelaDoDisplay(displayDoEvento: Int): Janela? {
        val gerenciador = getSystemService(android.content.Context.DISPLAY_SERVICE)
            as android.hardware.display.DisplayManager
        val alvo = if (displayDoEvento != Display.DEFAULT_DISPLAY) {
            displayDoEvento
        } else {
            gerenciador.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }?.displayId
        } ?: return null
        val display = gerenciador.getDisplay(alvo) ?: return null
        val tamanho = android.graphics.Point()
        runCatching { display.getRealSize(tamanho) }
        if (tamanho.x <= 0 || tamanho.y <= 0) return null
        Log.i(TAG, "sem janela; usando display $alvo inteiro")
        return Janela(Rect(0, 0, tamanho.x, tamanho.y), alvo)
    }

    /**
     * Marca o leitor como ocupado e arma o vigia. Devolve falso quando já existe
     * uma leitura em andamento.
     */
    private fun ocupar(): Boolean {
        if (!ocupado.compareAndSet(false, true)) return false
        agendarVigia()
        return true
    }

    /** Solta o leitor. Roda em qualquer caminho de saída, inclusive com erro. */
    private fun liberar() {
        ocupado.set(false)
        vigiaRunnable?.let { vigia.removeCallbacks(it) }
        vigiaRunnable = null
    }

    /**
     * Rede de segurança: se uma captura ou o OCR nunca responder (o serviço de
     * acessibilidade não avisa), o leitor destrava em vez de parar de ler
     * ofertas até o app ser reiniciado.
     */
    private fun agendarVigia() {
        vigiaRunnable?.let { vigia.removeCallbacks(it) }
        val vigiaNovo = Runnable {
            vigiaRunnable = null
            if (ocupado.get()) {
                Log.w(TAG, "leitura sem resposta há ${VIGIA_MS / 1000}s; liberando o leitor")
                ocupado.set(false)
            }
        }
        vigiaRunnable = vigiaNovo
        vigia.postDelayed(vigiaNovo, VIGIA_MS)
    }

    private fun reservar(agora: Long, intervalo: Long): Boolean {
        while (true) {
            val anterior = ultimaCaptura.get()
            if (agora - anterior < intervalo) return false
            if (ultimaCaptura.compareAndSet(anterior, agora)) return true
        }
    }

    private fun capturar(app: AppOrigem, cfg: Configuracao, janela: Janela?, pacote: String) {
        val displayId = if (cfg.modoTablet) {
            janela?.displayId ?: Display.DEFAULT_DISPLAY
        } else {
            Display.DEFAULT_DISPLAY
        }
        takeScreenshot(
            displayId,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val completa = paraBitmap(screenshot)
                    if (completa == null) {
                        falharLeitura(app, cfg, janela, pacote)
                        return
                    }
                    val recorte = recortar(completa, janela?.area, displayId)
                    reconhecer(app, cfg, recorte, janela, pacote)
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "takeScreenshot falhou: $errorCode")
                    falharLeitura(app, cfg, janela, pacote)
                }
            },
        )
    }

    /** Leitura forçada acionada ao tocar na bolha flutuante. Captura a tela inteira e processa. */
    private fun capturarForcada() {
        if (!ocupar()) return

        val cfg = PontoVerdeApp.instancia.prefs.estado.value
        if (!cfg.capturaLigada) {
            liberar()
            return
        }

        val displayId = Display.DEFAULT_DISPLAY
        takeScreenshot(
            displayId,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val completa = paraBitmap(screenshot)
                    if (completa == null) {
                        falharLeitura(AppOrigem.OUTRO, cfg, null, "desconhecido")
                        return
                    }
                    val recorte = recortar(completa, null, displayId)
                    val pacoteAtivo = runCatching { rootInActiveWindow?.packageName?.toString() }
                        .getOrNull() ?: "desconhecido"
                    val app = AppOrigem.porPacote(pacoteAtivo) ?: AppOrigem.OUTRO
                    reconhecer(app, cfg, recorte, null, pacoteAtivo)
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "takeScreenshot forçado falhou: $errorCode")
                    falharLeitura(AppOrigem.OUTRO, cfg, null, "desconhecido")
                }
            },
        )
    }

    private fun paraBitmap(screenshot: ScreenshotResult): Bitmap? {
        val buffer = screenshot.hardwareBuffer
        return try {
            Bitmap.wrapHardwareBuffer(buffer, null)?.copy(Bitmap.Config.ARGB_8888, false)
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { buffer.close() }
        }
    }

    private fun recortar(completa: Bitmap, area: Rect?, displayId: Int): Bitmap {
        if (area == null || area.width() <= 0 || area.height() <= 0) return completa

        val gerenciador = getSystemService(android.content.Context.DISPLAY_SERVICE)
            as android.hardware.display.DisplayManager
        val display = gerenciador.getDisplay(displayId)
        val real = android.graphics.Point()
        runCatching { display?.getRealSize(real) }

        var left = area.left
        var top = area.top
        var right = area.right
        var bottom = area.bottom

        val estouraX = right > completa.width + completa.width / 10 ||
            left < -completa.width / 10
        val estouraY = bottom > completa.height + completa.height / 10 ||
            top < -completa.height / 10
        if ((estouraX || estouraY) && real.x > 0 && real.y > 0) {
            val sx = completa.width.toFloat() / real.x
            val sy = completa.height.toFloat() / real.y
            left = (left * sx).toInt()
            right = (right * sx).toInt()
            top = (top * sy).toInt()
            bottom = (bottom * sy).toInt()
            Log.i(TAG, "recorte reescalado sx=$sx sy=$sy")
        }

        Log.i(
            TAG,
            "recorte bitmap=${completa.width}x${completa.height} display=${real.x}x${real.y} " +
                "area=($left,$top)-($right,$bottom)",
        )

        val x = left.coerceIn(0, (completa.width - 1).coerceAtLeast(0))
        val y = top.coerceIn(0, (completa.height - 1).coerceAtLeast(0))
        val w = (right - left).coerceAtMost(completa.width - x)
        val h = (bottom - top).coerceAtMost(completa.height - y)
        if (w <= 0 || h <= 0) return completa
        return runCatching { Bitmap.createBitmap(completa, x, y, w, h) }.getOrDefault(completa)
    }

    private fun reconhecer(
        app: AppOrigem,
        cfg: Configuracao,
        recorte: Bitmap,
        janela: Janela?,
        pacote: String,
    ) {
        val imagem = InputImage.fromBitmap(recorte, 0)
        val leitor = reconhecedor
        if (leitor == null) {
            falharLeitura(app, cfg, janela, pacote)
            recorte.recycle()
            return
        }
        leitor.process(imagem)
            .addOnSuccessListener { texto -> tratar(app, cfg, texto, recorte, janela, pacote) }
            .addOnFailureListener {
                Log.w(TAG, "OCR falhou")
                falharLeitura(app, cfg, janela, pacote)
                recorte.recycle()
            }
    }

    private fun tratar(
        app: AppOrigem,
        cfg: Configuracao,
        resultado: Text,
        recorte: Bitmap,
        janela: Janela?,
        pacote: String,
    ) {
        val texto = resultado.text
        Log.i(TAG, "OCR: ${texto.length} caracteres")

        escopo.launch {
            try {
                var lido = ParserOferta.ler(texto)
                var fonte = "auto"
                var iaBruta: String? = null

                // Corretor opcional: só chama a rede quando o parser local não
                // entendeu (ou quando o usuário pediu IA em toda leitura).
                if (CorretorIA.disponivel(cfg) &&
                    (lido == null || lido.kmTrecho <= 0 || cfg.iaSempre)
                ) {
                    withContext(Dispatchers.Main) { agendarVigia() }
                    val correcao = CorretorIA.corrigir(cfg, texto)
                    iaBruta = correcao.bruto
                    if (correcao.lido != null) {
                        lido = correcao.lido
                        fonte = "ia"
                    }
                    Log.i(
                        TAG,
                        "corretor IA: ${correcao.erro ?: "ok"} · " +
                            "leu=${correcao.lido != null}",
                    )
                }

                val oferta = lido?.let { ParserOferta.paraOferta(it, app) }
                    ?.takeIf { it.kmTrecho > 0 }
                val avaliacao = oferta?.let { Avaliador.avaliar(it, cfg.limiaresDe(app)) }
                val alertas = avaliacao?.let { Alertas.montar(it, cfg.alertas) }.orEmpty()

                val tela = buildString {
                    append("${recorte.width}x${recorte.height}")
                    janela?.let { append(" janela=${it.area} display=${it.displayId}") }
                }

                // Log de treino: imagem + OCR bruto + o que o parser entendeu.
                // Roda antes de reciclar o bitmap e nunca derruba a leitura.
                if (cfg.registrarLog) {
                    withContext(Dispatchers.IO) {
                        LogTreino.registrar(
                            context = this@LeitorDeOfertasService,
                            pacote = pacote,
                            app = app.name,
                            texto = texto,
                            lido = lido,
                            avaliacao = avaliacao,
                            bitmap = recorte,
                            tela = tela,
                            fonte = fonte,
                            iaBruta = iaBruta,
                        )
                    }
                }

                val print = if (cfg.salvarPrint) {
                    withContext(Dispatchers.IO) {
                        Prints.salvar(this@LeitorDeOfertasService, recorte)
                    }
                } else {
                    null
                }

                if (oferta == null || avaliacao == null) {
                    Log.i(TAG, "sem campos. OCR: " + texto.replace('\n', ' ').take(300))
                    if (print != null) Log.i(TAG, "print de diagnóstico: $print")
                    recorte.recycle()
                    withContext(Dispatchers.Main) {
                        falharLeitura(app, cfg, janela, pacote)
                    }
                    return@launch
                }

                val textoGuardar = if (cfg.salvarTexto) texto.take(4000) else null
                Log.i(
                    TAG,
                    "oferta ${oferta.valor} · ${oferta.kmTrecho} km · ${oferta.minTrecho} min · " +
                        "embarque ${oferta.kmEmbarque} → ${avaliacao.corFinal} (fonte=$fonte)",
                )

                withContext(Dispatchers.IO) {
                    PontoVerdeApp.instancia.repositorio.registrar(avaliacao, alertas, textoGuardar, print)
                }
                recorte.recycle()
                EstadoLeitor.ultimaLeitura.value = System.currentTimeMillis()
                VigiaDoLeitor.registrarLeitura(this@LeitorDeOfertasService)

                withContext(Dispatchers.Main) {
                    ultimaAvaliacao = avaliacao
                    ultimosAlertas = alertas
                    ultimoCfg = cfg
                    ultimoPacote = pacote
                    ultimoRotulo = if (app == AppOrigem.OUTRO) rotuloDoPacote(pacote) else app.rotulo
                    ultimoDisplayId = if (cfg.modoTablet) janela?.displayId ?: Display.DEFAULT_DISPLAY
                        else Display.DEFAULT_DISPLAY
                    ultimaArea = if (cfg.modoTablet) janela?.area else null

                    if (cfg.recusaAutomatica && avaliacao.corFinal == Cor.VERMELHO) {
                        agendarRecusa()
                    }
                    cartoes?.mostrar(
                        pacote = pacote,
                        rotulo = ultimoRotulo,
                        displayId = ultimoDisplayId,
                        area = ultimaArea,
                        avaliacao = avaliacao,
                        alertas = alertas,
                        cfg = cfg,
                        aoClicar = { cancelarRecusa() },
                        segundosRestantesRecusa = segundosRestantesRecusa,
                    )
                    EstadoLeitor.ultimaCor.value = avaliacao.corFinal
                    if (cfg.vozLigada) narrador?.falarCor(avaliacao.corFinal)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "falha ao guardar/abrir o cartão da oferta: ${t.message}")
                runCatching { recorte.recycle() }
            } finally {
                liberar()
            }
        }
    }

    private fun falharLeitura(
        app: AppOrigem,
        cfg: Configuracao,
        janela: Janela?,
        pacote: String,
    ) {
        Log.i(TAG, "falha de leitura em ${app.name} (${janela?.displayId})")
        if (cfg.avisarFalha) {
            cartoes?.erro(
                pacote = pacote,
                rotulo = if (app == AppOrigem.OUTRO) rotuloDoPacote(pacote) else app.rotulo,
                displayId = if (cfg.modoTablet) janela?.displayId ?: Display.DEFAULT_DISPLAY
                else Display.DEFAULT_DISPLAY,
                area = if (cfg.modoTablet) janela?.area else null,
                cfg = cfg,
            )
        }
        liberar()
    }

    private fun agendarRecusa() {
        cancelarRecusa()
        segundosRestantesRecusa = 5
        atualizarContadorRecusa()
        recusaRunnable = Runnable {
            recusaRunnable = null
            tentarRecusar()
        }
        recusaHandler.postDelayed(recusaRunnable!!, 5000L)
        contadorRecusaRunnable = object : Runnable {
            override fun run() {
                if (segundosRestantesRecusa > 0) {
                    segundosRestantesRecusa--
                    atualizarContadorRecusa()
                    contadorRecusaHandler.postDelayed(this, 1000L)
                }
            }
        }
        contadorRecusaHandler.postDelayed(contadorRecusaRunnable!!, 1000L)
        Log.i(TAG, "recusa automática agendada em 5 s")
    }

    private fun cancelarRecusa() {
        recusaRunnable?.let {
            recusaHandler.removeCallbacks(it)
            recusaRunnable = null
        }
        contadorRecusaRunnable?.let {
            contadorRecusaHandler.removeCallbacks(it)
            contadorRecusaRunnable = null
        }
        segundosRestantesRecusa = 0
        atualizarContadorRecusa()
        Log.i(TAG, "recusa automática cancelada")
    }

    private fun atualizarContadorRecusa() {
        val avaliacao = ultimaAvaliacao
        val cfg = ultimoCfg
        if (avaliacao != null && cfg != null) {
            cartoes?.mostrar(
                pacote = ultimoPacote,
                rotulo = ultimoRotulo,
                displayId = ultimoDisplayId,
                area = ultimaArea,
                avaliacao = avaliacao,
                alertas = ultimosAlertas,
                cfg = cfg,
                aoClicar = { cancelarRecusa() },
                segundosRestantesRecusa = segundosRestantesRecusa,
            )
        }
    }

    private fun tentarRecusar() {
        val raiz: AccessibilityNodeInfo? = rootInActiveWindow
        if (raiz == null) return
        val larguraTela = resources.displayMetrics.widthPixels
        val limiteDireita = larguraTela * 0.6f
        val fila = mutableListOf<AccessibilityNodeInfo>()
        fila.add(raiz)
        while (fila.isNotEmpty()) {
            val proxima = mutableListOf<AccessibilityNodeInfo>()
            for (no in fila) {
                if (no.isClickable && no.isEnabled) {
                    val texto = (no.text ?: no.contentDescription ?: "").toString().lowercase()
                    val ehBotaoRecusaPorTexto = texto == "x" || texto.contains("recusar") ||
                        texto.contains("cancelar") || texto.contains("não") ||
                        texto.contains("nao")
                    val bounds = Rect()
                    no.getBoundsInScreen(bounds)
                    val centroX = (bounds.left + bounds.right) / 2f
                    val estaADireita = centroX > limiteDireita
                    if (ehBotaoRecusaPorTexto || estaADireita) {
                        Log.i(TAG, "botão de recusa encontrado: '$texto' (centroX=$centroX de $larguraTela, bounds=$bounds)")
                        no.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        raiz.recycle()
                        return
                    }
                }
                for (i in 0 until no.childCount) {
                    val filho = no.getChild(i)
                    if (filho != null) proxima.add(filho)
                }
                no.recycle()
            }
            fila.clear()
            fila.addAll(proxima)
        }
        raiz.recycle()
        Log.i(TAG, "botão de recusa não encontrado na árvore de acessibilidade")
        tentarRecusarPorCoordenada()
    }

    private fun tentarRecusarPorCoordenada() {
        val largura = resources.displayMetrics.widthPixels
        val altura = resources.displayMetrics.heightPixels
        val x = (largura * 0.98f).toInt()
        val y = (altura * 0.10f).toInt()
        Log.i(TAG, "tentando recusa por coordenada: ($x, $y) de ($largura x $altura)")

        val caminho = Path().apply {
            moveTo((x - 20).toFloat(), (y - 20).toFloat())
            lineTo((x + 20).toFloat(), (y - 20).toFloat())
            lineTo((x + 20).toFloat(), (y + 20).toFloat())
            lineTo((x - 20).toFloat(), (y + 20).toFloat())
            lineTo((x - 20).toFloat(), (y - 20).toFloat())
        }
        val gesto = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(caminho, 200, 250))
            .build()
        dispatchGesture(gesto, null, null)
    }

    private fun rotuloDoPacote(pacote: String): String = runCatching {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pacote, 0)).toString()
    }.getOrDefault(pacote)

    companion object {
        const val TAG = "PontoVerde"
        const val TURBO_MS = 500L
        const val VIGIA_MS = 20_000L
    }
}