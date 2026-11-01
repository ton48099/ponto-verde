package com.pontoverde.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.pontoverde.app.core.Cor

val Verde = Color(0xFF1B7F4B)
val Ambar = Color(0xFFB7791F)
val Vermelho = Color(0xFFB3261E)
val Azul = Color(0xFF2F5D8A)
val Tinta = Color(0xFF1B1B1B)
val Papel = Color(0xFFFAFAF7)
val Superficie = Color(0xFFF1F1EC)
val Contorno = Color(0xFFDCDCD4)

/** Acentos do tema escuro: verde neon sobre preto de OLED. */
val VerdeNeon = Color(0xFF3DFF9A)
val AmbarNeon = Color(0xFFFFC857)
val VermelhoNeon = Color(0xFFFF5C5C)

val LocalTemaEscuro = staticCompositionLocalOf { false }

private val esquemaClaro = lightColorScheme(
    primary = Verde,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6ECDD),
    onPrimaryContainer = Color(0xFF0B3B22),
    secondary = Azul,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2EDDD),
    onSecondaryContainer = Color(0xFF1B3A24),
    tertiary = Ambar,
    onTertiary = Color.White,
    background = Papel,
    onBackground = Tinta,
    surface = Papel,
    onSurface = Tinta,
    surfaceVariant = Superficie,
    onSurfaceVariant = Color(0xFF4A4A45),
    surfaceContainer = Superficie,
    surfaceContainerLow = Color(0xFFF5F5F0),
    surfaceContainerHigh = Color(0xFFEBEBE4),
    surfaceContainerHighest = Color(0xFFE4E4DC),
    outline = Contorno,
    outlineVariant = Color(0xFFE7E7DF),
    error = Vermelho,
    onError = Color.White,
)

/**
 * Preto puro no fundo e nos cartões, com acento verde neon. Em OLED o pixel
 * preto fica desligado de verdade; os contornos verdes discretos dão relevo
 * sem acender a tela inteira.
 */
private val esquemaEscuro = darkColorScheme(
    primary = VerdeNeon,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF062415),
    onPrimaryContainer = Color(0xFFB9FFD8),
    secondary = Color(0xFF8BE8B5),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF0A1F14),
    onSecondaryContainer = Color(0xFFB9FFD8),
    tertiary = AmbarNeon,
    onTertiary = Color.Black,
    background = Color.Black,
    onBackground = Color(0xFFE8FFF0),
    surface = Color.Black,
    onSurface = Color(0xFFE8FFF0),
    surfaceVariant = Color(0xFF050805),
    onSurfaceVariant = Color(0xFF9FB8A8),
    surfaceContainer = Color.Black,
    surfaceContainerLow = Color(0xFF030503),
    surfaceContainerHigh = Color(0xFF0B120D),
    surfaceContainerHighest = Color(0xFF111A14),
    outline = Color(0xFF23402F),
    outlineVariant = Color(0xFF16271D),
    error = VermelhoNeon,
    onError = Color.Black,
)

@Composable
fun PontoVerdeTema(escuro: Boolean = false, conteudo: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTemaEscuro provides escuro) {
        MaterialTheme(
            colorScheme = if (escuro) esquemaEscuro else esquemaClaro,
            typography = Typography(),
            content = conteudo,
        )
    }
}

/** Cor de farol que funciona nos dois temas: sólida no claro, neon no escuro. */
@Composable
fun corDe(cor: Cor): Color = when (cor) {
    Cor.VERDE -> if (LocalTemaEscuro.current) VerdeNeon else Verde
    Cor.AMARELO -> if (LocalTemaEscuro.current) AmbarNeon else Ambar
    Cor.VERMELHO -> if (LocalTemaEscuro.current) VermelhoNeon else Vermelho
}

/** Texto sobre um chip colorido: preto no neon, branco no tema claro. */
@Composable
fun textoDoChip(): Color = if (LocalTemaEscuro.current) Color.Black else Color.White
