package com.cjajk.phototranslate.translate

import android.util.LruCache
import com.cjajk.phototranslate.model.LookupResult
import com.cjajk.phototranslate.model.PosMeaning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.random.Random

/** 进程内翻译缓存：同引擎同文本只打一次 API。 */
private val textCache = LruCache<String, String>(300)
private val lookupCache = LruCache<String, LookupResult>(200)

interface Translator {
    /** 返回翻译后的文本；失败时抛异常（携带可展示的 message）。 */
    suspend fun translate(text: String): String

    /** 点词 / 查词：返回带词典结构的完整释义；无词典时降级为普通译文。 */
    suspend fun lookup(text: String): LookupResult =
        LookupResult(word = text, plainText = translate(text), source = "plain")
}

/** 未接入的引擎占位。 */
class UnsupportedTranslator(private val engine: String) : Translator {
    override suspend fun translate(text: String): String =
        throw IllegalStateException("“$engine”引擎未接入，请换用百度翻译或有道智云")
}

/** 方向判断：文本含中文字符 → 译为英文；否则 → 译为中文。 */
fun targetLangFor(text: String): String =
    if (Regex("[\\u4e00-\\u9fff]").containsMatchIn(text)) "en" else "zh"

/** 是否英文单词 / 词组（仅字母、连字符、空格），可尝试词典解析。 */
private fun isWordOrPhrase(text: String): Boolean =
    Regex("^[A-Za-z][A-Za-z'\\- ]*$").matches(text.trim())

class BaiduTranslator(private val appId: String, private val apiKey: String) : Translator {

    override suspend fun translate(text: String): String = withContext(Dispatchers.IO) {
        requireKeys()
        val key = "baidu|$text"
        textCache.get(key)?.let { return@withContext it }
        val result = chunkByLength(text, 4500)
            .map { request(it).getJSONArray("trans_result").getJSONObject(0).getString("dst") }
            .joinToString(" ")
        textCache.put(key, result)
        result
    }

    override suspend fun lookup(text: String): LookupResult = withContext(Dispatchers.IO) {
        requireKeys()
        val key = "baidu|$text"
        lookupCache.get(key)?.let { return@withContext it }
        val json = request(text)
        val plain = json.getJSONArray("trans_result").getJSONObject(0).getString("dst")
        val result = if (isWordOrPhrase(text)) {
            parseDict(text.trim(), plain, json.optJSONObject("dict"))
        } else {
            LookupResult(word = text.trim(), plainText = plain, source = "baidu")
        }
        lookupCache.put(key, result)
        result
    }

    private fun requireKeys() {
        if (appId.isBlank() || apiKey.isBlank()) {
            throw IllegalStateException("请先在设置页填写百度翻译 APP ID 与密钥")
        }
    }

    private fun request(text: String): JSONObject {
        val salt = Random.nextLong().toString()
        val sign = md5(appId + text + salt + apiKey)
        val params = mapOf(
            "q" to text,
            "from" to "auto",
            "to" to targetLangFor(text),
            "appid" to appId,
            "salt" to salt,
            "sign" to sign
        )
        val json = JSONObject(httpPost("https://fanyi-api.baidu.com/api/trans/vip/translate", params))
        if (json.optInt("error_code", 0) != 0) {
            throw IllegalStateException("百度翻译错误：" + json.optString("error_msg", json.optString("error_code")))
        }
        return json
    }

    private suspend fun parseDict(word: String, plain: String, dict: JSONObject?): LookupResult {
        if (dict == null || dict.length() == 0) {
            return LookupResult(word = word, plainText = plain, source = "baidu")
        }
        val sm = dict.optJSONObject("simple_means")
        val symbols = sm?.optJSONObject("symbols")
        val phEn = symbols?.optString("ph_en")?.takeIf { it.isNotBlank() }
        val phAm = symbols?.optString("ph_am")?.takeIf { it.isNotBlank() }
        val posMeans = symbols?.let { parsePos(it) } ?: emptyList()
        val exchange = sm?.optJSONObject("exchange")?.let { parseExchange(it) } ?: emptyMap()
        val tags = sm?.optJSONObject("tags")?.let { parseTags(it) } ?: emptyList()
        val baiduEdict = dict.optJSONObject("word_result")
            ?.optJSONObject("edict")?.let { parseEdict(it) } ?: emptyList()
        val freeDict = FreeDictionaryApi.lookup(word)
        val english = (baiduEdict + freeDict).distinct().take(10)
        return LookupResult(
            word = word,
            plainText = plain,
            phoneticEn = phEn,
            phoneticAm = phAm,
            posMeans = posMeans,
            exchange = exchange,
            tags = tags,
            englishDefinitions = english,
            source = "baidu"
        )
    }

