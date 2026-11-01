package com.pontoverde.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.pontoverde.app.core.AppOrigem
import com.pontoverde.app.core.Cor

enum class Decisao { NENHUMA, ACEITA, RECUSADA }

/** Uma oferta já avaliada, congelada como foi mostrada. */
data class RegistroOferta(
    val id: Long,
    val criadoEm: Long,
    val app: AppOrigem,
    val valor: Double,
    val kmTrecho: Double,
    val minTrecho: Int,
    val kmEmbarque: Double?,
    val minEmbarque: Int? = null,
    val nota: Double?,
    val rsPorKm: Double,
    val rsPorHora: Double,
    val rsPorMinuto: Double,
    val corKm: Cor,
    val corHora: Cor,
    val corNota: Cor?,
    val corEmbarque: Cor?,
    val corFinal: Cor,
    val alertas: String,
    val decisao: Decisao,
    val textoLido: String?,
    val printPath: String? = null,
) {
    /** Só o trecho, sem o deslocamento — informativo. */
    val rsPorKmTrecho: Double get() = if (kmTrecho > 0) valor / kmTrecho else 0.0

    val kmTotal: Double get() = kmTrecho + (kmEmbarque ?: 0.0)
}

class HistoricoDb(context: Context) : SQLiteOpenHelper(context, "historico.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE ofertas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                criado_em INTEGER NOT NULL,
                app TEXT NOT NULL,
                valor REAL NOT NULL,
                km_trecho REAL NOT NULL,
                min_trecho INTEGER NOT NULL,
                km_embarque REAL,
                min_embarque INTEGER,
                nota REAL,
                rs_por_km REAL NOT NULL,
                rs_por_hora REAL NOT NULL,
                rs_por_minuto REAL NOT NULL,
                cor_km TEXT NOT NULL,
                cor_hora TEXT NOT NULL,
                cor_nota TEXT,
                cor_embarque TEXT,
                cor_final TEXT NOT NULL,
                alertas TEXT NOT NULL,
                decisao TEXT NOT NULL,
                texto_lido TEXT,
                print_path TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_ofertas_data ON ofertas(criado_em DESC)")
        db.execSQL("CREATE INDEX idx_ofertas_app ON ofertas(app)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE ofertas ADD COLUMN print_path TEXT")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE ofertas ADD COLUMN min_embarque INTEGER")
        }
    }

    fun inserir(r: RegistroOferta): Long {
        val cv = ContentValues().apply {
            put("criado_em", r.criadoEm)
            put("app", r.app.name)
            put("valor", r.valor)
            put("km_trecho", r.kmTrecho)
            put("min_trecho", r.minTrecho)
            r.kmEmbarque?.let { put("km_embarque", it) }
            r.minEmbarque?.let { put("min_embarque", it) }
            r.nota?.let { put("nota", it) }
            put("rs_por_km", r.rsPorKm)
            put("rs_por_hora", r.rsPorHora)
            put("rs_por_minuto", r.rsPorMinuto)
            put("cor_km", r.corKm.name)
            put("cor_hora", r.corHora.name)
            r.corNota?.let { put("cor_nota", it.name) }
            r.corEmbarque?.let { put("cor_embarque", it.name) }
            put("cor_final", r.corFinal.name)
            put("alertas", r.alertas)
            put("decisao", r.decisao.name)
            r.textoLido?.let { put("texto_lido", it) }
            r.printPath?.let { put("print_path", it) }
        }
        return writableDatabase.insert("ofertas", null, cv)
    }

    fun listar(): List<RegistroOferta> {
        val lista = mutableListOf<RegistroOferta>()
        readableDatabase.query(
            "ofertas", null, null, null, null, null, "criado_em DESC",
        ).use { c ->
            while (c.moveToNext()) {
                val app = runCatching { AppOrigem.valueOf(c.getString(c.getColumnIndexOrThrow("app"))) }
                    .getOrDefault(AppOrigem.UBER)
                lista += RegistroOferta(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    criadoEm = c.getLong(c.getColumnIndexOrThrow("criado_em")),
                    app = app,
                    valor = c.getDouble(c.getColumnIndexOrThrow("valor")),
                    kmTrecho = c.getDouble(c.getColumnIndexOrThrow("km_trecho")),
                    minTrecho = c.getInt(c.getColumnIndexOrThrow("min_trecho")),
                    kmEmbarque = c.getNullableDouble("km_embarque"),
                    minEmbarque = c.getNullableInt("min_embarque"),
                    nota = c.getNullableDouble("nota"),
                    rsPorKm = c.getDouble(c.getColumnIndexOrThrow("rs_por_km")),
                    rsPorHora = c.getDouble(c.getColumnIndexOrThrow("rs_por_hora")),
                    rsPorMinuto = c.getDouble(c.getColumnIndexOrThrow("rs_por_minuto")),
                    corKm = Cor.valueOf(c.getString(c.getColumnIndexOrThrow("cor_km"))),
                    corHora = Cor.valueOf(c.getString(c.getColumnIndexOrThrow("cor_hora"))),
                    corNota = c.getString(c.getColumnIndexOrThrow("cor_nota"))?.let(Cor::valueOf),
                    corEmbarque = c.getString(c.getColumnIndexOrThrow("cor_embarque"))?.let(Cor::valueOf),
                    corFinal = Cor.valueOf(c.getString(c.getColumnIndexOrThrow("cor_final"))),
                    alertas = c.getString(c.getColumnIndexOrThrow("alertas")),
                    decisao = runCatching {
                        Decisao.valueOf(c.getString(c.getColumnIndexOrThrow("decisao")))
                    }.getOrDefault(Decisao.NENHUMA),
                    textoLido = c.getString(c.getColumnIndexOrThrow("texto_lido")),
                    printPath = c.getString(c.getColumnIndexOrThrow("print_path")),
                )
            }
        }
        return lista
    }

    fun definirDecisao(id: Long, decisao: Decisao) {
        writableDatabase.update(
            "ofertas",
            ContentValues().apply { put("decisao", decisao.name) },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    fun limpar() {
        writableDatabase.delete("ofertas", null, null)
    }

    private fun android.database.Cursor.getNullableDouble(coluna: String): Double? {
        val i = getColumnIndexOrThrow(coluna)
        return if (isNull(i)) null else getDouble(i)
    }

    private fun android.database.Cursor.getNullableInt(coluna: String): Int? {
        val i = getColumnIndexOrThrow(coluna)
        return if (isNull(i)) null else getInt(i)
    }
}
