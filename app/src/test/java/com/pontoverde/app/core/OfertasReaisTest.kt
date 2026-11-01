package com.pontoverde.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Ofertas reais enviadas pelo usuário (Uber e 99, outubro/novembro de 2026).
 *
 * O texto abaixo é uma transcrição dos campos visíveis, no formato que o OCR
 * tende a devolver. Serve de oráculo: se o parser mudar e voltar a errar o
 * embarque do Comfort ou a duração "1 h e 55 min", estes testes quebram.
 */
class OfertasReaisTest {

    private val ubr = Limiares(AppOrigem.UBER)
    private val n99 = Limiares(AppOrigem.APP99)

    private data class Caso(
        val nome: String,
        val app: AppOrigem,
        val texto: String,
        val valor: Double,
        val kmTrecho: Double,
        val minTrecho: Int,
        val kmEmbarque: Double?,
        val nota: Double?,
        val paradas: Boolean = false,
        val novo: Boolean = false,
        val pior: Cor,
    )

    private val casos = listOf(
        Caso(
            "#1 UberX 37,03", AppOrigem.UBER,
            """
            R${'$'} 37,03
            Est. de R${'$'}1,25/km
            4,88 (1039)
            4 min (1.6 km)
            2 paradas
            58 minutos (28.1 km)
            Viagem longa (mais de 30 min)
            """.trimIndent(),
            37.03, 28.1, 58, 1.6, 4.88, paradas = true, pior = Cor.VERMELHO,
        ),
        Caso(
            "#2 UberX 10,20", AppOrigem.UBER,
            """
            R${'$'} 10,20
            R${'$'}3,40 Tarifa base dinâmica incl.
            4,88 · 108 corridas
            9 min (2,5 km)
            15 min (5,5 km)
            """.trimIndent(),
            10.20, 5.5, 15, 2.5, 4.88, pior = Cor.VERMELHO,
        ),
        Caso(
            "#3 UberX 22,00", AppOrigem.UBER,
            """
            R${'$'} 22,00
            R${'$'}1,70 por corrida longa incl.
            4,82 · 358 corridas
            7 min (1,6 km)
            31 min (15,7 km)
            """.trimIndent(),
            22.00, 15.7, 31, 1.6, 4.82, pior = Cor.VERMELHO,
        ),
        Caso(
            "#4 UberX 118,89", AppOrigem.UBER,
            """
            R${'$'} 118,89
            Est. de R${'$'}1,63/km
            4,92 (229)
            4 min (1.3 km)
            1 h e 19 min (71.7 km)
            """.trimIndent(),
            118.89, 71.7, 79, 1.3, 4.92, pior = Cor.AMARELO,
        ),
        Caso(
            "#5 99 9,20", AppOrigem.APP99,
            """
            R${'$'}9,20 ⚡1,2x
            R${'$'}1,89/km
            R${'$'}1,42 por embarque distante incl.
            4,84 · +999 corridas
            6 min (1,5 km)
            7 min (3,3 km)
            """.trimIndent(),
            9.20, 3.3, 7, 1.5, 4.84, pior = Cor.AMARELO,
        ),
        Caso(
            "#6 99 7,70", AppOrigem.APP99,
            """
            R${'$'}7,70
            R${'$'}1,98/km
            R${'$'}2,04 por espera longa incl.
            4,98 · 155 corridas
            5 min (1,1 km)
            9 min (2,8 km)
            """.trimIndent(),
            7.70, 2.8, 9, 1.1, 4.98, pior = Cor.AMARELO,
        ),
        Caso(
            "#7 99 23,30", AppOrigem.APP99,
            """
            R${'$'}23,30
            R${'$'}1,95 por dirigir entre áreas
            4,84 · 477 corridas
            7 min (5,1 km)
            35 min (17,9 km)
            """.trimIndent(),
            23.30, 17.9, 35, 5.1, 4.84, pior = Cor.VERMELHO,
        ),
        Caso(
            "#8 99 5,51", AppOrigem.APP99,
            """
            R${'$'}5,51
            4,78 · 822 corridas
            7 min (2,1 km)
            1 parada(s)
            7 min (3,2 km)
            """.trimIndent(),
            5.51, 3.2, 7, 2.1, 4.78, paradas = true, pior = Cor.VERMELHO,
        ),
        Caso(
            "#9 Comfort 7,46 (embarque maior que o trecho)", AppOrigem.UBER,
            """
            R${'$'} 7,46
            Est. de R${'$'}1,17/km
            9 min • 4.4 km
            4,92 (811)
            5 minutos • 2.0 km
            """.trimIndent(),
            7.46, 2.0, 5, 4.4, 4.92, pior = Cor.VERMELHO,
        ),
        Caso(
            "#10 Reservar 204,93", AppOrigem.UBER,
            """
            R${'$'} 204,93
            Est. de R${'$'}1,85/km
            4,96 (104) Verificado
            +R${'$'} 1,75 incluído
            3 min (0.8 km)
            1 h e 55 min (109.9 km)
            Viagem longa (mais de 30 min)
            """.trimIndent(),
            204.93, 109.9, 115, 0.8, 4.96, pior = Cor.VERDE,
        ),
        Caso(
            "#11 Priority 162,72", AppOrigem.UBER,
            """
            R${'$'} 162,72
            Est. de R${'$'}1,45/km
            +R${'$'} 7,35 incluída para retirada
            18 min • 10.2 km
            5,00 (4) Verificado
            1 h e 33 min • 102.3 km
            """.trimIndent(),
            162.72, 102.3, 93, 10.2, 5.00, pior = Cor.AMARELO,
        ),
        Caso(
            "#12 Pop Nova 183,00", AppOrigem.APP99,
            """
            R${'$'}183,00
            4,75 • 50 corridas • Cartão verif.
            (12 min 4,3 km) Rua B, 467, Bom Fim, Guaíba
            (2 h e 13 min 157,3 km) R. Irmão Emílio, 712
            """.trimIndent(),
            183.00, 157.3, 133, 4.3, 4.75, pior = Cor.VERMELHO,
        ),
        Caso(
            "#13 Envios Carro 10,03", AppOrigem.APP99,
            """
            R${'$'} 10,03
            4,99 (103)
            17 min (11.1 km)
            12 minutos (5.9 km)
            """.trimIndent(),
            10.03, 5.9, 12, 11.1, 4.99, pior = Cor.VERMELHO,
        ),
        Caso(
            "#14 UberX 25,20", AppOrigem.UBER,
            """
            R${'$'}25,20
            R${'$'}1,09 por embarque distante incl.
            4,94 · 342 corridas
            8 min (1,8 km)
            37 min (18,1 km)
            """.trimIndent(),
            25.20, 18.1, 37, 1.8, 4.94, pior = Cor.VERMELHO,
        ),
        Caso(
            "#15 99 58,60 (embarque em metros)", AppOrigem.APP99,
            """
            R${'$'}58,60
            R${'$'}2,84 Tarifa base dinâmica incl.
            4,96 · 340 corridas
            4min (812m)
            57min (46,7km)
            """.trimIndent(),
            58.60, 46.7, 57, null, 4.96, pior = Cor.VERMELHO,
        ),
        Caso(
            "#16 99 15,50 (passageiro novo, sem nota)", AppOrigem.APP99,
            """
            R${'$'}15,50
            R${'$'}2,84 por espera longa incl.
            Passageiro novo
            5 min (1,4 km)
            20 min (10,8 km)
            """.trimIndent(),
            15.50, 10.8, 20, 1.4, null, novo = true, pior = Cor.VERMELHO,
        ),
        Caso(
            "#17 99 8,75", AppOrigem.APP99,
            """
            R${'$'} 8,75
            Est. de R${'$'}1,18/km
            7 min • 4.1 km
            4,79 (97)
            7 minutos • 3.3 km
            """.trimIndent(),
            8.75, 3.3, 7, 4.1, 4.79, pior = Cor.VERMELHO,
        ),
    )