    private fun parsePos(symbols: JSONObject): List<PosMeaning> {
        val parts = symbols.opt("parts") ?: return emptyList()
        return when (parts) {
            is JSONObject -> listOfNotNull(posFrom(parts))
            is JSONArray -> (0 until parts.length()).mapNotNull { posFrom(parts.optJSONObject(it)) }
            else -> emptyList()
        }
    }

    private fun posFrom(o: JSONObject?): PosMeaning? {
        if (o == null) return null
        val part = o.optString("part", "").trim()
        val means = mutableListOf<String>()
        o.optJSONArray("means")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optString(i).trim().takeIf { it.isNotBlank() }?.let { means += it }
            }
        }
        if (part.isBlank() && means.isEmpty()) return null
        return PosMeaning(part, means)
    }

    private val exchangeLabels = listOf(
        "word_pl" to "复数",
        "word_third" to "第三人称单数",
        "word_past" to "过去式",
        "word_done" to "过去分词"
    )

    private fun parseExchange(ex: JSONObject): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        exchangeLabels.forEach { (k, label) ->
            val v = ex.opt(k) ?: return@forEach
            val s = when (v) {
                is JSONArray -> (0 until v.length())
                    .mapNotNull { v.optString(it).takeIf { s -> s.isNotBlank() } }
                    .joinToString(", ")
                is String -> v.takeIf { it.isNotBlank() }
                else -> null
            }
            if (!s.isNullOrBlank()) out[label] = s
        }
        return out
    }

    private fun parseTags(tags: JSONObject): List<String> {
        val core = tags.optJSONArray("core") ?: return emptyList()
        return (0 until core.length()).mapNotNull { core.optString(it).takeIf { s -> s.isNotBlank() } }
    }

    private fun parseEdict(edict: JSONObject): List<String> {
        val out = mutableListOf<String>()
        val items = edict.optJSONArray("item") ?: return emptyList()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val pos = item.optJSONArray("pos")?.let { arr -> if (arr.length() > 0) arr.optString(0) else null }
            val groups = item.optJSONArray("tr_group") ?: continue
            for (g in 0 until groups.length()) {
                val tr = groups.optJSONObject(g)?.opt("tr") ?: continue
                val defs = when (tr) {
                    is JSONArray -> (0 until tr.length()).mapNotNull { stringFrom(tr.opt(it)) }
                    else -> listOfNotNull(stringFrom(tr))
                }
                defs.forEach { d ->
                    out += if (!pos.isNullOrBlank()) "$pos. $d" else d
                }
            }
            if (out.size >= 8) break
        }
        return out.distinct()
    }

    private fun stringFrom(v: Any?): String? = when (v) {
        is String -> v.trim().takeIf { it.isNotBlank() }
        is JSONObject -> v.keys().asSequence()
            .mapNotNull { stringFrom(v.opt(it)) }
            .firstOrNull()
        else -> v?.toString()?.trim()?.takeIf { it.isNotBlank() }
    }
}

class YoudaoTranslator(private val appId: String, private val apiKey: String) : Translator {

    override suspend fun translate(text: String): String = withContext(Dispatchers.IO) {
        requireKeys()
        val key = "youdao|$text"
        textCache.get(key)?.let { return@withContext it }
        val result = chunkByLength(text, 1800)
            .map { request(it) }
            .joinToString(" ")
        textCache.put(key, result)
        result
    }

    override suspend fun lookup(text: String): LookupResult = withContext(Dispatchers.IO) {
        requireKeys()
        LookupResult(word = text.trim(), plainText = translate(text), source = "youdao")
    }

    private fun requireKeys() {
        if (appId.isBlank() || apiKey.isBlank()) {
            throw IllegalStateException("请先在设置页填写有道 应用ID 与 应用密钥")
        }
    }

