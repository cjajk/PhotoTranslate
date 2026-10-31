package com.cjajk.phototranslate.ui.result

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.cjajk.phototranslate.data.FavoritesStore
import com.cjajk.phototranslate.model.FavoriteItem
import com.cjajk.phototranslate.model.WordBox
import com.cjajk.phototranslate.ocr.OcrManager
import com.cjajk.phototranslate.util.BitmapLoader
import kotlin.math.minOf
import kotlinx.coroutines.launch

private sealed interface OcrStatus {
    object Loading : OcrStatus
    object Done : OcrStatus
    object Empty : OcrStatus
    data class Error(val message: String) : OcrStatus
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(uri: Uri, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var boxes by remember { mutableStateOf<List<WordBox>>(emptyList()) }
    var status by remember { mutableStateOf<OcrStatus>(OcrStatus.Loading) }
    var selected by remember { mutableStateOf<WordBox?>(null) }
    var favorited by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        status = OcrStatus.Loading
        val bmp = BitmapLoader.loadRotated(context, uri)
        if (bmp == null) {
            status = OcrStatus.Error("图片读取失败")
            return@LaunchedEffect
        }
        bitmap = bmp
        runCatching { OcrManager.recognize(bmp) }
            .onSuccess { words ->
                boxes = words
                status = if (words.isEmpty()) OcrStatus.Empty else OcrStatus.Done
            }
            .onFailure { e ->
                status = OcrStatus.Error(e.message ?: "OCR 失败（设备可能缺少 Google Play 服务）")
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("识别结果") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        when (val s = status) {
            OcrStatus.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            is OcrStatus.Error -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    s.message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }

            OcrStatus.Empty -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("未识别到文字，试试更清晰的图片")
            }

            OcrStatus.Done -> {
                val bmp = bitmap
                if (bmp != null) {
                    OcrOverlay(
                        bitmap = bmp,
                        boxes = boxes,
                        onWordTap = { word ->
                            selected = word
                            favorited = false
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    )
                }
            }
        }
    }

    selected?.let { word ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(word.text, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "译文：翻译引擎下一版接入（设置页可先保存 API Key）",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        scope.launch {
                            FavoritesStore.add(
                                context,
                                FavoriteItem(
                                    text = word.text,
                                    type = if (word.text.contains(" ")) "SENTENCE" else "WORD",
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                            favorited = true
                        }
                    },
                    enabled = !favorited,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (favorited) "已收藏" else "收藏到复习")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun OcrOverlay(
    bitmap: Bitmap,
    boxes: List<WordBox>,
    onWordTap: (WordBox) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val cw = with(density) { maxWidth.toPx() }
        val ch = with(density) { maxHeight.toPx() }
        val imgW = bitmap.width
        val imgH = bitmap.height
        val scale = minOf(cw / imgW, ch / imgH)
        val offX = (cw - imgW * scale) / 2f
        val offY = (ch - imgH * scale) / 2f

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(boxes, scale, offX, offY) {
                    detectTapGestures { offset ->
                        nearestBox(offset, boxes, scale, offX, offY)?.let(onWordTap)
                    }
                }
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            Canvas(Modifier.fillMaxSize()) {
                boxes.forEach { wb ->
                    val left = wb.box.left * scale + offX
                    val top = wb.box.top * scale + offY
                    val right = wb.box.right * scale + offX
                    val bottom = wb.box.bottom * scale + offY
                    drawRect(
                        color = Color(0xFF1E88E5).copy(alpha = 0.6f),
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        style = Stroke(width = 2f)
                    )
                }
            }
        }
    }
}

private fun nearestBox(
    tap: Offset,
    boxes: List<WordBox>,
    scale: Float,
    offX: Float,
    offY: Float
): WordBox? {
    val threshold = 130f
    var best: WordBox? = null
    var bestDist = threshold * threshold
    boxes.forEach { wb ->
        val cx = wb.box.exactCenterX() * scale + offX
        val cy = wb.box.exactCenterY() * scale + offY
        val dx = tap.x - cx
        val dy = tap.y - cy
        val d = dx * dx + dy * dy
        if (d < bestDist) {
            bestDist = d
            best = wb
        }
    }
    return best
}
