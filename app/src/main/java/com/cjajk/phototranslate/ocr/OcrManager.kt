package com.cjajk.phototranslate.ocr

import android.graphics.Bitmap
import com.cjajk.phototranslate.model.WordBox
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object OcrManager {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /** 返回按词级（element）拆分的识别结果；词级缺失时回退到行级。 */
    suspend fun recognize(bitmap: Bitmap): List<WordBox> = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val text = Tasks.await(recognizer.process(image))
        text.textBlocks.flatMap { block ->
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
    }

    fun close() {
        recognizer.close()
    }
}
