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

    /**
     * 残词容错查询：OCR 识别不完整（如 traditional → traditiona）时自动找回最接近的完整词。
     * 顺序：前缀匹配 → 去尾 1~2 字符精确查 → 编辑距离 ≤2（同首字母、长度相近，避免全表扫描）。
     * 返回的 LookupResult.note 说明原始残词；彻底未命中返回 null。
     */
    fun fuzzyLookup(context: Context, word: String): LookupResult? {
        val w = word.trim().lowercase()
        if (w.isBlank() || w.length < 3) return null
        val db = ensure(context)
        return try {
            // 1) 前缀匹配：选词频最高的最接近完整词
            db.rawQuery(
                "SELECT word,phonetic,translation,pos,tag,exchange FROM dict WHERE word LIKE ? ORDER BY frq DESC, bnc LIMIT 1",
                arrayOf("$w%")
            ).use { c ->
                if (c.moveToFirst()) {
                    val matched = c.getString(0)
                    return buildResult(matched, c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), w)
                }
            }
            // 2) 去尾 1~2 字符精确查（OCR 常见的截尾）
            for (n in 1..2) {
                if (w.length - n < 3) break
                val stem = w.substring(0, w.length - n)
                db.rawQuery(
                    "SELECT word,phonetic,translation,pos,tag,exchange FROM dict WHERE word=?",
                    arrayOf(stem)
                ).use { c ->
                    if (c.moveToFirst()) {
                        return buildResult(stem, c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), w)
                    }
                }
            }
            // 3) 编辑距离 ≤2：同首字母 + 长度差 ≤2 缩小范围
            var best: String? = null
            var bestDist = 2
            db.rawQuery(
                "SELECT word FROM dict WHERE length(word) BETWEEN ? AND ? AND substr(word,1,1)=?",
                arrayOf((w.length - 2).coerceAtLeast(2).toString(), (w.length + 2).toString(), w.first().toString())
            ).use { c ->
                while (c.moveToNext()) {
                    val cand = c.getString(0)
                    val d = levenshtein(w, cand)
                    if (d < bestDist) {
                        bestDist = d
                        best = cand
                    }
                }
            }
            val matched = best ?: return null
            db.rawQuery(
                "SELECT word,phonetic,translation,pos,tag,exchange FROM dict WHERE word=?",
                arrayOf(matched)
            ).use { c ->
                if (!c.moveToFirst()) return null
                buildResult(matched, c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), w)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 编辑距离（Levenshtein）：最少增删改次数，用于残词与词典词的相似度比较。 */
    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        val dp = IntArray(n + 1) { it }
        for (i in 1..m) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..n) {
                val tmp = dp[j]
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    prev + if (a[i - 1] == b[j - 1]) 0 else 1
                )
                prev = tmp
            }
        }
        return dp[n]
    }

    private fun buildResult(
        word: String,
        phonetic: String?,
        translation: String?,
        pos: String?,
        tag: String?,
        exchange: String?,
        fuzzyFrom: String? = null
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
            source = "ecdict",
            note = fuzzyFrom?.takeIf { it != word }
                ?.let { "未找到 $it，已展示最接近词 $word" } ?: ""
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
