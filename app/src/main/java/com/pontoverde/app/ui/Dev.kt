package com.pontoverde.app.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pontoverde.app.PontoVerdeApp
import com.pontoverde.app.capture.CorretorIA
import com.pontoverde.app.capture.EstadoLeitor
import com.pontoverde.app.capture.LogTreino
import com.pontoverde.app.capture.Permissoes
import com.pontoverde.app.core.Cor
import com.pontoverde.app.core.Formato
import com.pontoverde.app.data.Decisao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opções de desenvolvedor: tudo que é de teste, manutenção e diagnóstico.
 * Não é o lugar de uso diário.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaDev(aoVoltar: () -> Unit, aoAbrirCalculadora: () -> Unit) {
    val app = PontoVerdeApp.instancia
    val contexto = LocalContext.current
    val escopo = rememberCoroutineScope()
    val config by app.prefs.estado.collectAsState()
    val registros by app.repositorio.registros.collectAsState()
    var confirmarLimpeza by remember { mutableStateOf(false) }
    var logVersao by remember { mutableStateOf(0) }
    val resumoLog = remember(logVersao, config.registrarLog) { LogTreino.resumo(contexto) }

    var chaveIa by remember { mutableStateOf(config.nvidiaApiKey) }
    var modeloIa by remember { mutableStateOf(config.nvidiaModelo) }
    var enderecoIa by remember { mutableStateOf(config.nvidiaEndpoint) }
    var resultadoTeste by remember { mutableStateOf("") }

    val conectada by EstadoLeitor.ativo.collectAsState()
    val habilitada = Permissoes.acessibilidadeHabilitada(contexto)
    val sobreposicao = Permissoes.sobreposicaoAtiva(contexto)

    BackHandler { aoVoltar() }

    fun alterar(bloco: (com.pontoverde.app.data.Configuracao) -> com.pontoverde.app.data.Configuracao) {
        app.prefs.atualizar(bloco)
    }

    fun exportarLog(compartilhar: Boolean) {
        escopo.launch {
            val zip = withContext(Dispatchers.IO) { LogTreino.exportarZip(contexto) }
            if (zip == null) {
                Toast.makeText(contexto, "Nada para exportar ainda", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (compartilhar) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, zip.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching {
                    contexto.startActivity(Intent.createChooser(intent, "Compartilhar log"))
                }
            } else {
                Toast.makeText(
                    contexto,
                    "Salvo em Download/Ponto Verde/${zip.nome}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
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
                resumo = (if (conectada) "acessibilidade conectada"
                else if (habilitada) "acessibilidade religando"
                else "acessibilidade pendente") +
                    " · " + (if (sobreposicao) "sobreposição ok" else "sobreposição pendente"),
            ) {
                ItemPermissao("Acessibilidade ligada no sistema", habilitada) {
                    Permissoes.abrirAcessibilidade(contexto)
                }
                ItemPermissao("Serviço conectado", conectada) {
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
                titulo = "Log de treino",
                resumo = "${resumoLog.eventos} eventos · ${resumoLog.kilobytes} KB",
            ) {
                LinhaSwitch(
                    titulo = "Registrar cada leitura",
                    descricao = "salva a imagem recortada, o OCR bruto e o que o parser entendeu",
                    ligado = config.registrarLog,
                    onChange = { v ->
                        alterar { it.copy(registrarLog = v) }
                        logVersao++
                    },
                )
                LinhaAcao(
                    titulo = "Exportar ZIP para Downloads",
                    descricao = "junta tudo em Download/Ponto Verde",
                    onClick = { exportarLog(compartilhar = false) },
                )
                LinhaAcao(
                    titulo = "Compartilhar ZIP",
                    descricao = "manda o log por WhatsApp, Drive ou e-mail",
                    onClick = { exportarLog(compartilhar = true) },
                )
                LinhaAcao(
                    titulo = "Apagar log de treino",
                    descricao = "remove imagens e JSONs já gravados",
                    onClick = {
                        LogTreino.limpar(contexto)
                        logVersao++
                        Toast.makeText(contexto, "Log apagado", Toast.LENGTH_SHORT).show()
                    },
                )
                Text(
                    "Cada evento guarda oferta_*.json + oferta_*.jpg. Para puxar no PC:\n" +
                        "adb pull \"/sdcard/Download/Ponto Verde\"",
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                )
            }

            SecaoExpansivel(
                titulo = "Corretor IA (NVIDIA Nemotron)",
                resumo = if (config.corretorIA) {
                    "ligado · ${config.nvidiaModelo.ifBlank { CorretorIA.MODELO_PADRAO }}"
                } else {
                    "desligado · nada sai do aparelho"
                },
            ) {
                LinhaSwitch(
                    titulo = "Usar corretor IA",
                    descricao = "quando o parser local não entende, manda o OCR bruto para a API",
                    ligado = config.corretorIA,
                    onChange = { v -> alterar { it.copy(corretorIA = v) } },
                )
                LinhaSwitch(
                    titulo = "IA em todas as leituras",
                    descricao = "mais lento e gasta mais requisições; usa a IA mesmo quando o parser entendeu",
                    ligado = config.iaSempre,
                    onChange = { v -> alterar { it.copy(iaSempre = v) } },
                )
                OutlinedTextField(
                    value = chaveIa,
                    onValueChange = { chaveIa = it },
                    label = { Text("API key da NVIDIA") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                OutlinedTextField(
                    value = modeloIa,
                    onValueChange = { modeloIa = it },
                    label = { Text("Modelo") },
                    placeholder = { Text(CorretorIA.MODELO_PADRAO) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                OutlinedTextField(
                    value = enderecoIa,
                    onValueChange = { enderecoIa = it },
                    label = { Text("Endpoint") },
                    placeholder = { Text(CorretorIA.ENDERECO_PADRAO) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                LinhaAcao(
                    titulo = "Salvar configuração da IA",
                    descricao = "grava a chave nas preferências do app",
                    onClick = {
                        alterar {
                            it.copy(
                                nvidiaApiKey = chaveIa.trim(),
                                nvidiaModelo = modeloIa.trim(),
                                nvidiaEndpoint = enderecoIa.trim(),
                            )
                        }
                        Toast.makeText(contexto, "Configuração salva", Toast.LENGTH_SHORT).show()
                    },
                )
                LinhaAcao(
                    titulo = "Testar conexão",
                    descricao = "manda uma oferta de exemplo e mostra o que a IA respondeu",
                    onClick = {
                        escopo.launch {
                            resultadoTeste = "testando…"
                            val teste = config.copy(
                                nvidiaApiKey = chaveIa.trim(),
                                nvidiaModelo = modeloIa.trim(),
                                nvidiaEndpoint = enderecoIa.trim(),
                            )
                            val r = CorretorIA.corrigir(teste, CorretorIA.AMOSTRA)
                            resultadoTeste = if (r.lido != null) {
                                "ok · R$ ${Formato.dinheiro(r.lido.valor)} · " +
                                    "${r.lido.kmTrecho} km · ${r.lido.minTrecho} min"
                            } else {
                                "falhou · ${r.erro ?: "resposta sem oferta"}\n" +
                                    r.bruto.take(300)
                            }
                        }
                    },
                )
                LinhaAcao(
                    titulo = "Listar modelos da conta",
                    descricao = "mostra os IDs que a sua API key pode usar",
                    onClick = {
                        escopo.launch {
                            resultadoTeste = "consultando…"
                            val ids = CorretorIA.modelos(
                                config.copy(nvidiaApiKey = chaveIa.trim()),
                            )
                            resultadoTeste = if (ids.isEmpty()) {
                                "nenhum modelo (chave inválida ou sem rede)"
                            } else {
                                ids.take(15).joinToString("\n")
                            }
                        }
                    },
                )
                if (resultadoTeste.isNotBlank()) {
                    Text(
                        resultadoTeste,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Text(
                    "Aviso: com o corretor ligado o app passa a usar internet. A chave fica " +
                        "salva nas preferências do app, em texto simples — use uma chave só " +
                        "para este teste e revogue depois.",
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
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
                resumo = "Ponto Verde 0.2.2 · uso pessoal",
            ) {
                Text(
                    "Ponto Verde 0.2.2 · uso pessoal · internet só com o corretor IA ligado",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                LinhaResumo("apps monitorados", config.appsLigados.size.toString())
                LinhaResumo("print das ofertas", if (config.salvarPrint) "ligado" else "desligado")
                LinhaResumo("log de treino", if (config.registrarLog) "ligado" else "desligado")
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
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PontoStatus(ativo)
        Spacer(Modifier.width(10.dp))
        Text(titulo, fontSize = 14.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = aoClicar) { Text(if (ativo) "ok" else "abrir") }
    }
}
