package com.pontoverde.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pontoverde.app.PontoVerdeApp
import com.pontoverde.app.core.Cor
import com.pontoverde.app.core.Formato
import com.pontoverde.app.core.ParserOferta
import com.pontoverde.app.data.DiaResumo
import com.pontoverde.app.data.DiaStore
import com.pontoverde.app.data.GastoDia
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val formatoData = DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", Locale("pt", "BR"))
private val formatoDataCurta = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("pt", "BR"))

/** Conteúdo da aba Início: resumo, fechamento e histórico do dia. */
@Composable
fun ConteudoDia() {
    val app = PontoVerdeApp.instancia
    val contexto = LocalContext.current
    val store = remember { DiaStore(contexto) }
    val config by app.prefs.estado.collectAsState()
    val escopo = rememberCoroutineScope()
    val rolagem = rememberScrollState()

    val hoje = LocalDate.now().toString()
    var dataSel by rememberSaveable { mutableStateOf(hoje) }
    var dia by remember { mutableStateOf(store.carregar(dataSel)) }
    var uberTxt by remember { mutableStateOf(textoNumero(dia.uber)) }
    var n99Txt by remember { mutableStateOf(textoNumero(dia.app99)) }
    var outrosTxt by remember { mutableStateOf(textoNumero(dia.outros)) }
    var kmTxt by remember { mutableStateOf(textoNumero(dia.km)) }
    var inicioTxt by remember { mutableStateOf(textoHora(dia.inicioMin)) }
    var fimTxt by remember { mutableStateOf(textoHora(dia.fimMin)) }
    var gastoDesc by remember { mutableStateOf("") }
    var gastoValor by remember { mutableStateOf("") }
    var metaTxt by remember { mutableStateOf(textoNumero(dia.meta)) }
    var salvos by remember { mutableStateOf(store.diasSalvos()) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var confirmarZerar by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    fun carregar(data: String) {
        val d = store.carregar(data)
        dataSel = data
        dia = d
        uberTxt = textoNumero(d.uber)
        n99Txt = textoNumero(d.app99)
        outrosTxt = textoNumero(d.outros)
        kmTxt = textoNumero(d.km)
        inicioTxt = textoHora(d.inicioMin)
        fimTxt = textoHora(d.fimMin)
        gastoDesc = ""
        gastoValor = ""
        metaTxt = textoNumero(d.meta)
        salvos = store.diasSalvos()
        aviso = null
    }

    fun salvar(novo: DiaResumo) {
        dia = novo
        store.salvar(novo)
    }

    fun salvarNoHistorico() {
        store.salvarDia(dataSel)
        salvos = store.diasSalvos()
        aviso = "Dia salvo no histórico. Você pode abrir e editar quando quiser."
        escopo.launch {
            delay(2600)
            aviso = null
        }
    }

    LaunchedEffect(dia.online) {
        while (dia.online) {
            delay(30_000)
            tick++
            fimTxt = textoHora(LocalTime.now().let { it.hour * 60 + it.minute })
        }
    }

    val agoraMin = remember(tick) {
        LocalTime.now().let { it.hour * 60 + it.minute }
    }
    val minutos = dia.minutosEfetivos(agoraMin)
    val custos = config.custos
    val liquido = dia.liquido(custos)
    val progresso = dia.progressoMeta(custos)
    val ehHoje = dataSel == hoje
    val jaSalvo = dataSel in salvos
    val escuro = LocalTemaEscuro.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rolagem)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (ehHoje) "Meu dia" else "Dia salvo",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    runCatching { LocalDate.parse(dataSel).format(formatoData) }
                        .getOrDefault(dataSel)
                        .replaceFirstChar { it.uppercase() },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!ehHoje) {
                TextButton(onClick = { carregar(hoje) }) { Text("hoje") }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { salvarNoHistorico() },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (jaSalvo) "Dia salvo ✓" else "Salvar dia", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = { confirmarZerar = true },
                modifier = Modifier.weight(1f),
            ) {
                Text("Zerar dia")
            }
        }
        aviso?.let {
            Text(
                it,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        val progressoMeta = progresso.percentual.coerceIn(0.0, 100.0)
        val metaBateu = progresso.bateu
        val corCopo = if (escuro) Color(0xFF050805) else Color(0xFFF5F5F0)
        /** Cor do "copinho": vermelho longe da meta, amarelo no meio, verde quando bate. */
        val corVerde = when {
            metaBateu -> corDe(Cor.VERDE)
            progressoMeta <= 33 -> corDe(Cor.VERMELHO)
            progressoMeta <= 66 -> corDe(Cor.AMARELO)
            else -> Color(0xFF4CAF50)
        }
        val alturaCopo = 96.dp
        val alturaVerde = ((progressoMeta / 100.0).coerceIn(0.0, 1.0) * 96f).dp
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (metaBateu) corDe(Cor.VERDE) else corCopo,
            ),
            border = BorderStroke(
                1.dp,
                if (metaBateu) corDe(Cor.VERDE) else MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "saldo líquido",
                            color = if (metaBateu) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                        )
                        Text(
                            Formato.dinheiro(liquido),
                            color = if (metaBateu) Color.Black else corDe(Cor.VERDE),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "bruto " + Formato.dinheiro(dia.bruto) +
                                " · despesas " + Formato.dinheiro(dia.despesas(custos)),
                            color = if (metaBateu) Color.Black.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }
                    if (progresso.meta > 0) {
                        Column(
                            Modifier.width(80.dp).height(alturaCopo),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(alturaCopo)
                                    .background(corCopo, shape = RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                if (progressoMeta > 0) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(alturaVerde)
                                            .background(corVerde, shape = RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            Formato.numero(progressoMeta) + "%",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (metaBateu) corDe(Cor.VERDE) else corVerde,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("ganho por km", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Formato.dinheiro(dia.porKm), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("ganho por hora", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Formato.dinheiro(dia.porHora(agoraMin)), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        SecaoExpansivel(
            titulo = "Meta de lucro",
            resumo = when {
                progresso.meta <= 0 -> "defina a meta do dia"
                progresso.bateu -> "meta batida · ${Formato.dinheiro(liquido)} líquido"
                else -> "${Formato.numero(progresso.percentual)}% · faltam " +
                    Formato.dinheiro(progresso.brutoFaltante)
            },
            inicialAberta = true,
        ) {
            OutlinedTextField(
                value = metaTxt,
                onValueChange = {
                    metaTxt = it
                    salvar(dia.copy(meta = ParserOferta.numero(it) ?: 0.0))
                },
                label = { Text("meta de lucro do dia (R$)") },
                placeholder = { Text("padrão ${Formato.dinheiro(custos.metaDiaria)}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )

            if (progresso.meta <= 0) {
                Text(
                    "Defina uma meta para o app calcular o quanto falta.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                LinearProgressIndicator(
                    progress = { (progresso.percentual / 100.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    Formato.numero(progresso.percentual) + "% da meta · líquido " +
                        Formato.dinheiro(liquido) + " de " + Formato.dinheiro(progresso.meta),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp),
                )

                if (progresso.bateu) {
                    Text(
                        "Meta batida. O excedente já entra no saldo do dia.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                } else {
                    Text(
                        "faltam " + Formato.dinheiro(progresso.brutoFaltante) +
                            " de faturamento (" + Formato.numero(progresso.percentual) + "%)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (progresso.estimavel) {
                        Text(
                            "≈ " + Formato.km(progresso.kmFaltantes) + " a rodar · margem " +
                                Formato.dinheiro(progresso.margemPorKm) + "/km " +
                                "(média " + Formato.dinheiro(dia.porKm) + " − custo " +
                                Formato.dinheiro(custos.custoPorKm) + ")",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Text(
                            "faturamento alvo: " + Formato.dinheiro(progresso.brutoAlvo),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (dia.porKm > 0 && progresso.margemPorKm <= 0) {
                        Text(
                            "Sua média por km não cobre o custo por km. Rodar mais não fecha a meta: " +
                                "é preciso melhorar a oferta média ou reduzir o custo.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    } else {
                        Text(
                            "Registre os km do dia para o app estimar quantos km ainda faltam.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }

        SecaoExpansivel(
            titulo = "Faturamento bruto",
            resumo = "Uber " + Formato.dinheiro(dia.uber) +
                " · 99 " + Formato.dinheiro(dia.app99) +
                " · outros " + Formato.dinheiro(dia.outros),
        ) {
            CampoDinheiro("Uber (X / Comfort / Black)", uberTxt) {
                uberTxt = it
                salvar(dia.copy(uber = ParserOferta.numero(it) ?: 0.0))
            }
            AtalhosDinheiro { valor ->
                val novo = dia.uber + valor
                uberTxt = textoNumero(novo)
                salvar(dia.copy(uber = novo))
            }
            CampoDinheiro("99 (Pop / Comfort)", n99Txt) {
                n99Txt = it
                salvar(dia.copy(app99 = ParserOferta.numero(it) ?: 0.0))
            }
            AtalhosDinheiro { valor ->
                val novo = dia.app99 + valor
                n99Txt = textoNumero(novo)
                salvar(dia.copy(app99 = novo))
            }
            CampoDinheiro("Outros / gorjetas", outrosTxt) {
                outrosTxt = it
                salvar(dia.copy(outros = ParserOferta.numero(it) ?: 0.0))
            }
        }

        SecaoExpansivel(
            titulo = "Gastos do dia",
            resumo = if (dia.gastos.isEmpty()) {
                "nenhum gasto lançado"
            } else {
                Formato.dinheiro(dia.gastos.sumOf { it.valor }) + " em " +
                    dia.gastos.size + (if (dia.gastos.size == 1) " gasto" else " gastos")
            },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = gastoDesc,
                    onValueChange = { gastoDesc = it },
                    label = { Text("ex.: lanche") },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f),
                )
                OutlinedTextField(
                    value = gastoValor,
                    onValueChange = { gastoValor = it },
                    label = { Text("R$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = {
                    val valor = ParserOferta.numero(gastoValor) ?: 0.0
                    if (valor > 0) {
                        val lista = dia.gastos + GastoDia(
                            gastoDesc.ifBlank { "gasto" },
                            valor,
                        )
                        salvar(dia.copy(gastos = lista))
                        gastoDesc = ""
                        gastoValor = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            ) { Text("Adicionar gasto") }

            dia.gastos.forEachIndexed { indice, gasto ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(gasto.descricao, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(Formato.dinheiro(gasto.valor), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = {
                        salvar(dia.copy(gastos = dia.gastos.filterIndexed { i, _ -> i != indice }))
                    }) { Text("remover") }
                }
            }
        }

        SecaoExpansivel(
            titulo = "Quilômetros rodados",
            resumo = Formato.km(dia.km) + " no dia",
        ) {
            OutlinedTextField(
                value = kmTxt,
                onValueChange = {
                    kmTxt = it
                    salvar(dia.copy(km = ParserOferta.numero(it) ?: 0.0))
                },
                label = { Text("km no dia") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10.0, 25.0, 50.0, 100.0).forEach { passo ->
                    OutlinedButton(onClick = {
                        val novo = dia.km + passo
                        kmTxt = textoNumero(novo)
                        salvar(dia.copy(km = novo))
                    }) { Text("+${passo.toInt()}") }
                }
            }
        }

        SecaoExpansivel(
            titulo = "Horas trabalhadas",
            resumo = if (minutos > 0) {
                (dia.inicioMin?.let { textoHora(it) } ?: "--:--") + "–" +
                    (if (dia.online) "agora" else dia.fimMin?.let { textoHora(it) } ?: "--:--") +
                    " · " + Formato.minutos(minutos)
            } else {
                "sem horário lançado"
            },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = inicioTxt,
                    onValueChange = {
                        inicioTxt = it
                        salvar(dia.copy(inicioMin = lerHora(it), online = dia.online || dia.inicioMin == null))
                    },
                    label = { Text("início (HH:mm)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = fimTxt,
                    onValueChange = {
                        fimTxt = it
                        salvar(dia.copy(fimMin = lerHora(it), online = false))
                    },
                    label = { Text("fim (HH:mm)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Estou online ainda", fontSize = 14.sp)
                    Text(
                        "usa o horário de agora como fim",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = dia.online,
                    onCheckedChange = { ligado ->
                        val agora = LocalTime.now().let { it.hour * 60 + it.minute }
                        val inicio = dia.inicioMin ?: agora
                        if (ligado && inicioTxt.isBlank()) {
                            inicioTxt = textoHora(inicio)
                        }
                        if (!ligado) {
                            fimTxt = textoHora(agora)
                        } else {
                            fimTxt = textoHora(agora)
                        }
                        salvar(
                            dia.copy(
                                online = ligado,
                                inicioMin = if (dia.inicioMin == null) inicio else dia.inicioMin,
                                fimMin = agora,
                            ),
                        )
                    },
                )
            }
            if (minutos > 0) {
                Text(
                    "= " + Formato.minutos(minutos) + " · " +
                        Formato.dinheiro(dia.porHora(agoraMin)) + " bruto por hora",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SecaoExpansivel(
            titulo = "Dias salvos",
            resumo = when (salvos.size) {
                0 -> "nenhum dia salvo ainda"
                1 -> "1 dia salvo"
                else -> "${salvos.size} dias salvos"
            },
            inicialAberta = true,
        ) {
            if (salvos.isEmpty()) {
                Text(
                    "Toque em \"Salvar dia\" para guardar o fechamento de hoje aqui. " +
                        "Depois é só abrir o dia para ver ou editar.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            } else {
                salvos.forEach { data ->
                    val d = store.carregar(data)
                    val p = d.progressoMeta(custos)
                    val selecionado = data == dataSel
                    val dataLegivel = runCatching { LocalDate.parse(data) }
                        .map { it.format(formatoDataCurta) + " · " + it.format(formatoData) }
                        .getOrDefault(data)
                    Card(
                        onClick = {
                            carregar(data)
                            escopo.launch { rolagem.animateScrollTo(0) }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (selecionado) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    dataLegivel,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "bruto " + Formato.dinheiro(d.bruto) +
                                        " · líquido " + Formato.dinheiro(d.liquido(custos)) +
                                        " · " + Formato.numero(p.percentual) + "% da meta",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = {
                                    store.removerDiaSalvo(data)
                                    salvos = store.diasSalvos()
                                    if (data == dataSel) aviso = "Dia removido da lista de salvos."
                                },
                            ) { Text("remover") }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmarZerar) {
        AlertDialog(
            onDismissRequest = { confirmarZerar = false },
            title = { Text("Zerar este dia?") },
            text = {
                Text(
                    "Os valores de faturamento, km, horas, gastos e meta de " +
                        runCatching { LocalDate.parse(dataSel).format(formatoDataCurta) }
                            .getOrDefault(dataSel) +
                        " serão apagados. O dia continua na lista de salvos, se estiver lá.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val limpo = DiaResumo(data = dataSel)
                    salvar(limpo)
                    uberTxt = ""
                    n99Txt = ""
                    outrosTxt = ""
                    kmTxt = ""
                    inicioTxt = ""
                    fimTxt = ""
                    gastoDesc = ""
                    gastoValor = ""
                    metaTxt = ""
                    aviso = "Dia zerado."
                    confirmarZerar = false
                }) { Text("Zerar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarZerar = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun CampoDinheiro(rotulo: String, texto: String, aoMudar: (String) -> Unit) {
    OutlinedTextField(
        value = texto,
        onValueChange = aoMudar,
        label = { Text(rotulo, fontSize = 13.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun AtalhosDinheiro(aoAdicionar: (Double) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(10.0, 20.0, 50.0).forEach { valor ->
            OutlinedButton(onClick = { aoAdicionar(valor) }) { Text("+${valor.toInt()}") }
        }
    }
}

private fun textoNumero(valor: Double): String = if (valor <= 0) "" else Formato.numero(valor)

private fun textoHora(minutos: Int?): String {
    if (minutos == null) return ""
    return "%02d:%02d".format(minutos / 60, minutos % 60)
}

private fun lerHora(texto: String): Int? {
    val limpo = texto.trim().replace('.', ':').replace('h', ':').replace(" ", "")
    val partes = limpo.split(":").filter { it.isNotEmpty() }
    if (partes.size != 2) return null
    val h = partes[0].toIntOrNull() ?: return null
    val m = partes[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}
