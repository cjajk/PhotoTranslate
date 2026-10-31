package com.cjajk.phototranslate.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cjajk.phototranslate.util.BitmapLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

private val Context.dataStore by preferencesDataStore(name = "history")

data class HistoryItem(
    val imagePath: String,
    val lines: List<String>,
    val createdAt: Long
)

object HistoryStore {

    private val KEY_HISTORY = stringPreferencesKey("history_json")
    private const val MAX_ITEMS = 100

    fun history(context: Context): Flow<List<HistoryItem>> =
        context.dataStore.data.map { prefs -> parse(prefs[KEY_HISTORY] ?: "[]") }

    /**
     * 把图片转正后压缩存入 App 私有目录（长边 ≤ 1600、JPEG 质量 80），
     * 避免原图整份复制导致存储暴涨；失败返回 null。
     */
    suspend fun saveImage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "history").apply { mkdirs() }
            val target = File(dir, "img_${System.currentTimeMillis()}.jpg")
            val bmp = BitmapLoader.loadRotated(context, uri) ?: return@runCatching null
            val scaled = scaleToMax(bmp, 1600)
            FileOutputStream(target).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            if (scaled !== bmp) scaled.recycle()
            bmp.recycle()
            target.absolutePath
        }.getOrNull()
    }

    private fun scaleToMax(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= maxDim && h <= maxDim) return src
        val ratio = maxDim.toFloat() / maxOf(w, h)
        val nw = (w * ratio).toInt().coerceAtLeast(1)
        val nh = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }

    /** 同图去重（按路径），超出上限删除最旧并清理文件。 */
    suspend fun add(context: Context, imagePath: String, lines: List<String>) {
        context.dataStore.edit { prefs ->
            val list = parse(prefs[KEY_HISTORY] ?: "[]").toMutableList()
            list.removeAll { it.imagePath == imagePath }
            list.add(0, HistoryItem(imagePath, lines, System.currentTimeMillis()))
            val trimmed = list.take(MAX_ITEMS)
            val removed = list.drop(MAX_ITEMS)
            prefs[KEY_HISTORY] = toJson(trimmed)
            if (removed.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    removed.forEach { runCatching { File(it.imagePath).delete() } }
                }
            }
        }
    }

    suspend fun clearAll(context: Context) {
        context.dataStore.edit { prefs ->
            val list = parse(prefs[KEY_HISTORY] ?: "[]")
            prefs[KEY_HISTORY] = "[]"
            withContext(Dispatchers.IO) {
                list.forEach { runCatching { File(it.imagePath).delete() } }
            }
        }
    }

    private fun parse(json: String): List<HistoryItem> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val linesArr = o.optJSONArray("lines") ?: JSONArray()
            val lines = (0 until linesArr.length()).map { linesArr.getString(it) }
            HistoryItem(
                imagePath = o.getString("imagePath"),
                lines = lines,
                createdAt = o.getLong("createdAt")
            )
        }
    }.getOrDefault(emptyList())

    private fun toJson(items: List<HistoryItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            val linesArr = JSONArray()
            item.lines.forEach { linesArr.put(it) }
            arr.put(
                JSONObject()
                    .put("imagePath", item.imagePath)
                    .put("lines", linesArr)
                    .put("createdAt", item.createdAt)
            )
        }
        return arr.toString()
    }
}
