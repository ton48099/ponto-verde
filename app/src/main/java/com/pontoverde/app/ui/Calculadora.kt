package com.pontoverde.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pontoverde.app.PontoVerdeApp
import com.pontoverde.app.capture.CartaoFlutuante
import com.pontoverde.app.capture.Permissoes
import com.pontoverde.app.core.Alertas
import com.pontoverde.app.core.AppOrigem
import com.pontoverde.app.core.Avaliacao
import com.pontoverde.app.core.Avaliador
import com.pontoverde.app.core.Formato
import com.pontoverde.app.core.Oferta
import com.pontoverde.app.core.ParserOferta
import androidx.compose.ui.platform.LocalContext

/** Calculadora de mesa: os mesmos números que o leitor extrai, digitados à mão. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaCalculadora(aoVoltar: () -> Unit) {
    val app = PontoVerdeApp.instancia
    val contexto = LocalContext.current
    val config by app.prefs.estado.collectAsState()

    var origem by remember { mutableStateOf(AppOrigem.UBER) }
    var valor by remember { mutableStateOf("") }
    var km by remember { mutableStateOf("") }
    var minutos by remember { mutableStateOf("") }
    var embarque by remember { mutableStateOf("") }
    var nota by remember { mutableStateOf("") }
    var avaliacao by remember { mutableStateOf<Avaliacao?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }

    BackHandler { aoVoltar() }

    fun calcular() {
        val v = ParserOferta.numero(valor)
        val k = ParserOferta.numero(km)
        val m = ParserOferta.numero(minutos)?.toInt()
        if (v == null || k == null || m == null || k <= 0) {
            aviso = "Preencha valor, distância e duração."
            avaliacao = null
            return
        }
        val oferta = Oferta(
            app = origem,
            valor = v,
            kmTrecho = k,
            minTrecho = m,
            kmEmbarque = ParserOferta.numero(embarque),
            nota = ParserOferta.numero(nota),
            temParadas = false,
            passageiroNovo = false,
        )
        avaliacao = Avaliador.avaliar(oferta, config.limiaresDe(origem))
        aviso = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calcular oferta") },
                navigationIcon = { TextButton(onClick = aoVoltar) { Text("‹ voltar") } },
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
            Text(
                "Use os mesmos números que aparecem na oferta. Serve para conferir a conta e para testar o cartão.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AppOrigem.entries.forEach { item ->
                    FilterChip(
                        selected = origem == item,
                        onClick = { origem = item },
                        label = { Text(item.rotulo, fontSize = 12.sp) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            Campo("valor da oferta (R$)", valor) { valor = it }
            Campo("distância do trecho (km)", km) { km = it }
            Campo("duração do trecho (min)", minutos) { minutos = it }
            Campo("distância do embarque (km, opcional)", embarque) { embarque = it }
            Campo("nota do passageiro (opcional)", nota) { nota = it }

            Spacer(Modifier.height(12.dp))
            Button(onClick = { calcular() }, modifier = Modifier.fillMaxWidth()) {
                Text("Calcular")
            }
            aviso?.let {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }

            avaliacao?.let { a ->
                Spacer(Modifier.height(16.dp))
                CardResultado(a)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (Permissoes.sobreposicaoAtiva(contexto)) {
                                CartaoFlutuante(contexto).mostrar(
                                    a,
                                    Alertas.montar(a, config.alertas),
                                    config,
                                )
                            } else {
                                aviso = "Falta a permissão para desenhar sobre outros apps."
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Ver no cartão") }
                    Button(
                        onClick = {
                            app.repositorio.registrar(a, Alertas.montar(a, config.alertas), null)
                            aviso = "Oferta salva no histórico."
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Salvar") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Campo(rotulo: String, valor: String, aoMudar: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(rotulo, fontSize = 13.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun CardResultado(a: Avaliacao) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = corDe(a.corFinal)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "oferta ${Formato.corTexto(a.corFinal)}",
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 12.sp,
            )
            Text(
                Formato.dinheiro(a.oferta.valor),
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(Formato.numero(a.oferta.rsPorKm) + "/km", a.corKm)
                Chip(Formato.numero(a.oferta.rsPorHora) + "/h", a.corHora)
                a.corEmbarque?.let { Chip(Formato.km(a.oferta.kmEmbarque ?: 0.0), it) }
                a.corNota?.let { Chip(Formato.nota(a.oferta.nota ?: 0.0), it) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                Formato.km(a.oferta.kmTrecho) + " · " + Formato.minutos(a.oferta.minTrecho),
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
            )
            if (a.oferta.kmEmbarque != null) {
                Text(
                    "R${'$'}/km só trecho: " + Formato.numero(a.oferta.rsPorKmTrecho) + " (informativo)",
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}
