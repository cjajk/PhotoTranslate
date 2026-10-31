package com.cjajk.phototranslate.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.random.Random

interface Translator {
    /** 返回翻译后的文本；失败时抛异常（携带可展示的 message）。 */
    suspend fun translate(text: String): String
}

/** 未接入的引擎占位。 */
class UnsupportedTranslator(private val engine: String) : Translator {
    override suspend fun translate(text: String): String =
        throw IllegalStateException("\u201c$engine\u201d引擎未接入，请换用百度翻译或有道智云")
}

/** 方向判断：文本含中文字符 → 译为英文；否则 → 译为中文。 */
fun targetLangFor(text: String): String =
    if (Regex("[\\u4e00-\\u9fff]").containsMatchIn(text)) "en" else "zh"

class BaiduTranslator(private val appId: String, private val apiKey: String) : Translator {
    override suspend fun translate(text: String): String = withContext(Dispatchers.IO) {
        if (appId.isBlank() || apiKey.isBlank()) {
            throw IllegalStateException("请先在设置页填写百度翻译 APP ID 与密钥")
        }
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
        val json = JSONObject(httpGet("https://fanyi-api.baidu.com/api/trans/vip/translate?" + params.encode()))
        if (json.optInt("error_code", 0) != 0) {
            throw IllegalStateException("百度翻译错误：" + json.optString("error_msg", json.optString("error_code")))
        }
        json.getJSONArray("trans_result").getJSONObject(0).getString("dst")
    }
}

class YoudaoTranslator(private val appId: String, private val apiKey: String) : Translator {
    override suspend fun translate(text: String): String = withContext(Dispatchers.IO) {
        if (appId.isBlank() || apiKey.isBlank()) {
            throw IllegalStateException("请先在设置页填写有道 应用ID 与 应用密钥")
        }
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
        val json = JSONObject(httpGet("https://openapi.youdao.com/api?" + params.encode()))
        if (json.optString("errorCode") != "0") {
            throw IllegalStateException("有道翻译错误：errorCode=" + json.optString("errorCode"))
        }
        json.getJSONArray("translation").getString(0)
    }
}

fun createTranslator(engine: String, appId: String, apiKey: String): Translator = when (engine) {
    "baidu" -> BaiduTranslator(appId, apiKey)
    "youdao" -> YoudaoTranslator(appId, apiKey)
    else -> UnsupportedTranslator(engine)
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
        conn.setRequestProperty("User-Agent", "PhotoTranslate/0.2")
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
