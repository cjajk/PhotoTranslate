package com.cjajk.phototranslate.translate

import com.cjajk.phototranslate.model.LookupResult
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ML Kit 端侧翻译（com.google.mlkit:translate，内置模型版本，不依赖 Google Play 服务）。
 * 语言模型首次按需下载（约 30MB），下载完成后离线可用；零配置，无需任何 key。
 */
class MlKitTranslator : Translator {

    override suspend fun translate(text: String): String = withContext(Dispatchers.IO) {
        val toEn = targetLangFor(text) == "en"
        val source = if (toEn) TranslateLanguage.CHINESE else TranslateLanguage.ENGLISH
        val target = if (toEn) TranslateLanguage.ENGLISH else TranslateLanguage.CHINESE
        val client = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()
        )
        try {
            Tasks.await(client.downloadModelIfNeeded(DownloadConditions.Builder().build()))
            Tasks.await(client.translate(text))
        } finally {
            client.close()
        }
    }

    override suspend fun lookup(text: String): LookupResult {
        val word = cleanWord(text)
        return LookupResult(word = word, plainText = translate(word), source = "mlkit")
    }
}