    private fun request(text: String): String {
        val salt = Random.nextLong().toString()
        val curtime = (System.currentTimeMillis() / 1000).toString()
        val input = if (text.length <= 20) text
        else text.substring(0, 10) + text.length + text.substring(text.length - 10)
        val sign = sha256(appId + input + salt + curtime + apiKey)
        val params = mapOf(
            "q" to text,
            "from" to "auto",
            "to" to targetLangFor(text).let { if (it == "zh") "zh-CHS" else "en" },
            "appKey" to appId,
            "salt" to salt,
            "sign" to sign,
            "signType" to "v3",
            "curtime" to curtime
        )
        val json = JSONObject(httpPost("https://openapi.youdao.com/api", params))
        if (json.optString("errorCode") != "0") {
            throw IllegalStateException("有道翻译错误：errorCode=" + json.optString("errorCode"))
        }
        return json.getJSONArray("translation").getString(0)
    }
}

/** Free Dictionary API（dictionaryapi.dev）：免密钥英英释义补充，失败静默降级为空。 */
object FreeDictionaryApi {
    suspend fun lookup(word: String): List<String> = withContext(Dispatchers.IO) {
        if (!Regex("^[A-Za-z][A-Za-z'\\-]*$").matches(word.trim())) return@withContext emptyList()
        runCatching {
            val url = "https://api.dictionaryapi.dev/api/v2/entries/en/" +
                URLEncoder.encode(word.trim().lowercase(), "UTF-8")
            val arr = JSONArray(httpGet(url))
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val entry = arr.optJSONObject(i) ?: continue
                val meanings = entry.optJSONArray("meanings") ?: continue
                for (j in 0 until meanings.length()) {
                    val m = meanings.optJSONObject(j) ?: continue
                    val pos = m.optString("partOfSpeech").trim()
                    val defs = m.optJSONArray("definitions") ?: continue
                    for (k in 0 until defs.length()) {
                        val d = defs.optJSONObject(k)?.optString("definition")
                            ?.trim()?.takeIf { it.isNotBlank() } ?: continue
                        out += if (pos.isNotBlank()) "$pos. $d" else d
                        if (out.size >= 8) break
                    }
                }
            }
            out.distinct()
        }.getOrDefault(emptyList())
    }
}

fun createTranslator(engine: String, appId: String, apiKey: String): Translator = when (engine) {
    "baidu" -> BaiduTranslator(appId, apiKey)
    "youdao" -> YoudaoTranslator(appId, apiKey)
    else -> UnsupportedTranslator(engine)
}

/** 超长文本按行分块，保证单次请求不超接口长度上限。 */
private fun chunkByLength(text: String, max: Int): List<String> {
    if (text.length <= max) return listOf(text)
    val chunks = mutableListOf<String>()
    val sb = StringBuilder()
    text.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty()) return@forEach
        if (sb.isNotEmpty() && sb.length + line.length + 1 > max) {
            chunks += sb.toString()
            sb.clear()
        }
        if (sb.isNotEmpty()) sb.append(' ')
        sb.append(line)
    }
    if (sb.isNotEmpty()) chunks += sb.toString()
    return chunks.ifEmpty { listOf(text) }
}

private fun Map<String, String>.encode(): String =
    entries.joinToString("&") { (k, v) ->
        URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
    }

private fun httpGet(url: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "GET"
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "PhotoTranslate/0.3")
        if (conn.responseCode !in 200..299) {
            throw IllegalStateException("网络错误 HTTP ${conn.responseCode}")
        }
        return BufferedReader(conn.inputStream.reader(Charsets.UTF_8)).use { it.readText() }
    } finally {
        conn.disconnect()
    }
}

/** 表单 POST：长文本不再受 GET URL 长度限制。 */
private fun httpPost(url: String, params: Map<String, String>): String {
    val body = params.encode()
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("User-Agent", "PhotoTranslate/0.3")
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        if (conn.responseCode !in 200..299) {
            throw IllegalStateException("网络错误 HTTP ${conn.responseCode}")
        }
        return BufferedReader(conn.inputStream.reader(Charsets.UTF_8)).use { it.readText() }
    } finally {
        conn.disconnect()
    }
}

private fun md5(s: String): String =
    MessageDigest.getInstance("MD5").digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }

private fun sha256(s: String): String =
    MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }
