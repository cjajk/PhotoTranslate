package com.cjajk.phototranslate.model

import android.graphics.Rect

data class WordBox(
    val text: String,
    val box: Rect,
    val confidence: Float? = null
)
