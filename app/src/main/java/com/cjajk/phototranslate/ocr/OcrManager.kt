package com.cjajk.phototranslate.ocr

import android.graphics.Bitmap
import com.cjajk.phototranslate.model.WordBox
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OcrResult(
    val boxes: List<WordBox>,
    /** 按阅读顺序排列的行级完整文本，用于整句/全文翻译。 */
    val lines: List<String>
)

object OcrManager {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /** 返回词级词框 + 行级文本；词级缺失时回退到行级。 */
    suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
        val blocks = text.textBlocks

        val boxes = blocks.flatMap { block ->
            block.lines.flatMap { line ->
                val lineBox = line.boundingBox
                line.elements.map { element ->
                    WordBox(
                        text = element.text,
                        box = element.boundingBox ?: lineBox ?: block.boundingBox ?: android.graphics.Rect()
                    )
                }
            }
        }.filter { it.text.isNotBlank() && !it.box.isEmpty }

        val lines = blocks.sortedBy { it.boundingBox?.top ?: 0 }
            .flatMap { block ->
                block.lines.sortedBy { it.boundingBox?.top ?: 0 }
            }
            .map { it.text.trim() }
            .filter { it.isNotBlank() }

        OcrResult(boxes = boxes, lines = lines)
    }

    fun close() {
        recognizer.close()
    }
}
