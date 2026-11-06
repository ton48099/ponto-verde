package com.pontoverde.app.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.pontoverde.app.BuildConfig
import com.pontoverde.app.core.Avaliacao
import com.pontoverde.app.core.ParserOferta
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Log de treino do leitor.
 *
 * Para cada leitura (automática ou forçada) guarda, lado a lado:
 *  - a imagem recortada que o OCR enxergou;
 *  - o texto bruto devolvido pelo ML Kit;
 *  - o que o parser local entendeu;
 *  - a avaliação final e a resposta da IA, quando houver.
 *
 * A ideia é calibrar as heurísticas do parser com ofertas reais: quando uma
 * leitura erra, dá para ver exatamente o que a tela mostrava e o que o OCR
 * devolveu. Fica em `files/log_treino`, no armazenamento interno do app, e é
 * exportado em ZIP para Download/Ponto Verde.
 */
object LogTreino {

    const val PASTA = "log_treino"

    /** Vem do Gradle, para o JSON não mentir quando a versão sobe. */
    private val VERSAO: String = BuildConfig.VERSION_NAME
    private const val LARGURA_MAX = 1080

    data class Resumo(val eventos: Int, val bytes: Long) {
        val kilobytes: Long get() = bytes / 1024
    }

    data class Exportado(val uri: Uri, val nome: String)

    fun pasta(context: Context): File = File(context.filesDir, PASTA).apply { mkdirs() }

    fun resumo(context: Context): Resumo {
        val arquivos = runCatching { pasta(context).listFiles()?.toList() ?: emptyList() }
            .getOrDefault(emptyList())
        return Resumo(
            eventos = arquivos.count { it.name.endsWith(".json") },
            bytes = arquivos.sumOf { it.length() },
        )
    }

    /**
     * Grava um evento. Devolve o caminho do JSON ou `null` se algo falhar —
     * log é diagnóstico, nunca pode derrubar a leitura.
     */
    fun registrar(
        context: Context,
        pacote: String,
        app: String,
        texto: String,
        lido: ParserOferta.Lido?,
        avaliacao: Avaliacao?,
        bitmap: Bitmap,
        tela: String,
        fonte: String,
        iaBruta: String? = null,
    ): String? = runCatching {
        val dir = pasta(context)
        val agora = System.currentTimeMillis()
        val sufixo = pacote.substringAfterLast('.').take(16).ifBlank { "app" }
        val base = "oferta_${carimbo(agora)}_$sufixo"

        val imagem = File(dir, "$base.jpg")
        salvarImagem(bitmap, imagem)

        val json = JSONObject().apply {
            put("versao_app", VERSAO)
            put("quando", iso(agora))
            put("epoch_ms", agora)
            put("pacote", pacote)
            put("app", app)
            put("fonte", fonte)
            put("tela", tela)
            put("imagem", imagem.name)
            put("ocr", texto)
            put("lido", lidoJson(lido))
            put("avaliacao", avaliacaoJson(avaliacao))
            if (!iaBruta.isNullOrBlank()) put("ia_bruta", iaBruta)
        }

        val arquivo = File(dir, "$base.json")
        arquivo.writeText(json.toString(2), Charsets.UTF_8)
        arquivo.absolutePath
    }.getOrNull()

    /** Zipa tudo e publica em Download/Ponto Verde. Devolve o URI do ZIP. */
    fun exportarZip(context: Context): Exportado? = runCatching {
        val arquivos = pasta(context).listFiles()?.sortedBy { it.name } ?: return null
        if (arquivos.isEmpty()) return null

        val nome = "ponto-verde-log-${carimbo(System.currentTimeMillis())}.zip"
        val zip = File(context.cacheDir, nome)
        ZipOutputStream(FileOutputStream(zip).buffered()).use { saida ->
            arquivos.forEach { arquivo ->
                if (!arquivo.isFile) return@forEach
                saida.putNextEntry(ZipEntry(arquivo.name))
                arquivo.inputStream().use { entrada -> entrada.copyTo(saida) }
                saida.closeEntry()
            }
        }

        val uri = publicar(context, zip, nome) ?: return null
        runCatching { zip.delete() }
        Exportado(uri, nome)
    }.getOrNull()

    fun limpar(context: Context) {
        runCatching { pasta(context).deleteRecursively() }
    }

    private fun salvarImagem(bitmap: Bitmap, destino: File) {
        val escala = LARGURA_MAX.toFloat() / bitmap.width
        val imagem = if (escala < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                LARGURA_MAX,
                (bitmap.height * escala).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        FileOutputStream(destino).use { saida ->
            imagem.compress(Bitmap.CompressFormat.JPEG, 85, saida)
        }
        if (imagem !== bitmap) imagem.recycle()
    }

    private fun publicar(context: Context, arquivo: File, nome: String): Uri? {
        val valores = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, nome)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Ponto Verde")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores) ?: return null
        resolver.openOutputStream(uri)?.use { saida ->
            arquivo.inputStream().use { entrada -> entrada.copyTo(saida) }
        } ?: return null
        valores.clear()
        valores.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, valores, null, null)
        return uri
    }

    private fun lidoJson(lido: ParserOferta.Lido?): Any {
        if (lido == null) return JSONObject.NULL
        return JSONObject().apply {
            put("valor", lido.valor)
            put("km_trecho", lido.kmTrecho)
            put("min_trecho", lido.minTrecho)
            put("km_embarque", lido.kmEmbarque ?: JSONObject.NULL)
            put("min_embarque", lido.minEmbarque ?: JSONObject.NULL)
            put("nota", lido.nota ?: JSONObject.NULL)
            put("paradas", lido.temParadas)
            put("passageiro_novo", lido.passageiroNovo)
        }
    }

    private fun avaliacaoJson(avaliacao: Avaliacao?): Any {
        if (avaliacao == null) return JSONObject.NULL
        val oferta = avaliacao.oferta
        return JSONObject().apply {
            put("cor_final", avaliacao.corFinal.name)
            put("cor_km", avaliacao.corKm.name)
            put("cor_hora", avaliacao.corHora.name)
            put("cor_nota", avaliacao.corNota?.name ?: JSONObject.NULL)
            put("cor_embarque", avaliacao.corEmbarque?.name ?: JSONObject.NULL)
            put("rs_por_km", oferta.rsPorKm)
            put("rs_por_hora", oferta.rsPorHora)
            put("rs_por_minuto", oferta.rsPorMinuto)
            put("km_total", oferta.kmTotal)
            put("minutos_totais", oferta.minutosTotais)
        }
    }

    private fun carimbo(epoch: Long): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(epoch))

    private fun iso(epoch: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date(epoch))
}
