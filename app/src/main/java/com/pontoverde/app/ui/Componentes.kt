package com.pontoverde.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pontoverde.app.core.Cor
import com.pontoverde.app.core.Formato
import com.pontoverde.app.data.RegistroOferta
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val formatoHora = DateTimeFormatter.ofPattern("dd/MM · HH:mm", Locale("pt", "BR"))

fun dataHora(epoch: Long): String =
    Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(formatoHora)

@Composable
fun Chip(texto: String, cor: Cor, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(corDe(cor))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = texto,
            color = textoDoChip(),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun ChipNeutro(texto: String) {
    val escuro = LocalTemaEscuro.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (escuro) Color(0xFF141A16) else Color(0xFF6B6B62))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            texto,
            color = if (escuro) Color(0xFFB8CCBF) else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun Secao(titulo: String, conteudo: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text(
            text = titulo.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                conteudo()
            }
        }
    }
}

@Composable
fun SecaoExpansivel(
    titulo: String,
    resumo: String? = null,
    inicialAberta: Boolean = false,
    conteudo: @Composable () -> Unit,
) {
    var aberta by remember { mutableStateOf(inicialAberta) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { aberta = !aberta }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(titulo, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        resumo?.let {
                            Text(
                                it,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        if (aberta) "−" else "+",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (aberta) {
                    Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp)) {
                        conteudo()
                    }
                }
            }
        }
    }
}

@Composable
fun LinhaSwitch(
    titulo: String,
    descricao: String? = null,
    ligado: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, fontSize = 15.sp)
            descricao?.let {
                Text(
                    it,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = ligado, onCheckedChange = onChange)
    }
}

@Composable
fun LinhaAcao(
    titulo: String,
    descricao: String? = null,
    icone: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icone?.let {
            Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(titulo, fontSize = 15.sp)
            descricao?.let {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun AjusteNumerico(
    rotulo: String,
    valor: String,
    onMenos: () -> Unit,
    onMais: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rotulo, fontSize = 14.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onMenos) {
            Text("−", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
        }
        Text(valor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onMais) {
            Text("+", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun PontoStatus(ativo: Boolean) {
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (ativo) corDe(Cor.VERDE) else corDe(Cor.VERMELHO)),
    )
}

@Composable
fun CartaoRegistro(registro: RegistroOferta, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(corDe(registro.corFinal)),
                )
                Spacer(Modifier.width(8.dp))
                Text(registro.app.rotulo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    dataHora(registro.criadoEm),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Formato.dinheiro(registro.valor),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    Formato.km(registro.kmTrecho) + " · " + Formato.minutos(registro.minTrecho),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(Formato.numero(registro.rsPorKm), registro.corKm)
                Chip(Formato.numero(registro.rsPorHora), registro.corHora)
                ChipNeutro(Formato.numero(registro.rsPorMinuto))
            }
            if (registro.alertas.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    registro.alertas,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
