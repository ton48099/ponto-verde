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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pontoverde.app.PontoVerdeApp
import com.pontoverde.app.core.Custos
import com.pontoverde.app.core.Formato
import com.pontoverde.app.data.CampoCartao

/** Custos do veículo: transformam faturamento em lucro. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaCustos(aoVoltar: () -> Unit) {
    val app = PontoVerdeApp.instancia
    val config by app.prefs.estado.collectAsState()
    val c = config.custos

    fun alterar(bloco: (Custos) -> Custos) {
        app.prefs.atualizar { it.copy(custos = bloco(it.custos)) }
    }

    BackHandler { aoVoltar() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custos do veículo") },
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
                "Com o custo por km, o cartão mostra o lucro real da oferta — o valor menos o que você gasta para rodar trecho + embarque.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = {
                    alterar {
                        it.copy(
                            precoLitro = 4.19,
                            kmPorLitro = 9.0,
                            manutencaoPorKm = 0.25,
                            metaDiaria = 300.0,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            ) {
                Text("Usar valores típicos (etanol 4,19/L · 9 km/L · 0,25/km)")
            }

            Text(
                "O app já vem com esses valores. Ajuste o preço e o consumo do seu carro; " +
                    "a manutenção de 0,25/km é uma reserva média para óleo, pneu e freio.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            SecaoExpansivel(
                titulo = "Combustível",
                resumo = Formato.dinheiro(c.precoLitro) + "/L · " +
                    Formato.numero(c.kmPorLitro) + " km/L · " +
                    Formato.dinheiro(c.custoCombustivelPorKm) + "/km",
            ) {
                AjusteNumerico(
                    rotulo = "preço do litro",
                    valor = Formato.dinheiro(c.precoLitro),
                    onMenos = { alterar { it.copy(precoLitro = (c.precoLitro - 0.10).coerceAtLeast(0.0)) } },
                    onMais = { alterar { it.copy(precoLitro = c.precoLitro + 0.10) } },
                )
                AjusteNumerico(
                    rotulo = "consumo (km/L)",
                    valor = Formato.numero(c.kmPorLitro),
                    onMenos = { alterar { it.copy(kmPorLitro = (c.kmPorLitro - 1).coerceAtLeast(0.0)) } },
                    onMais = { alterar { it.copy(kmPorLitro = c.kmPorLitro + 1) } },
                )
                LinhaResumo("custo do combustível por km", Formato.dinheiro(c.custoCombustivelPorKm))
            }

            SecaoExpansivel(
                titulo = "Manutenção e outros",
                resumo = Formato.dinheiro(c.manutencaoPorKm) + "/km · meta " +
                    Formato.dinheiro(c.metaDiaria),
            ) {
                AjusteNumerico(
                    rotulo = "reserva por km",
                    valor = Formato.dinheiro(c.manutencaoPorKm),
                    onMenos = { alterar { it.copy(manutencaoPorKm = (c.manutencaoPorKm - 0.05).coerceAtLeast(0.0)) } },
                    onMais = { alterar { it.copy(manutencaoPorKm = c.manutencaoPorKm + 0.05) } },
                )
                HorizontalDivider()
                AjusteNumerico(
                    rotulo = "outros gastos por dia",
                    valor = Formato.dinheiro(c.outrosPorDia),
                    onMenos = { alterar { it.copy(outrosPorDia = (c.outrosPorDia - 5).coerceAtLeast(0.0)) } },
                    onMais = { alterar { it.copy(outrosPorDia = c.outrosPorDia + 5) } },
                )
                AjusteNumerico(
                    rotulo = "meta de lucro por dia",
                    valor = Formato.dinheiro(c.metaDiaria),
                    onMenos = { alterar { it.copy(metaDiaria = (c.metaDiaria - 50).coerceAtLeast(0.0)) } },
                    onMais = { alterar { it.copy(metaDiaria = c.metaDiaria + 50) } },
                )
            }

            Spacer(Modifier.height(14.dp))
            SecaoExpansivel(
                titulo = "Resultado",
                resumo = "custo por km " + Formato.dinheiro(c.custoPorKm),
            ) {
                LinhaResumo("custo por km", Formato.dinheiro(c.custoPorKm))
                LinhaResumo(
                    "status",
                    if (c.configurado) "configurado" else "sem custo configurado",
                )
                Text(
                    "Exemplo: oferta de R$ 14 com 7 km rodados → custo " +
                        Formato.dinheiro(c.custoPorKm * 7) +
                        ", lucro " + Formato.dinheiro(14.0 - c.custoPorKm * 7) + ".",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Quais linhas o cartão mostra. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaCamposCartao(aoVoltar: () -> Unit) {
    val app = PontoVerdeApp.instancia
    val config by app.prefs.estado.collectAsState()

    BackHandler { aoVoltar() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Informações no cartão") },
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
                "O valor da oferta e a cor final aparecem sempre. Escolha o resto.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Secao("Linhas") {
                CampoCartao.entries.forEach { campo ->
                    LinhaSwitch(
                        titulo = campo.rotulo,
                        ligado = campo in config.camposCartao,
                        onChange = { ligado ->
                            app.prefs.atualizar { cfg ->
                                cfg.copy(
                                    camposCartao = if (ligado) cfg.camposCartao + campo
                                    else cfg.camposCartao - campo,
                                )
                            }
                        },
                    )
                }
            }
            if (CampoCartao.LUCRO in config.camposCartao && !config.custos.configurado) {
                Text(
                    "Para o lucro aparecer, configure os custos do veículo em Ajustes → Custos.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
