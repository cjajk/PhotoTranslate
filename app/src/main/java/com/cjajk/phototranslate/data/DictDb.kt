package com.cjajk.phototranslate.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.cjajk.phototranslate.model.LookupResult
import com.cjajk.phototranslate.model.PosMeaning
import java.io.File

/**
 * 离线词典（ECDICT 裁剪版，assets/dict.sqlite）。
 * 首次使用时把资产复制到私有目录，只读 SQLite 查询；未命中返回 null。
 */
object DictDb {

    @Volatile
    private var db: SQLiteDatabase? = null

    private fun ensure(context: Context): SQLiteDatabase {
        db?.let { return it }
        synchronized(this) {
            db?.let { return it }
            val dir = File(context.filesDir, "dict")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "dict.sqlite")
            if (!file.exists()) {
                context.assets.open("dict.sqlite").use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            }
            return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
                .also { db = it }
        }
    }

    /** 本地查词：命中返回 LookupResult（source=ecdict），未命中返回 null。 */
    fun lookup(context: Context, word: String): LookupResult? {
        val w = word.trim().lowercase()
        if (w.isBlank()) return null
        val db = ensure(context)
        return try {
            db.rawQuery(
                "SELECT phonetic,translation,pos,tag,exchange FROM dict WHERE word=?",
                arrayOf(w)
            ).use { c ->
                if (!c.moveToFirst()) return null
                buildResult(
                    w,
                    c.getString(0),
                    c.getString(1),
                    c.getString(2),
                    c.getString(3),
                    c.getString(4)
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun buildResult(
        word: String,
        phonetic: String?,
        translation: String?,
        pos: String?,
        tag: String?,
        exchange: String?
    ): LookupResult {
        val posMeans = parseTranslation(translation ?: "")
        val plain = posMeans.firstOrNull()?.means?.joinToString("；")
            ?: translation.orEmpty().lineSequence().firstOrNull()?.trim().orEmpty()
        return LookupResult(
            word = word,
            plainText = plain,
            phoneticEn = phonetic?.trim()?.takeIf { it.isNotBlank() }
                ?.let { it.removePrefix("/").removeSuffix("/") },
            posMeans = posMeans,
            exchange = parseExchange(exchange ?: ""),
            tags = parseTags(tag ?: ""),
            source = "ecdict"
        )
    }

    private fun parseTranslation(translation: String): List<PosMeaning> {
        val out = mutableListOf<PosMeaning>()
        translation.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("[")) return@forEach
            val m = Regex("^([a-z]+)\\.\\s*(.+)$").find(line)
            if (m != null) {
                val part = m.groupValues[1]
                val means = m.groupValues[2]
                    .split(Regex("[；;]"))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                if (means.isNotEmpty()) out += PosMeaning(part, means)
            } else {
                if (line.length >= 3) out += PosMeaning("", listOf(line))
            }
        }
        return out
    }

    private val exchangeKeys = listOf(
        "s" to "复数",
        "3" to "第三人称单数",
        "p" to "过去式",
        "i" to "现在分词",
        "d" to "过去分词",
        "r" to "比较级",
        "t" to "最高级"
    )

    private fun parseExchange(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        raw.split('/').forEach { seg ->
            val idx = seg.indexOf(':')
            if (idx <= 0) return@forEach
            val k = seg.substring(0, idx)
            val v = seg.substring(idx + 1)
            val label = exchangeKeys.firstOrNull { it.first == k }?.second ?: return@forEach
            if (v.isNotBlank() && v != "0" && v != "1") out[label] = v
        }
        return out
    }

    private val tagLabels = mapOf(
        "zk" to "中考", "gk" to "高考", "cet4" to "四级", "cet6" to "六级",
        "ky" to "考研", "ielts" to "雅思", "toefl" to "托福", "gre" to "GRE"
    )

    private fun parseTags(raw: String): List<String> =
        raw.split(',').mapNotNull { t ->
            val k = t.trim()
            if (k.isEmpty()) null else tagLabels[k] ?: k
        }
}
