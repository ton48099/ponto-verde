package com.pontoverde.app.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/**
 * Prints das ofertas. Ficam em armazenamento interno do app (não aparecem na
 * galeria) e podem ser exportados para Downloads quando o usuário quiser.
 */
object Prints {

    private const val PASTA = "prints"
    private const val LARGURA_MAX = 1080

    fun salvar(context: Context, bitmap: Bitmap): String? = runCatching {
        val dir = File(context.filesDir, PASTA).apply { mkdirs() }
        val arquivo = File(dir, "oferta_${System.currentTimeMillis()}.jpg")

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

        FileOutputStream(arquivo).use { saida ->
            imagem.compress(Bitmap.CompressFormat.JPEG, 85, saida)
        }
        if (imagem !== bitmap) imagem.recycle()
        arquivo.absolutePath
    }.getOrNull()

    fun apagar(caminho: String?) {
        if (caminho.isNullOrBlank()) return
        runCatching { File(caminho).delete() }
    }

    fun limpar(context: Context) {
        runCatching { File(context.filesDir, PASTA).deleteRecursively() }
    }

    /**
     * Copia o print para Download/Ponto Verde. No Android 10+ isso é feito pelo
     * MediaStore, sem pedir permissão de armazenamento.
     */
    fun exportarParaDownloads(context: Context, caminho: String): Boolean = runCatching {
        val origem = File(caminho)
        if (!origem.exists()) return false

        val nome = "ponto-verde-${System.currentTimeMillis()}.jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val valores = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, nome)
                put(MediaStore.Downloads.MIME_TYPE, "image/jpeg")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Ponto Verde")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
                ?: return false
            resolver.openOutputStream(uri)?.use { saida ->
                origem.inputStream().use { entrada -> entrada.copyTo(saida) }
            } ?: return false
            valores.clear()
            valores.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, valores, null, null)
            true
        } else {
            val pasta = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Ponto Verde",
            ).apply { mkdirs() }
            val destino = File(pasta, nome)
            origem.inputStream().use { entrada ->
                FileOutputStream(destino).use { saida -> entrada.copyTo(saida) }
            }
            MediaScannerConnection.scanFile(context, arrayOf(destino.absolutePath), null, null)
            true
        }
    }.getOrDefault(false)
}
