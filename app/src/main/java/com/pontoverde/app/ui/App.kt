package com.pontoverde.app.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pontoverde.app.PontoVerdeApp
import com.pontoverde.app.capture.EstadoLeitor
import com.pontoverde.app.capture.Permissoes
import com.pontoverde.app.capture.TelaPretaOverlay
import com.pontoverde.app.capture.VigiaDoLeitor
import com.pontoverde.app.core.Cor
import com.pontoverde.app.core.Formato
import com.pontoverde.app.data.Configuracao
import com.pontoverde.app.data.CampoCartao
import com.pontoverde.app.data.Decisao
import com.pontoverde.app.data.PosicaoCartao
import com.pontoverde.app.data.TamanhoCartao
import com.pontoverde.app.data.Tema
import com.pontoverde.app.data.RegistroOferta
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun mesmoDia(epoch: Long, dia: LocalDate): Boolean =
    Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate() == dia

/** Quando foi a última oferta lida, para dar para ver que o leitor parou. */
private fun textoUltimaLeitura(epoch: Long?): String {
    if (epoch == null) return "Nenhuma oferta lida desde que o app abriu."
    val quando = Instant.ofEpochMilli(epoch)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale("pt", "BR")))
    val minutos = (System.currentTimeMillis() - epoch) / 60_000
    return when {
        minutos < 1 -> "Última oferta lida agora ($quando)."
        minutos < 60 -> "Última oferta lida às $quando, há $minutos min."
        else -> "Última oferta lida às $quando, há ${minutos / 60} h."
    }
}

