package com.cjajk.phototranslate.model

data class PosMeaning(
    val part: String,
    val means: List<String>
)

data class LookupResult(
    val word: String,
    /** 普通译文（词典为空时展示，也是句子的译文）。 */
    val plainText: String = "",
    /** 英式音标。 */
    val phoneticEn: String? = null,
    /** 美式音标。 */
    val phoneticAm: String? = null,
    /** 词性分组：n. / v. / adj. … 各带多条义项。 */
    val posMeans: List<PosMeaning> = emptyList(),
    /** 词形变化：复数 / 第三人称单数 / 过去式 / 过去分词。 */
    val exchange: Map<String, String> = emptyMap(),
    /** 考纲标签：高考 / 考研 / 核心词。 */
    val tags: List<String> = emptyList(),
    /** 英英释义（百度 edict + Free Dictionary API 合并）。 */
    val englishDefinitions: List<String> = emptyList(),
    /** 模糊匹配提示（OCR 残词找回完整词时说明，如"未找到 traditiona，已展示最接近词 traditional"）。 */
    val note: String = "",
    /** 数据来源：baidu / youdao / translate / plain。 */
    val source: String = "plain"
) {
    /** 是否拿到词典结构数据（词性义项或英英释义）。 */
    val hasDict: Boolean
        get() = posMeans.isNotEmpty() || englishDefinitions.isNotEmpty()

    /** 收藏时使用的多行摘要文本。 */
    fun summaryText(): String {
        val sb = StringBuilder()
        val ph = listOfNotNull(
            phoneticEn?.let { "英 /$it/" },
            phoneticAm?.let { "美 /$it/" }
        )
        if (ph.isNotEmpty()) {
            sb.append(ph.joinToString("　")).append('\n')
        }
        posMeans.forEach { pm ->
            sb.append(pm.part)
            if (pm.means.isNotEmpty()) {
                sb.append(' ').append(pm.means.mapIndexed { i, m -> "${i + 1}. $m" }.joinToString("；"))
            }
            sb.append('\n')
        }
        if (exchange.isNotEmpty()) {
            sb.append(exchange.entries.joinToString("　") { "${it.key}：${it.value}" }).append('\n')
        }
        if (tags.isNotEmpty()) {
            sb.append(tags.joinToString("、")).append('\n')
        }
        if (englishDefinitions.isNotEmpty()) {
            sb.append(englishDefinitions.joinToString("；")).append('\n')
        }
        return sb.toString().ifBlank { plainText }
    }
}