    @Test
    fun `todas as ofertas reais sao lidas corretamente`() {
        casos.forEach { caso ->
            val lido = ParserOferta.ler(caso.texto)
            assertNotNull("${caso.nome}: não leu", lido)
            lido!!
            assertEquals("${caso.nome}: valor", caso.valor, lido.valor, 0.001)
            assertEquals("${caso.nome}: km do trecho", caso.kmTrecho, lido.kmTrecho, 0.001)
            assertEquals("${caso.nome}: minutos do trecho", caso.minTrecho, lido.minTrecho)
            assertEquals("${caso.nome}: km de embarque", caso.kmEmbarque, lido.kmEmbarque)
            assertEquals("${caso.nome}: nota", caso.nota, lido.nota)
            assertEquals("${caso.nome}: paradas", caso.paradas, lido.temParadas)
            assertEquals("${caso.nome}: passageiro novo", caso.novo, lido.passageiroNovo)
        }
    }

    @Test
    fun `todas as ofertas reais recebem a cor esperada`() {
        casos.forEach { caso ->
            val lido = ParserOferta.ler(caso.texto)!!
            val oferta = ParserOferta.paraOferta(lido, caso.app)
            val limiares = if (caso.app == AppOrigem.UBER) ubr else n99
            val avaliacao = Avaliador.avaliar(oferta, limiares)
            assertEquals("${caso.nome}: cor final", caso.pior, avaliacao.corFinal)
        }
    }

    @Test
    fun `ordem manda mais que tamanho no embarque`() {
        val comfort = ParserOferta.ler(casos.first { it.nome.startsWith("#9") }.texto)!!
        // antes o parser pegava o maior km como trecho e lia 4,4 km de trecho
        assertEquals(2.0, comfort.kmTrecho, 0.001)
        assertEquals(4.4, comfort.kmEmbarque!!, 0.001)
    }

    @Test
    fun `duracao com hora e minuto`() {
        val longo = ParserOferta.ler(casos.first { it.nome.startsWith("#10") }.texto)!!
        assertEquals(115, longo.minTrecho)
    }

    @Test
    fun `a oferta 2 fica vermelha pelo total rodado`() {
        val lido = ParserOferta.ler(casos.first { it.nome.startsWith("#2") }.texto)!!
        val avaliacao = Avaliador.avaliar(ParserOferta.paraOferta(lido, AppOrigem.UBER), ubr)
        assertEquals(Cor.VERMELHO, avaliacao.corKm)
        assertEquals(null, avaliacao.corEmbarque)
        assertEquals(Cor.VERMELHO, avaliacao.corFinal)
    }

    @Test
    fun `a oferta 3 fica a um triz so no trecho`() {
        val lido = ParserOferta.ler(casos.first { it.nome.startsWith("#3") }.texto)!!
        val oferta = ParserOferta.paraOferta(lido, AppOrigem.UBER)
        assertEquals(1.4012, oferta.rsPorKmTrecho, 0.0001)
        assertEquals(22.0 / 17.3, oferta.rsPorKm, 0.0001)
    }
}
