package com.cjajk.phototranslate.model

data class FavoriteItem(
    val text: String,
    val type: String, // WORD | SENTENCE
    val createdAt: Long
)
