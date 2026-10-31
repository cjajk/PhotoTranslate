package com.cjajk.phototranslate.translate

import android.content.Context
import com.cjajk.phototranslate.data.DictDb
import com.cjajk.phototranslate.model.LookupResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 点词 / 查词统一入口：本地 ECDICT 命中优先 → 未命中回退在线引擎（对百度/有道/ML Kit 统一生效）。
 */
object DictLookup {

    private val wordPattern = Regex("^[A-Za-z][A-Za-z'\\- ]*$")

    suspend fun lookup(context: Context, translator: Translator, text: String): LookupResult {
        val word = cleanWord(text)
        if (word.isBlank()) return LookupResult(word = text.trim(), plainText = "", source = "plain")
        if (wordPattern.matches(word)) {
            withContext(Dispatchers.IO) { DictDb.lookup(context, word) }?.let { return it }
        }
        return translator.lookup(word)
    }
}
