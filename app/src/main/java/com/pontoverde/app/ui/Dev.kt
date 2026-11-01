package com.pontoverde.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pontoverde.app.PontoVerdeApp
import com.pontoverde.app.capture.Permissoes
import com.pontoverde.app.core.Cor
import com.pontoverde.app.core.Formato
import com.pontoverde.app.data.Decisao

/**
 * Opções de desenvolvedor: tudo que é de teste, manutenção e diagnóstico.
 * Não é o lugar de uso diário.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDev(aoVoltar: () -> Unit, aoAbrirCalculadora: () -> Unit) {
    val app = PontoVerdeApp.instancia
    val contexto = LocalContext.current
    val config by app.prefs.estado.collectAsState()
    val registros by app.repositorio.registros.collectAsState()
    var confirmarLimpeza by remember { mutableStateOf(false) }

    val acessibilidade = Permissoes.acessibilidadeAtiva(contexto)
    val sobreposicao = Permissoes.sobreposicaoAtiva(contexto)

    BackHandler { aoVoltar() }

    fun alterar(bloco: (com.pontoverde.app.data.Configuracao) -> com.pontoverde.app.data.Configuracao) {
        app.prefs.atualizar(bloco)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Opções de desenvolvedor") },
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
            SecaoExpansivel(
                titulo = "Permissões",
                resumo = (if (acessibilidade) "acessibilidade ok" else "acessibilidade pendente") +
                    " · " + (if (sobreposicao) "sobreposição ok" else "sobreposição pendente"),
            ) {
                ItemPermissao("Acessibilidade", acessibilidade) {
                    Permissoes.abrirAcessibilidade(contexto)
                }
                ItemPermissao("Desenhar sobre outros apps", sobreposicao) {
                    Permissoes.abrirSobreposicao(contexto)
                }
            }

            SecaoExpansivel(
                titulo = "Comportamento",
                resumo = "modo rápido, ler qualquer app e diagnóstico",
            ) {
                LinhaSwitch(
                    titulo = "Modo rápido",
                    descricao = "intervalo mínimo de 0,5 s entre leituras",
                    ligado = config.turbo,
                    onChange = { v -> alterar { it.copy(turbo = v) } },
                )
                LinhaSwitch(
                    titulo = "Ler qualquer app",
                    descricao = "para testar com print na galeria ou no WhatsApp",
                    ligado = config.modoTeste,
                    onChange = { v -> alterar { it.copy(modoTeste = v) } },
                )
                LinhaSwitch(
                    titulo = "Avisar quando não conseguir ler",
                    descricao = "cartão de erro quando o OCR não encontra a oferta",
                    ligado = config.avisarFalha,
                    onChange = { v -> alterar { it.copy(avisarFalha = v) } },
                )
                LinhaSwitch(
                    titulo = "Guardar texto lido",
                    descricao = "útil para calibrar o OCR",
                    ligado = config.salvarTexto,
                    onChange = { v -> alterar { it.copy(salvarTexto = v) } },
                )
                LinhaSwitch(
                    titulo = "Recusa automática (experimental)",
                    descricao = "recusa ofertas vermelhas após 5 s se você não tocar no card",
                    ligado = config.recusaAutomatica,
                    onChange = { v -> alterar { it.copy(recusaAutomatica = v) } },
                )
            }

            SecaoExpansivel(
                titulo = "Ferramentas",
                resumo = "calculadora manual e limpeza de dados",
            ) {
                LinhaAcao(
                    titulo = "Calcular oferta",
                    descricao = "digite os números e veja a cor",
                    onClick = aoAbrirCalculadora,
                )
                LinhaAcao(
                    titulo = "Apagar histórico",
                    descricao = "remove ofertas e prints salvos",
                    onClick = { confirmarLimpeza = true },
                )
            }

            SecaoExpansivel(
                titulo = "Estatísticas",
                resumo = registros.size.toString() + " ofertas lidas",
            ) {
                val total = registros.size
                val verdes = registros.count { it.corFinal == Cor.VERDE }
                val amarelas = registros.count { it.corFinal == Cor.AMARELO }
                val vermelhas = registros.count { it.corFinal == Cor.VERMELHO }
                val aceitas = registros.count { it.decisao == Decisao.ACEITA }
                val recusadas = registros.count { it.decisao == Decisao.RECUSADA }
                val taxa = if (aceitas + recusadas > 0) aceitas * 100 / (aceitas + recusadas) else 0
                val mediaKm = if (total > 0) registros.sumOf { it.rsPorKm } / total else 0.0
                val mediaHora = if (total > 0) registros.sumOf { it.rsPorHora } / total else 0.0
                LinhaResumo("ofertas lidas", total.toString())
                LinhaResumo("verdes / amarelas / vermelhas", "$verdes / $amarelas / $vermelhas")
                LinhaResumo("R$/km médio", Formato.numero(mediaKm))
                LinhaResumo("R$/h médio", Formato.numero(mediaHora))
                LinhaResumo("taxa de aceite", "$taxa%")
            }

            SecaoExpansivel(
                titulo = "Sobre",
                resumo = "Ponto Verde 0.1.0 · uso pessoal",
            ) {
                Text(
                    "Ponto Verde 0.1.0 · uso pessoal · sem internet no APK",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                LinhaResumo("apps monitorados", config.appsLigados.size.toString())
                LinhaResumo("print das ofertas", if (config.salvarPrint) "ligado" else "desligado")
                LinhaResumo("modo tablet", if (config.modoTablet) "ligado" else "desligado")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmarLimpeza) {
        AlertDialog(
            onDismissRequest = { confirmarLimpeza = false },
            title = { Text("Apagar todo o histórico?") },
            text = { Text("As ofertas registradas e os prints salvos serão removidos. As preferências continuam.") },
            confirmButton = {
                TextButton(onClick = {
                    app.repositorio.limpar()
                    confirmarLimpeza = false
                }) { Text("Apagar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarLimpeza = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun ItemPermissao(titulo: String, ativo: Boolean, aoClicar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PontoStatus(ativo)
        Spacer(Modifier.width(10.dp))
        Text(titulo, fontSize = 14.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = aoClicar) { Text(if (ativo) "ok" else "abrir") }
    }
}
