package com.pontoverde.app.capture

import com.pontoverde.app.core.ParserOferta
import com.pontoverde.app.core.RespostaIa
import com.pontoverde.app.data.Configuracao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Corretor opcional de leitura usando a API da NVIDIA (Nemotron).
 *
 * Só entra em ação quando o usuário liga a chave nas opções de desenvolvedor e
 * configura uma API key. O texto bruto do OCR é enviado, a IA devolve um JSON
 * com os campos da oferta e o app usa esse resultado quando o parser local não
 * conseguiu entender — ou em toda leitura, se "IA em todas as leituras" estiver
 * ligado.
 *
 * Sem chave configurada, nada sai do aparelho.
 */
object CorretorIA {

    const val ENDERECO_PADRAO = "https://integrate.api.nvidia.com/v1/chat/completions"
    const val MODELO_PADRAO = "nvidia/nemotron-3-ultra-550b-a55b"

    private const val CONECTAR_MS = 10_000
    private const val LER_MS = 25_000

    data class Resultado(
        val lido: ParserOferta.Lido?,
        val bruto: String,
        val erro: String?,
    )

    fun disponivel(cfg: Configuracao): Boolean =
        cfg.corretorIA && cfg.nvidiaApiKey.isNotBlank()

    /** Manda o OCR bruto e devolve a oferta corrigida (ou o erro). */
    suspend fun corrigir(cfg: Configuracao, texto: String): Resultado = withContext(Dispatchers.IO) {
        val chave = cfg.nvidiaApiKey.trim()
        if (chave.isBlank()) {
            return@withContext Resultado(null, "", "sem API key configurada")
        }
        if (texto.isBlank()) {
            return@withContext Resultado(null, "", "texto vazio")
        }

        runCatching {
            val endereco = cfg.nvidiaEndpoint.ifBlank { ENDERECO_PADRAO }
            val modelo = cfg.nvidiaModelo.ifBlank { MODELO_PADRAO }

            val corpo = JSONObject().apply {
                put("model", modelo)
                put("temperature", 0)
                put("max_tokens", 300)
                put(
                    "messages",
                    JSONArray().apply {
                        put(JSONObject().put("role", "system").put("content", PROMPT))
                        put(JSONObject().put("role", "user").put("content", texto.take(6000)))
                    },
                )
            }.toString()

            val conexao = (URL(endereco).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONECTAR_MS
                readTimeout = LER_MS
                doOutput = true
                setRequestProperty("Authorization", "Bearer $chave")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            try {
                conexao.outputStream.use { it.write(corpo.toByteArray(Charsets.UTF_8)) }
                val codigo = conexao.responseCode
                val fluxo = if (codigo in 200..299) conexao.inputStream else conexao.errorStream
                val resposta = fluxo?.bufferedReader()?.use { it.readText() }.orEmpty()

                if (codigo !in 200..299) {
                    return@runCatching Resultado(null, resposta, "HTTP $codigo")
                }

                val conteudo = JSONObject(resposta)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()

                Resultado(RespostaIa.ler(conteudo), conteudo, null)
            } finally {
                conexao.disconnect()
            }
        }.getOrElse { t ->
            Resultado(null, "", t.message ?: t.javaClass.simpleName)
        }
    }

    /** Lista os modelos que a conta pode usar (para o botão de teste). */
    suspend fun modelos(cfg: Configuracao): List<String> = withContext(Dispatchers.IO) {
        val chave = cfg.nvidiaApiKey.trim()
        if (chave.isBlank()) return@withContext emptyList()

        val endereco = cfg.nvidiaEndpoint.ifBlank { ENDERECO_PADRAO }
            .substringBefore("/chat/completions")
            .trimEnd('/') + "/models"

        runCatching {
            val conexao = (URL(endereco).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONECTAR_MS
                readTimeout = LER_MS
                setRequestProperty("Authorization", "Bearer $chave")
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (conexao.responseCode !in 200..299) return@runCatching emptyList()
                val resposta = conexao.inputStream.bufferedReader().use { it.readText() }
                val dados = JSONObject(resposta).optJSONArray("data") ?: return@runCatching emptyList()
                (0 until dados.length()).mapNotNull { i ->
                    dados.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }
                }
            } finally {
                conexao.disconnect()
            }
        }.getOrDefault(emptyList())
    }

    /** Texto de exemplo para o botão "Testar conexão". */
    const val AMOSTRA: String =
        "R$ 18,50\n4 min (1,2 km)\n22 min (7,4 km)\n4,9\nPassageiro novo"

    private val PROMPT = """
        Você extrai dados de cartões de oferta de aplicativos de motorista
        (Uber, 99, inDrive, iFood). Recebe o texto bruto de um OCR, que pode ter
        erros de reconhecimento.

        Responda SOMENTE um objeto JSON, sem explicação e sem markdown, no formato:
        {"oferta": true, "valor": 12.34, "km_trecho": 5.2, "min_trecho": 15, "km_embarque": 1.1, "min_embarque": 4, "nota": 4.9, "paradas": false, "passageiro_novo": false}

        Regras:
        - "valor" é o pagamento da corrida em reais. Ignore "Est. de R$/km" e
          valores por quilômetro.
        - O cartão mostra primeiro o deslocamento até o passageiro (embarque) e
          depois a viagem (trecho). Use km_embarque/min_embarque para o primeiro
          par e km_trecho/min_trecho para o segundo.
        - Se houver só um par de duração e distância, ele é o trecho.
        - Use null quando o dado não aparecer.
        - Se o texto não for uma oferta de corrida, responda {"oferta": false}.
    """.trimIndent()
}