@Composable
fun PontoVerdeRoot() {
    val app = PontoVerdeApp.instancia
    val contexto = LocalContext.current
    val registros by app.repositorio.registros.collectAsState()
    val config by app.prefs.estado.collectAsState()
    val ativo by EstadoLeitor.ativo.collectAsState()
    val telaPreta by TelaPretaOverlay.ativa.collectAsState()
    val ultimaLeitura by EstadoLeitor.ultimaLeitura.collectAsState()

    var aba by remember { mutableIntStateOf(0) }
    var detalheId by remember { mutableStateOf<Long?>(null) }
    var calculadoraAberta by remember { mutableStateOf(false) }
    var custosAberto by remember { mutableStateOf(false) }
    var camposAberto by remember { mutableStateOf(false) }
    var devAberto by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    // A permissão de notificação é pedida na aba Copiloto, onde a vigia mora.
    val notificacoesOk = remember(tick) { VigiaDoLeitor.podeNotificar(contexto) }
    val pedirNotificacao = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { tick++ }

    val dono = LocalLifecycleOwner.current
    DisposableEffect(dono) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME) tick++
        }
        dono.lifecycle.addObserver(observador)
        onDispose { dono.lifecycle.removeObserver(observador) }
    }

    // Conectado de verdade é o que o serviço avisa; estar ligado nas configurações
    // não basta, porque o Android pode derrubar o processo e deixar o serviço parado.
    val acessibilidade = ativo
    val acessibilidadeHabilitada = remember(tick, ativo) {
        Permissoes.acessibilidadeHabilitada(contexto)
    }
    val otimizacaoDeBateria = remember(tick) { Permissoes.otimizacaoDeBateriaLigada(contexto) }
    val sobreposicao = remember(tick) { Permissoes.sobreposicaoAtiva(contexto) }

    val detalhe = registros.firstOrNull { it.id == detalheId }

    if (detalhe != null) {
        TelaDetalhe(
            registro = detalhe,
            aoVoltar = { detalheId = null },
            aoDecidir = { decisao -> app.repositorio.definirDecisao(detalhe.id, decisao) },
        )
        return
    }
    if (calculadoraAberta) {
        TelaCalculadora(aoVoltar = { calculadoraAberta = false })
        return
    }
    if (custosAberto) {
        TelaCustos(aoVoltar = { custosAberto = false })
        return
    }
    if (camposAberto) {
        TelaCamposCartao(aoVoltar = { camposAberto = false })
        return
    }
    if (devAberto) {
        TelaDev(
            aoVoltar = { devAberto = false },
            aoAbrirCalculadora = { calculadoraAberta = true },
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = aba == 0,
                    onClick = { aba = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("Início") },
                )
                NavigationBarItem(
                    selected = aba == 1,
                    onClick = { aba = 1 },
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    label = { Text("Copiloto") },
                )
                NavigationBarItem(
                    selected = aba == 2,
                    onClick = { aba = 2 },
                    icon = { Icon(Icons.Filled.List, contentDescription = null) },
                    label = { Text("Histórico") },
                )
                NavigationBarItem(
                    selected = aba == 3,
                    onClick = { aba = 3 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Ajustes") },
                )
            }
        },
    ) { espaco ->
        Box(Modifier.fillMaxSize().padding(espaco)) {
            when (aba) {
                0 -> ConteudoDia()
                1 -> TelaCopiloto(
                    registros = registros,
                    config = config,
                    ativo = acessibilidade,
                    habilitada = acessibilidadeHabilitada,
                    otimizacaoDeBateria = otimizacaoDeBateria,
                    telaPreta = telaPreta,
                    ultimaLeitura = ultimaLeitura,
                    notificacoesOk = notificacoesOk,
                    aoPedirNotificacoes = {
                        runCatching {
                            pedirNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    aoAbrirDetalhe = { detalheId = it },
                    aoAlternarCaptura = { ligado ->
                        app.prefs.atualizar { it.copy(capturaLigada = ligado) }
                    },
                    aoAlternarTelaPreta = { TelaPretaOverlay.alternar() },
                )
                2 -> TelaHistorico(
                    registros = registros,
                    aoAbrirDetalhe = { detalheId = it },
                    aoLimpar = { app.repositorio.limpar() },
                )
                else -> TelaAjustes(
                    config = config,
                    aoAbrirCustos = { custosAberto = true },
                    aoAbrirCampos = { camposAberto = true },
                    aoAbrirDev = { devAberto = true },
                    aoAlternar = { bloco -> app.prefs.atualizar(bloco) },
                )
            }
        }
    }
}

/**
 * Estado da vigia: notificação permanente com a hora da última oferta e aviso
 * quando o leitor passa muito tempo sem ler.
 */
@Composable
private fun VigiaDoCopiloto(
    ligada: Boolean,
    notificacoesOk: Boolean,
    aoPedirNotificacoes: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PontoStatus(ligada && notificacoesOk)
            Spacer(Modifier.width(8.dp))
            Text("Vigia do copiloto", fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (!notificacoesOk) {
                TextButton(onClick = aoPedirNotificacoes) { Text("Permitir") }
            }
        }
        Text(
            when {
                !notificacoesOk ->
                    "Sem permissão de notificação: a vigia não tem como mostrar a hora da " +
                        "última oferta nem avisar quando o leitor parar."
                ligada ->
                    "Fica na gaveta de notificações com a hora da última oferta e um " +
                        "cronômetro que continua contando mesmo se o Android matar o app. " +
                        "Passando de ${VigiaDoLeitor.LIMITE_MINUTOS} min sem ler, o celular " +
                        "apita e vibra."
                else ->
                    "A vigia aparece quando o copiloto está ligado e o serviço de " +
                        "acessibilidade conectado."
            },
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun TelaCopiloto(
    registros: List<RegistroOferta>,
    config: Configuracao,
    ativo: Boolean,
    habilitada: Boolean,
    otimizacaoDeBateria: Boolean,
    telaPreta: Boolean,
    ultimaLeitura: Long?,
    notificacoesOk: Boolean,
    aoPedirNotificacoes: () -> Unit,
    aoAbrirDetalhe: (Long) -> Unit,
    aoAlternarCaptura: (Boolean) -> Unit,
    aoAlternarTelaPreta: () -> Unit,
) {
    val contexto = LocalContext.current
    var engrenagemAberta by remember { mutableStateOf(false) }
    val hoje = LocalDate.now()
    val deHoje = registros.filter { mesmoDia(it.criadoEm, hoje) }
    val ultima = registros.firstOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("Copiloto", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "o leitor de ofertas e o que ele já viu",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Secao("Leitor") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PontoStatus(ativo)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        ativo -> "Serviço de acessibilidade conectado"
                        habilitada -> "Acessibilidade ligada, mas o serviço está parado"
                        else -> "Serviço de acessibilidade desligado"
                    },
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { aoAlternarCaptura(!config.capturaLigada) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (config.capturaLigada) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (config.capturaLigada) {
                        MaterialTheme.colorScheme.onError
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (config.capturaLigada) "Desativar copiloto" else "Ativar copiloto",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                if (config.capturaLigada) {
                    "Copiloto ligado: lê ofertas e mostra a bolha PV quando você está em outro app."
                } else {
                    "Copiloto desligado: não lê nenhuma oferta e a bolha PV fica escondida."
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                textoUltimaLeitura(ultimaLeitura),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            VigiaDoCopiloto(
                ligada = config.capturaLigada && ativo,
                notificacoesOk = notificacoesOk,
                aoPedirNotificacoes = aoPedirNotificacoes,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = aoAlternarTelaPreta,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (telaPreta) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (telaPreta) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (telaPreta) "Desativar tela preta OLED" else "Tela preta OLED",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                IconButton(onClick = { engrenagemAberta = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Status da tela preta")
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PontoStatus(telaPreta)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (telaPreta) "Tela preta ativa na tela do celular"
                    else "Tela preta desligada na tela do celular",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "Cobre de preto a tela do celular inteira — inclusive a barra de status e " +
                    "a de navegação (a do Smart Connect continua normal) — para evitar " +
                    "aquecimento quando o 99 insiste em ligar a tela. Para cobrir as barras " +
                    "o serviço de acessibilidade precisa estar conectado; sem ele o preto " +
                    "fica só sobre os apps. Toque no X para sair.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (!ativo) {
                Text(
                    if (habilitada) {
                        "O serviço está ligado nas configurações, mas parou de rodar — por isso " +
                            "nada é lido. Abra a acessibilidade, desligue e ligue o Ponto Verde de novo."
                    } else {
                        "O serviço de acessibilidade está desligado no sistema. Ative para o copiloto funcionar."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
                TextButton(onClick = { Permissoes.abrirAcessibilidade(contexto) }) {
                    Text("Abrir acessibilidade")
                }
            }
            if (otimizacaoDeBateria) {
                Text(
                    "O Android ainda pode matar o copiloto para economizar bateria — foi " +
                        "assim que as leituras pararam. Tire o Ponto Verde da lista de apps " +
                        "restritos.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
                TextButton(onClick = { Permissoes.pedirIgnorarOtimizacao(contexto) }) {
                    Text("Desativar otimização de bateria")
                }
            }
            Text(
                "Permissões e opções de teste ficam em Ajustes → Opções de desenvolvedor.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Secao("Hoje") {
            if (deHoje.isEmpty()) {
                Text(
                    "Nenhuma oferta lida hoje. Quando uma aparecer num app monitorado, ela cai aqui.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            } else {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Resumo("ofertas", deHoje.size.toString(), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    Resumo(
                        "verdes",
                        deHoje.count { it.corFinal == Cor.VERDE }.toString(),
                        corDe(Cor.VERDE),
                        Modifier.weight(1f),
                    )
                    Resumo(
                        "amarelas",
                        deHoje.count { it.corFinal == Cor.AMARELO }.toString(),
                        corDe(Cor.AMARELO),
                        Modifier.weight(1f),
                    )
                    Resumo(
                        "vermelhas",
                        deHoje.count { it.corFinal == Cor.VERMELHO }.toString(),
                        corDe(Cor.VERMELHO),
                        Modifier.weight(1f),
                    )
                }
            }
        }

        if (ultima != null) {
            Secao("Última oferta") {
                Box(Modifier.padding(vertical = 6.dp)) {
                    CartaoRegistro(ultima) { aoAbrirDetalhe(ultima.id) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (engrenagemAberta) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { engrenagemAberta = false },
            title = { Text("Tela preta OLED") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "A tela preta está ativa na tela principal?",
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Chip(
                            if (telaPreta) "Sim" else "Não",
                            if (telaPreta) Cor.VERDE else Cor.VERMELHO,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (telaPreta) {
                            "O celular está coberto de preto agora. O monitor do " +
                                "Smart Connect continua mostrando o app normalmente."
                        } else {
                            "Nada está cobrindo o celular agora: a tela dele pode " +
                                "estar ligando e esquentando fora da sua vista."
                        },
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "O status vem da janela que o app desenha no celular, não " +
                            "de um botão: se o X for tocado ou o sistema fechar a " +
                            "janela, ele muda sozinho.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { engrenagemAberta = false }) { Text("Fechar") }
            },
        )
    }
}

@Composable
private fun Resumo(
    rotulo: String,
    valor: String,
    cor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(valor, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = cor)
        Text(rotulo, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TelaHistorico(
    registros: List<RegistroOferta>,
    aoAbrirDetalhe: (Long) -> Unit,
    aoLimpar: () -> Unit,
) {
    var dia by remember { mutableStateOf(LocalDate.now()) }
    var confirmarLimpeza by remember { mutableStateOf(false) }
    var escolhendoDia by remember { mutableStateOf(false) }
    val doDia = registros.filter { mesmoDia(it.criadoEm, dia) }
    val formatoDia = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("pt", "BR"))
    val formatoDiaLongo = DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", Locale("pt", "BR"))
    val ehHoje = dia == LocalDate.now()

    fun escolherDia() {
        escolhendoDia = true
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { dia = dia.minusDays(1) }) { Text("‹", fontSize = 24.sp) }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { escolherDia() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    formatoDia.format(dia) + if (ehHoje) " · hoje" else "",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    formatoDiaLongo.format(dia).replaceFirstChar { it.uppercase() },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { if (!ehHoje) dia = dia.plusDays(1) }) {
                Text("›", fontSize = 24.sp)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { escolherDia() }) { Text("escolher dia") }
            if (!ehHoje) {
                TextButton(onClick = { dia = LocalDate.now() }) { Text("hoje") }
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (doDia.size == 1) "1 oferta" else "${doDia.size} ofertas",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (doDia.isNotEmpty()) {
            val total = doDia.sumOf { it.valor }
            val mediaKm = doDia.sumOf { it.rsPorKm } / doDia.size
            val mediaHora = doDia.sumOf { it.rsPorHora } / doDia.size
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Resumo(
                        "total do dia",
                        Formato.dinheiro(total),
                        MaterialTheme.colorScheme.onSurface,
                        Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Resumo(
                            "R$/km médio",
                            Formato.numero(mediaKm),
                            MaterialTheme.colorScheme.onSurface,
                            Modifier.weight(1f),
                        )
                        Resumo(
                            "R$/h médio",
                            Formato.numero(mediaHora),
                            MaterialTheme.colorScheme.onSurface,
                            Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (doDia.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Nenhuma oferta neste dia.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(doDia, key = { it.id }) { registro ->
                    CartaoRegistro(registro) { aoAbrirDetalhe(registro.id) }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { confirmarLimpeza = true },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Limpar histórico")
            }
            Spacer(Modifier.weight(1f))
            Text(
                "apaga ofertas e prints",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (escolhendoDia) {
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = dia.toEpochDay() * 86_400_000L,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= System.currentTimeMillis()
            },
        )
        DatePickerDialog(
            onDismissRequest = { escolhendoDia = false },
            confirmButton = {
                TextButton(
                    enabled = estado.selectedDateMillis != null,
                    onClick = {
                        estado.selectedDateMillis?.let {
                            dia = LocalDate.ofEpochDay(it / 86_400_000L)
                        }
                        escolhendoDia = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { escolhendoDia = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(
                state = estado,
                title = {
                    Text(
                        "Escolher o dia",
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                    )
                },
                showModeToggle = false,
            )
        }
    }

    if (confirmarLimpeza) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmarLimpeza = false },
            title = { Text("Apagar todo o histórico?") },
            text = { Text("Todas as ofertas e prints salvos serão removidos. As preferências continuam.") },
            confirmButton = {
                TextButton(onClick = {
                    aoLimpar()
                    confirmarLimpeza = false
                }) { Text("Apagar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarLimpeza = false }) { Text("Cancelar") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TelaDetalhe(
    registro: RegistroOferta,
    aoVoltar: () -> Unit,
    aoDecidir: (Decisao) -> Unit,
) {
    BackHandler { aoVoltar() }
    var aviso by remember { mutableStateOf<String?>(null) }
    val contexto = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Oferta · ${registro.app.rotulo}") },
                navigationIcon = {
                    TextButton(onClick = aoVoltar) { Text("‹ voltar") }
                },
            )
        },
    ) { espaco ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(espaco)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip(Formato.corTexto(registro.corFinal), registro.corFinal)
                Spacer(Modifier.width(8.dp))
                Text(dataHora(registro.criadoEm), fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                Formato.dinheiro(registro.valor),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                Formato.km(registro.kmTrecho) +
                    (registro.kmEmbarque?.let { " + " + Formato.km(it) + " de embarque" } ?: "") +
                    " · " + Formato.minutos(registro.minTrecho + (registro.minEmbarque ?: 0)) + " total" +
                    " · " + registro.app.rotulo,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Secao("Por que esta cor") {
                LinhaCriterio(
                    "R$/km total",
                    Formato.numero(registro.rsPorKm),
                    registro.corKm,
                    "valor ÷ (trecho + embarque) · verde a partir de 1,80 · vermelho até 1,40",
                )
                LinhaCriterio(
                    "R$/h total",
                    Formato.numero(registro.rsPorHora),
                    registro.corHora,
                    "valor ÷ (tempo de trecho + embarque) · verde a partir de 40,00",
                )
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("só trecho", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "sem o deslocamento · não classifica",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ChipNeutro(Formato.numero(registro.rsPorKmTrecho) + "/km")
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("R$/min", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "informativo, não classifica",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ChipNeutro(Formato.numero(registro.rsPorMinuto))
                }
                registro.kmEmbarque?.let {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("embarque", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "já entrou no R$/km e no R$/h totais",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ChipNeutro(Formato.km(it))
                    }
                }
                registro.corNota?.let {
                    LinhaCriterio(
                        "nota",
                        Formato.nota(registro.nota ?: 0.0),
                        it,
                        "verde a partir de 4,90 · vermelho até 4,70",
                    )
                }
            }

            Secao("Cartão") {
                Button(
                    onClick = {
                        if (Permissoes.sobreposicaoAtiva(contexto)) {
                            val oferta = com.pontoverde.app.core.Oferta(
                                app = registro.app,
                                valor = registro.valor,
                                kmTrecho = registro.kmTrecho,
                                minTrecho = registro.minTrecho,
                                kmEmbarque = registro.kmEmbarque,
                                nota = registro.nota,
                            )
                            val avaliacao = com.pontoverde.app.core.Avaliacao(
                                oferta = oferta,
                                corKm = registro.corKm,
                                corHora = registro.corHora,
                                corNota = registro.corNota,
                                corEmbarque = registro.corEmbarque,
                                corFinal = registro.corFinal,
                            )
                            com.pontoverde.app.capture.CartaoFlutuante(contexto).mostrar(
                                avaliacao,
                                registro.alertas,
                                PontoVerdeApp.instancia.prefs.estado.value,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) { Text("Mostrar no cartão") }
            }

            registro.printPath?.let { caminho ->
                Secao("Print da oferta") {
                    val imagem = remember(caminho) {
                        runCatching { android.graphics.BitmapFactory.decodeFile(caminho) }.getOrNull()
                    }
                    if (imagem != null) {
                        androidx.compose.foundation.Image(
                            bitmap = imagem.asImageBitmap(),
                            contentDescription = "Print da oferta",
                            contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    } else {
                        Text(
                            "O arquivo do print não está mais no aparelho.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    Button(
                        onClick = {
                            aviso = if (com.pontoverde.app.capture.Prints.exportarParaDownloads(contexto, caminho)) {
                                "Print salvo em Downloads/Ponto Verde."
                            } else {
                                "Não consegui salvar o print."
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) { Text("Baixar em Downloads") }
                }
            }

            aviso?.let {
                Text(
                    it,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }

            if (registro.alertas.isNotBlank()) {
                Secao("Alertas") {
                    Text(
                        registro.alertas,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            }

            Secao("Sua decisão") {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { aoDecidir(Decisao.ACEITA) },
                        enabled = registro.decisao != Decisao.ACEITA,
                        modifier = Modifier.weight(1f),
                    ) { Text("Aceitei") }
                    OutlinedButton(
                        onClick = { aoDecidir(Decisao.RECUSADA) },
                        enabled = registro.decisao != Decisao.RECUSADA,
                        modifier = Modifier.weight(1f),
                    ) { Text("Recusei") }
                }
                Text(
                    when (registro.decisao) {
                        Decisao.ACEITA -> "registrado: aceitei"
                        Decisao.RECUSADA -> "registrado: recusei"
                        Decisao.NENHUMA -> "sem decisão registrada"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            registro.textoLido?.let { texto ->
                Secao("Texto lido") {
                    Text(
                        texto,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LinhaCriterio(rotulo: String, valor: String, cor: Cor, explicacao: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(rotulo, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(explicacao, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Chip(valor, cor)
    }
}

@Composable
private fun TelaAjustes(
    config: Configuracao,
    aoAbrirCustos: () -> Unit,
    aoAbrirCampos: () -> Unit,
    aoAbrirDev: () -> Unit,
    aoAlternar: ((Configuracao) -> Configuracao) -> Unit,
) {

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("Ajustes", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))

        SecaoExpansivel(
            titulo = "Aparência",
            resumo = when (config.tema) {
                Tema.CLARO -> "tema claro"
                Tema.ESCURO -> "tema escuro (preto OLED)"
                Tema.SISTEMA -> "segue o sistema"
            },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Tema.CLARO to "Claro",
                    Tema.ESCURO to "Escuro",
                    Tema.SISTEMA to "Sistema",
                ).forEach { (tema, rotulo) ->
                    FilterChip(
                        selected = config.tema == tema,
                        onClick = { aoAlternar { it.copy(tema = tema) } },
                        label = { Text(rotulo, fontSize = 12.sp) },
                    )
                }
            }
        }

        SecaoExpansivel(
            titulo = "Regras",
            resumo = "R$/km ${Formato.numero(config.limiaresGlobais.kmRuim)}–" +
                "${Formato.numero(config.limiaresGlobais.kmBom)} · " +
                "R$/h ${Formato.numero(config.limiaresGlobais.horaRuim)}–" +
                "${Formato.numero(config.limiaresGlobais.horaBom)} · " +
                "nota ${Formato.nota(config.limiaresGlobais.notaRuim)}–" +
                "${Formato.nota(config.limiaresGlobais.notaBom)}",
        ) {
            Text(
                "Uma regra só, vale para Uber e 99. Verde a partir do valor bom; vermelho até o ruim.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            ParLimiares(
                rotulo = "R$/km",
                ruim = config.limiaresGlobais.kmRuim,
                bom = config.limiaresGlobais.kmBom,
                passo = 0.05,
                formatar = { Formato.numero(it) },
                aoMudarRuim = { v ->
                    aoAlternar { it.copy(limiaresGlobais = it.limiaresGlobais.copy(kmRuim = v)) }
                },
                aoMudarBom = { v ->
                    aoAlternar { it.copy(limiaresGlobais = it.limiaresGlobais.copy(kmBom = v)) }
                },
            )
            ParLimiares(
                rotulo = "R$/h",
                ruim = config.limiaresGlobais.horaRuim,
                bom = config.limiaresGlobais.horaBom,
                passo = 1.0,
                formatar = { Formato.numero(it) },
                aoMudarRuim = { v ->
                    aoAlternar { it.copy(limiaresGlobais = it.limiaresGlobais.copy(horaRuim = v)) }
                },
                aoMudarBom = { v ->
                    aoAlternar { it.copy(limiaresGlobais = it.limiaresGlobais.copy(horaBom = v)) }
                },
            )
            ParLimiares(
                rotulo = "nota",
                ruim = config.limiaresGlobais.notaRuim,
                bom = config.limiaresGlobais.notaBom,
                passo = 0.05,
                formatar = { Formato.nota(it) },
                aoMudarRuim = { v ->
                    aoAlternar { it.copy(limiaresGlobais = it.limiaresGlobais.copy(notaRuim = v)) }
                },
                aoMudarBom = { v ->
                    aoAlternar { it.copy(limiaresGlobais = it.limiaresGlobais.copy(notaBom = v)) }
                },
            )
        }

        SecaoExpansivel(
            titulo = "Custos",
            resumo = if (config.custos.configurado) {
                "custo por km " + Formato.dinheiro(config.custos.custoPorKm)
            } else {
                "combustível e manutenção"
            },
        ) {
            LinhaAcao(
                titulo = "Custos do veículo",
                descricao = "combustível, manutenção e meta diária",
                onClick = aoAbrirCustos,
            )
        }

        SecaoExpansivel(
            titulo = "Cartão",
            resumo = "${config.cartaoSegundos}s · ${config.tamanhoCartao.rotulo} · " +
                when (config.posicaoCartao) {
                    PosicaoCartao.ESQUERDA -> "esquerda"
                    PosicaoCartao.CENTRO -> "centro"
                    PosicaoCartao.DIREITA -> "direita"
                },
        ) {
            AjusteNumerico(
                rotulo = "tempo na tela",
                valor = "${config.cartaoSegundos}s",
                onMenos = { aoAlternar { it.copy(cartaoSegundos = (it.cartaoSegundos - 1).coerceAtLeast(3)) } },
                onMais = { aoAlternar { it.copy(cartaoSegundos = (it.cartaoSegundos + 1).coerceAtMost(60)) } },
            )
            AjusteNumerico(
                rotulo = "opacidade",
                valor = "${config.cartaoOpacidade}%",
                onMenos = { aoAlternar { it.copy(cartaoOpacidade = (it.cartaoOpacidade - 10).coerceAtLeast(20)) } },
                onMais = { aoAlternar { it.copy(cartaoOpacidade = (it.cartaoOpacidade + 10).coerceAtMost(100)) } },
            )
            Text("posição no topo da tela", fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PosicaoCartao.entries.forEach { posicao ->
                    FilterChip(
                        selected = config.posicaoCartao == posicao,
                        onClick = { aoAlternar { it.copy(posicaoCartao = posicao) } },
                        label = {
                            Text(
                                when (posicao) {
                                    PosicaoCartao.ESQUERDA -> "Esquerda"
                                    PosicaoCartao.CENTRO -> "Centro"
                                    PosicaoCartao.DIREITA -> "Direita"
                                },
                                fontSize = 12.sp,
                            )
                        },
                    )
                }
            }
            Text("tamanho do cartão", fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TamanhoCartao.entries.forEach { tamanho ->
                    FilterChip(
                        selected = config.tamanhoCartao == tamanho,
                        onClick = { aoAlternar { it.copy(tamanhoCartao = tamanho) } },
                        label = { Text(tamanho.rotulo, fontSize = 11.sp, maxLines = 1) },
                    )
                }
            }
            LinhaAcao(
                titulo = "Informações no cartão",
                descricao = "${config.camposCartao.size} de ${CampoCartao.entries.size} linhas ligadas",
                onClick = aoAbrirCampos,
            )
            LinhaSwitch(
                titulo = "Modo tablet / segunda tela",
                descricao = "um cartão dentro da janela de cada app (Uber e 99 ao mesmo tempo) e em telas externas",
                ligado = config.modoTablet,
                onChange = { v -> aoAlternar { it.copy(modoTablet = v) } },
            )
            LinhaSwitch(
                titulo = "Lucro no cartão",
                descricao = "mostra o valor menos o custo de rodar",
                ligado = CampoCartao.LUCRO in config.camposCartao,
                onChange = { ligado ->
                    aoAlternar { cfg ->
                        cfg.copy(
                            camposCartao = if (ligado) cfg.camposCartao + CampoCartao.LUCRO
                            else cfg.camposCartao - CampoCartao.LUCRO,
                        )
                    }
                },
            )
            LinhaSwitch(
                titulo = "Narração por voz",
                descricao = "fala apenas a cor final da oferta",
                ligado = config.vozLigada,
                onChange = { v -> aoAlternar { it.copy(vozLigada = v) } },
            )
        }

        SecaoExpansivel(
            titulo = "Alertas",
            resumo = if (config.alertas.mostrarValorMinimo) "valor mínimo e limites ligados" else "alguns desligados",
        ) {
            LinhaSwitch(
                titulo = "Valor mínimo",
                descricao = "avisa quando a oferta paga menos que isso",
                ligado = config.alertas.mostrarValorMinimo,
                onChange = { v -> aoAlternar { c -> c.copy(alertas = c.alertas.copy(mostrarValorMinimo = v)) } },
            )
            AjusteNumerico(
                rotulo = "valor mínimo (R$)",
                valor = Formato.dinheiro(config.alertas.valorMinimo),
                onMenos = {
                    aoAlternar { c ->
                        c.copy(alertas = c.alertas.copy(valorMinimo = (c.alertas.valorMinimo - 1).coerceAtLeast(0.0)))
                    }
                },
                onMais = {
                    aoAlternar { c ->
                        c.copy(alertas = c.alertas.copy(valorMinimo = c.alertas.valorMinimo + 1))
                    }
                },
            )
            HorizontalDivider()
            AjusteNumerico(
                rotulo = "embarque máximo (km)",
                valor = Formato.numero(config.alertas.embarqueMaxKm),
                onMenos = {
                    aoAlternar { c ->
                        c.copy(alertas = c.alertas.copy(embarqueMaxKm = (c.alertas.embarqueMaxKm - 0.5).coerceAtLeast(0.0)))
                    }
                },
                onMais = {
                    aoAlternar { c ->
                        c.copy(alertas = c.alertas.copy(embarqueMaxKm = c.alertas.embarqueMaxKm + 0.5))
                    }
                },
            )
            AjusteNumerico(
                rotulo = "embarque máximo (min)",
                valor = config.alertas.embarqueMaxMin.toString(),
                onMenos = {
                    aoAlternar { c ->
                        c.copy(alertas = c.alertas.copy(embarqueMaxMin = (c.alertas.embarqueMaxMin - 5).coerceAtLeast(0)))
                    }
                },
                onMais = {
                    aoAlternar { c ->
                        c.copy(alertas = c.alertas.copy(embarqueMaxMin = c.alertas.embarqueMaxMin + 5))
                    }
                },
            )
            AjusteNumerico(
                rotulo = "trecho máximo (km)",
                valor = Formato.numero(config.alertas.trechoMaxKm),
                onMenos = {
                    aoAlternar { c ->
                        c.copy(alertas = c.alertas.copy(trechoMaxKm = (c.alertas.trechoMaxKm - 5).coerceAtLeast(0.0)))
                    }
                },
                onMais = {
                    aoAlternar { c ->
                        c.copy(alertas = c.alertas.copy(trechoMaxKm = c.alertas.trechoMaxKm + 5))
                    }
                },
            )
            AjusteNumerico(
                rotulo = "viagem máxima (min)",
                valor = config.alertas.trechoMaxMin.toString(),
                onMenos = {
                    aoAlternar { c ->
                        c.copy(alertas = c.alertas.copy(trechoMaxMin = (c.alertas.trechoMaxMin - 15).coerceAtLeast(0)))
                    }
                },
                onMais = {
                    aoAlternar { c ->
                        c.copy(alertas = c.alertas.copy(trechoMaxMin = c.alertas.trechoMaxMin + 15))
                    }
                },
            )
            HorizontalDivider()
            LinhaSwitch(
                titulo = "Embarque longe",
                descricao = "avisa quando o embarque passa do limite em km",
                ligado = config.alertas.mostrarEmbarqueLonge,
                onChange = { v -> aoAlternar { c -> c.copy(alertas = c.alertas.copy(mostrarEmbarqueLonge = v)) } },
            )
            LinhaSwitch(
                titulo = "Demora para embarcar",
                descricao = "avisa quando o embarque passa do limite em minutos",
                ligado = config.alertas.mostrarEmbarqueDemorado,
                onChange = { v -> aoAlternar { c -> c.copy(alertas = c.alertas.copy(mostrarEmbarqueDemorado = v)) } },
            )
            LinhaSwitch(
                titulo = "Trecho longo",
                descricao = "avisa quando a corrida passa do limite em km",
                ligado = config.alertas.mostrarTrechoLongo,
                onChange = { v -> aoAlternar { c -> c.copy(alertas = c.alertas.copy(mostrarTrechoLongo = v)) } },
            )
            LinhaSwitch(
                titulo = "Viagem demorada",
                descricao = "avisa quando a corrida passa do limite em minutos",
                ligado = config.alertas.mostrarViagemDemorada,
                onChange = { v -> aoAlternar { c -> c.copy(alertas = c.alertas.copy(mostrarViagemDemorada = v)) } },
            )
            LinhaSwitch(
                titulo = "Com paradas",
                descricao = "marca ofertas com paradas no caminho",
                ligado = config.alertas.mostrarParada,
                onChange = { v -> aoAlternar { c -> c.copy(alertas = c.alertas.copy(mostrarParada = v)) } },
            )
            LinhaSwitch(
                titulo = "Passageiro novo",
                descricao = "marca ofertas de passageiro sem histórico",
                ligado = config.alertas.mostrarPassageiroNovo,
                onChange = { v -> aoAlternar { c -> c.copy(alertas = c.alertas.copy(mostrarPassageiroNovo = v)) } },
            )
        }

        SecaoExpansivel(
            titulo = "Dados",
            resumo = if (config.salvarPrint) "print das ofertas ligado" else "print das ofertas desligado",
        ) {
            LinhaSwitch(
                titulo = "Salvar print das ofertas",
                descricao = "guarda a imagem no histórico; você baixa a que quiser",
                ligado = config.salvarPrint,
                onChange = { v -> aoAlternar { it.copy(salvarPrint = v) } },
            )
        }

        SecaoExpansivel(
            titulo = "Avançado",
            resumo = "permissões, testes e estatísticas",
        ) {
            LinhaAcao(
                titulo = "Opções de desenvolvedor",
                descricao = "permissões, modo rápido, testes, limpar histórico e estatísticas",
                onClick = aoAbrirDev,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ParLimiares(
    rotulo: String,
    ruim: Double,
    bom: Double,
    passo: Double,
    formatar: (Double) -> String,
    aoMudarRuim: (Double) -> Unit,
    aoMudarBom: (Double) -> Unit,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(rotulo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ColunaStepper(
                titulo = "ruim",
                valor = formatar(ruim),
                onMenos = { aoMudarRuim((ruim - passo).coerceAtLeast(0.0)) },
                onMais = { aoMudarRuim(ruim + passo) },
                modifier = Modifier.weight(1f),
            )
            ColunaStepper(
                titulo = "bom",
                valor = formatar(bom),
                onMenos = { aoMudarBom((bom - passo).coerceAtLeast(0.0)) },
                onMais = { aoMudarBom(bom + passo) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ColunaStepper(
    titulo: String,
    valor: String,
    onMenos: () -> Unit,
    onMais: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(titulo, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "−",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onMenos)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text(
                valor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text(
                "+",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onMais)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun LinhaResumo(rotulo: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(rotulo, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(valor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
