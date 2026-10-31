package com.cjajk.phototranslate.ui.result

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cjajk.phototranslate.data.FavoritesStore
import com.cjajk.phototranslate.data.HistoryStore
import com.cjajk.phototranslate.data.SettingsStore
import com.cjajk.phototranslate.model.FavoriteItem
import com.cjajk.phototranslate.model.WordBox
import com.cjajk.phototranslate.ocr.OcrManager
import com.cjajk.phototranslate.translate.createTranslator
import com.cjajk.phototranslate.util.BitmapLoader
import java.io.File
import kotlin.comparisons.minOf
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private sealed interface OcrStatus {
    object Loading : OcrStatus
    object Done : OcrStatus
    object Empty : OcrStatus
    data class Error(val message: String) : OcrStatus
}

private sealed interface TranslateState {
    object Idle : TranslateState
    object Loading : TranslateState
    data class Done(val text: String) : TranslateState
    data class Error(val message: String) : TranslateState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(uri: Uri, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var boxes by remember { mutableStateOf<List<WordBox>>(emptyList()) }
    var resultLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var status by remember { mutableStateOf<OcrStatus>(OcrStatus.Loading) }
    var selected by remember { mutableStateOf<WordBox?>(null) }
    var wordTranslate by remember { mutableStateOf<TranslateState>(TranslateState.Idle) }
    var wholeTranslate by remember { mutableStateOf<TranslateState>(TranslateState.Idle) }
    var wholeRequested by remember { mutableStateOf(false) }
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
            .onSuccess { result ->
                boxes = result.boxes
                resultLines = result.lines
                status = if (result.boxes.isEmpty()) OcrStatus.Empty else OcrStatus.Done
                if (result.lines.isNotEmpty()) {
                    val historyDir = File(context.filesDir, "history").absolutePath
                    val isHistory = uri.scheme == "file" && uri.path?.startsWith(historyDir) == true
                    val imagePath = if (isHistory) uri.path
                    else HistoryStore.saveImage(context, uri)
                    imagePath?.let { HistoryStore.add(context, it, result.lines) }
                }
            }
            .onFailure { e ->
                status = OcrStatus.Error(e.message ?: "OCR 失败（设备可能缺少 Google Play 服务）")
            }
    }

    LaunchedEffect(selected) {
        wordTranslate = TranslateState.Idle
        val word = selected ?: return@LaunchedEffect
        wordTranslate = TranslateState.Loading
        wordTranslate = try {
            val settings = SettingsStore.settings(context).first()
            val text = createTranslator(settings.engineType, settings.appId, settings.apiKey)
                .translate(word.text)
            TranslateState.Done(text)
        } catch (e: Exception) {
            TranslateState.Error(e.message ?: "翻译失败")
        }
    }

    LaunchedEffect(wholeRequested) {
        if (!wholeRequested) return@LaunchedEffect
        wholeTranslate = TranslateState.Loading
        wholeTranslate = try {
            val text = resultLines.joinToString(" ")
            if (text.isBlank()) throw IllegalStateException("没有可翻译的文本")
            val settings = SettingsStore.settings(context).first()
            val translated = createTranslator(settings.engineType, settings.appId, settings.apiKey)
                .translate(text)
            TranslateState.Done(translated)
        } catch (e: Exception) {
            TranslateState.Error(e.message ?: "翻译失败")
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
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        if (resultLines.isNotEmpty()) {
                            WholeTranslateCard(
                                lines = resultLines,
                                state = wholeTranslate,
                                onRequest = { wholeRequested = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        OcrOverlay(
                            bitmap = bmp,
                            boxes = boxes,
                            onWordTap = { word ->
                                selected = word
                                favorited = false
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f)
                        )
                    }
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
                when (val t = wordTranslate) {
                    TranslateState.Idle -> {}
                    TranslateState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("翻译中…", style = MaterialTheme.typography.bodyMedium)
                    }
                    is TranslateState.Done -> Text(
                        t.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    is TranslateState.Error -> Text(
                        t.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        scope.launch {
                            FavoritesStore.add(
                                context,
                                FavoriteItem(
                                    text = word.text,
                                    type = if (word.text.contains(" ")) "SENTENCE" else "WORD",
                                    translation = (wordTranslate as? TranslateState.Done)?.text ?: "",
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
private fun WholeTranslateCard(
    lines: List<String>,
    state: TranslateState,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(
                lines.joinToString(" "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                Text("翻译整段")
            }
            when (val t = state) {
                TranslateState.Idle -> {}
                TranslateState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("翻译中…", style = MaterialTheme.typography.bodyMedium)
                }
                is TranslateState.Done -> Text(
                    t.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                is TranslateState.Error -> Text(
                    t.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
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
    var userScale by remember { mutableFloatStateOf(1f) }
    var userOffset by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier.clipToBounds()) {
        val cw = with(density) { maxWidth.toPx() }
        val ch = with(density) { maxHeight.toPx() }
        val imgW = bitmap.width
        val imgH = bitmap.height
        val fitScale = minOf(cw / imgW, ch / imgH)
        val offX = (cw - imgW * fitScale) / 2f
        val offY = (ch - imgH * fitScale) / 2f

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(boxes, fitScale, offX, offY) {
                    coroutineScope {
                        launch {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val newScale = (userScale * zoom).coerceIn(1f, 6f)
                                userOffset = (userOffset + centroid) * (newScale / userScale) - centroid + pan
                                userScale = newScale
                            }
                        }
                        launch {
                            detectTapGestures { tap ->
                                val inv = (tap - userOffset) / userScale
                                pickBox(inv, boxes, fitScale, offX, offY, userScale)?.let(onWordTap)
                            }
                        }
                    }
                }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = userScale
                        scaleY = userScale
                        translationX = userOffset.x
                        translationY = userOffset.y
                        transformOrigin = TransformOrigin(0f, 0f)
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
                        val left = wb.box.left * fitScale + offX
                        val top = wb.box.top * fitScale + offY
                        val right = wb.box.right * fitScale + offX
                        val bottom = wb.box.bottom * fitScale + offY
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
        if (userScale > 1.01f || userOffset != Offset.Zero) {
            IconButton(
                onClick = {
                    userScale = 1f
                    userOffset = Offset.Zero
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "重置视图")
            }
        }
    }
}

private fun pickBox(
    tap: Offset,
    boxes: List<WordBox>,
    scale: Float,
    offX: Float,
    offY: Float,
    zoom: Float
): WordBox? {
    val pad = 6f / zoom
    var inside: WordBox? = null
    var insideArea = Float.MAX_VALUE
    boxes.forEach { wb ->
        val l = wb.box.left * scale + offX
        val t = wb.box.top * scale + offY
        val r = wb.box.right * scale + offX
        val b = wb.box.bottom * scale + offY
        if (tap.x in (l - pad)..(r + pad) && tap.y in (t - pad)..(b + pad)) {
            val area = (r - l) * (b - t)
            if (area < insideArea) {
                insideArea = area
                inside = wb
            }
        }
    }
    if (inside != null) return inside

    val threshold = 90f / zoom
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
