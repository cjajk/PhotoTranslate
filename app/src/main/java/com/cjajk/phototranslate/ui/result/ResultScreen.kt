package com.cjajk.phototranslate.ui.result

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cjajk.phototranslate.data.FavoritesStore
import com.cjajk.phototranslate.data.HistoryStore
import com.cjajk.phototranslate.data.SettingsStore
import com.cjajk.phototranslate.model.FavoriteItem
import com.cjajk.phototranslate.model.LookupResult
import com.cjajk.phototranslate.model.WordBox
import com.cjajk.phototranslate.ocr.OcrManager
import com.cjajk.phototranslate.translate.DictLookup
import com.cjajk.phototranslate.translate.createTranslator
import com.cjajk.phototranslate.ui.common.WordResultCard
import com.cjajk.phototranslate.util.BitmapLoader
import java.io.File
import kotlin.comparisons.minOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

private sealed interface LookupState {
    object Idle : LookupState
    object Loading : LookupState
    data class Done(val result: LookupResult) : LookupState
    data class Error(val message: String) : LookupState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(uri: Uri, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var boxes by remember { mutableStateOf<List<WordBox>>(emptyList()) }
    var resultLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var paragraphs by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    var status by remember { mutableStateOf<OcrStatus>(OcrStatus.Loading) }
    var selected by remember { mutableStateOf<WordBox?>(null) }
    var wordLookup by remember { mutableStateOf<LookupState>(LookupState.Idle) }
    var wholeTranslate by remember { mutableStateOf<TranslateState>(TranslateState.Idle) }
    var wholeTick by remember { mutableIntStateOf(0) }
    var favorited by remember { mutableStateOf(false) }

    // 长按选择模式：滑动多选词框 → 拼句翻译
    var selecting by remember { mutableStateOf(false) }
    var selectedIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var sentenceText by remember { mutableStateOf("") }
    var sentenceTranslate by remember { mutableStateOf<TranslateState>(TranslateState.Idle) }
    var sentenceTick by remember { mutableIntStateOf(0) }
    var showSentence by remember { mutableStateOf(false) }
    var sentenceFavorited by remember { mutableStateOf(false) }

    // 解码切 IO 线程避免主线程卡顿/ANR；无论成败，结束后清理拍照缓存残图
    LaunchedEffect(uri) {
        status = OcrStatus.Loading
        try {
            val bmp = withContext(Dispatchers.IO) { BitmapLoader.loadRotated(context, uri) }
            if (bmp == null) {
                status = OcrStatus.Error("图片读取失败")
                return@LaunchedEffect
            }
            bitmap = bmp
            runCatching { OcrManager.recognize(bmp) }
                .onSuccess { result ->
                    boxes = result.boxes
                    resultLines = result.lines
                    paragraphs = result.paragraphs
                    status = if (result.boxes.isEmpty()) OcrStatus.Empty else OcrStatus.Done
                    // 历史来源的照片已在历史库中，不再重复写入（避免把创建时间改成现在）；
                    // 新照片才压缩保存进历史。
                    if (result.lines.isNotEmpty()) {
                        val historyDir = File(context.filesDir, "history").absolutePath
                        val isHistory = uri.scheme == "file" && uri.path?.startsWith(historyDir) == true
                        if (!isHistory) {
                            HistoryStore.saveImage(context, uri)
                                ?.let { HistoryStore.add(context, it, result.lines) }
                        }
                    }
                }
                .onFailure { e ->
                    status = OcrStatus.Error(e.message ?: "OCR 识别失败，请重试或换更清晰的图片")
                }
        } finally {
            deleteCaptureCache(context, uri)
        }
    }

    // 点词完整释义：本地 ECDICT 优先 → 未命中回退在线引擎（单词/词组走词典，句子降级普通译文）
    LaunchedEffect(selected) {
        wordLookup = LookupState.Idle
        val word = selected ?: return@LaunchedEffect
        wordLookup = LookupState.Loading
        wordLookup = try {
            val settings = SettingsStore.settings(context).first()
            val translator = createTranslator(settings.engineType, settings.appId, settings.apiKey)
            val result = DictLookup.lookup(context, translator, word.text)
            LookupState.Done(result)
        } catch (e: Exception) {
            LookupState.Error(e.message ?: "翻译失败")
        }
    }

    // 整段翻译：按段落拼接（保留段落边界），请求计数器保证失败后可重试
    LaunchedEffect(wholeTick) {
        if (wholeTick == 0) return@LaunchedEffect
        wholeTranslate = TranslateState.Loading
        wholeTranslate = try {
            val text = paragraphs.joinToString("\n") { it.joinToString(" ") }
            if (text.isBlank()) throw IllegalStateException("没有可翻译的文本")
            val settings = SettingsStore.settings(context).first()
            val translated = createTranslator(settings.engineType, settings.appId, settings.apiKey)
                .translate(text)
            TranslateState.Done(translated)
        } catch (e: Exception) {
            TranslateState.Error(e.message ?: "翻译失败")
        }
    }

    // 选中句子翻译：长按选择的词拼成句子，走当前引擎整句翻译
    LaunchedEffect(sentenceTick) {
        if (sentenceTick == 0) return@LaunchedEffect
        sentenceTranslate = TranslateState.Loading
        sentenceTranslate = try {
            val text = sentenceText
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
                                paragraphs = paragraphs,
                                state = wholeTranslate,
                                onRequest = { wholeTick++ },
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
                            selecting = selecting,
                            selectedIndices = selectedIndices,
                            onEnterSelect = { index ->
                                selecting = true
                                selectedIndices = setOf(index)
                            },
                            onDragSelect = { index ->
                                if (selecting) selectedIndices = selectedIndices + index
                            },
                            onTapSelect = { index ->
                                selectedIndices =
                                    if (index in selectedIndices) selectedIndices - index
                                    else selectedIndices + index
                            },
                            onRequestSentence = { text ->
                                sentenceText = text
                                sentenceTranslate = TranslateState.Idle
                                sentenceFavorited = false
                                showSentence = true
                                sentenceTick++
                            },
                            onExitSelect = {
                                selecting = false
                                selectedIndices = emptySet()
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
                    .verticalScroll(rememberScrollState())
            ) {
                when (val t = wordLookup) {
                    LookupState.Idle -> Text(word.text, style = MaterialTheme.typography.headlineSmall)
                    LookupState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("翻译中…", style = MaterialTheme.typography.bodyMedium)
                    }
                    is LookupState.Done -> WordResultCard(t.result)
                    is LookupState.Error -> Column {
                        Text(word.text, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            t.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        scope.launch {
                            FavoritesStore.add(
                                context,
                                FavoriteItem(
                                    text = word.text,
                                    type = if (word.text.contains(" ")) "SENTENCE" else "WORD",
                                    translation = (wordLookup as? LookupState.Done)?.result?.summaryText() ?: "",
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

    // 选中句子翻译结果面板：原文 + 译文 + 收藏整句
    if (showSentence && sentenceText.isNotBlank()) {
        ModalBottomSheet(onDismissRequest = { showSentence = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    "选中的句子",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(sentenceText, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                when (val t = sentenceTranslate) {
                    TranslateState.Idle -> {}
                    TranslateState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("翻译中…", style = MaterialTheme.typography.bodyMedium)
                    }
                    is TranslateState.Done -> Column {
                        Text(
                            t.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    FavoritesStore.add(
                                        context,
                                        FavoriteItem(
                                            text = sentenceText,
                                            type = "SENTENCE",
                                            translation = t.text,
                                            createdAt = System.currentTimeMillis()
                                        )
                                    )
                                    sentenceFavorited = true
                                }
                            },
                            enabled = !sentenceFavorited,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (sentenceFavorited) "已收藏" else "收藏到复习")
                        }
                    }
                    is TranslateState.Error -> Text(
                        t.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun WholeTranslateCard(
    paragraphs: List<List<String>>,
    state: TranslateState,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fullText = paragraphs.joinToString("\n") { it.joinToString(" ") }
    // 默认收起成窄条不挡图，点卡片展开/收起
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier.clickable { expanded = !expanded }
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "整段翻译",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (expanded) "收起 ▴" else "展开 ▾",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    fullText,
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
                    is TranslateState.Done -> Column(
                        Modifier
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            t.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is TranslateState.Error -> Text(
                        t.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun OcrOverlay(
    bitmap: Bitmap,
    boxes: List<WordBox>,
    onWordTap: (WordBox) -> Unit,
    selecting: Boolean,
    selectedIndices: Set<Int>,
    onEnterSelect: (Int) -> Unit,
    onDragSelect: (Int) -> Unit,
    onTapSelect: (Int) -> Unit,
    onRequestSentence: (String) -> Unit,
    onExitSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var userScale by remember { mutableFloatStateOf(1f) }
    var userOffset by remember { mutableStateOf(Offset.Zero) }
    var controlsVisible by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableLongStateOf(0L) }

    BoxWithConstraints(modifier.clipToBounds()) {
        val cw = with(density) { maxWidth.toPx() }
        val ch = with(density) { maxHeight.toPx() }
        val imgW = bitmap.width
        val imgH = bitmap.height
        val fitScale = minOf(cw / imgW, ch / imgH)
        val offX = (cw - imgW * fitScale) / 2f
        val offY = (ch - imgH * fitScale) / 2f

        val showControls = {
            controlsVisible = true
            lastInteraction = System.currentTimeMillis()
        }

        // 画面静止 5 秒后自动隐藏缩放按钮；任一交互都会重置计时
        LaunchedEffect(controlsVisible, lastInteraction) {
            if (controlsVisible) {
                delay(5000)
                controlsVisible = false
            }
        }

        val zoomStep: (Float) -> Unit = { factor ->
            val newScale = (userScale * factor).coerceIn(1f, 6f)
            val center = Offset(cw / 2f, ch / 2f)
            userOffset = center + (userOffset - center) * (newScale / userScale)
            userScale = newScale
            if (userScale <= 1.001f) {
                userOffset = Offset.Zero
            } else {
                userOffset = clampOffset(userOffset, cw, ch, offX, offY, imgW, imgH, fitScale, userScale)
            }
            showControls()
        }

        // 手势采集放在未做渲染变换的外层节点，坐标统一在外层（屏幕相对）坐标系；
        // 渲染变换单独放内层，避免命中坐标被框架二次反变换导致放大后点偏。
        // 缩放围绕双指质心：保持质心在屏幕上不动，倍率越大画面也不会漂移。
        // 选择模式下禁用双指缩放（避免滑选误触发），改为长按进入 + 滑动连续选中。
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(boxes, fitScale, offX, offY, selecting) {
                    coroutineScope {
                        if (!selecting) {
                            launch {
                                detectTransformGestures { centroid, pan, zoom, _ ->
                                    val newScale = (userScale * zoom).coerceIn(1f, 6f)
                                    userOffset = centroid + (userOffset - centroid) * (newScale / userScale) + pan
                                    userScale = newScale
                                    if (userScale <= 1.001f) {
                                        userOffset = Offset.Zero
                                    } else {
                                        userOffset = clampOffset(
                                            userOffset, cw, ch, offX, offY, imgW, imgH, fitScale, userScale
                                        )
                                    }
                                    showControls()
                                }
                            }
                        }
                        launch {
                            detectTapGestures(
                                onTap = { tap ->
                                    val inv = (tap - userOffset) / userScale
                                    if (selecting) {
                                        pickBoxIndex(inv, boxes, fitScale, offX, offY, userScale)?.let(onTapSelect)
                                    } else {
                                        pickBoxIndex(inv, boxes, fitScale, offX, offY, userScale)
                                            ?.let { idx -> onWordTap(boxes[idx]) }
                                    }
                                },
                                onLongPress = { tap ->
                                    if (!selecting) {
                                        val inv = (tap - userOffset) / userScale
                                        pickBoxIndex(inv, boxes, fitScale, offX, offY, userScale)?.let(onEnterSelect)
                                    }
                                }
                            )
                        }
                        if (selecting) {
                            // 滑动多选：按下与拖动路径上命中的词框连续选中
                            val hit: (Offset) -> Unit = { pos ->
                                val inv = (pos - userOffset) / userScale
                                pickBoxIndex(inv, boxes, fitScale, offX, offY, userScale)?.let(onDragSelect)
                            }
                            launch {
                                detectDragGestures(
                                    onDragStart = hit,
                                    onDrag = { change, _ -> hit(change.position) }
                                )
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
                    boxes.forEachIndexed { index, wb ->
                        val left = wb.box.left * fitScale + offX
                        val top = wb.box.top * fitScale + offY
                        val right = wb.box.right * fitScale + offX
                        val bottom = wb.box.bottom * fitScale + offY
                        val isSelected = selecting && index in selectedIndices
                        drawRect(
                            color = if (isSelected) Color(0xFFE53935).copy(alpha = 0.45f)
                            else Color(0xFF1E88E5).copy(alpha = 0.6f),
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top),
                            style = if (isSelected) Stroke(width = 4f) else Stroke(width = 2f)
                        )
                    }
                }
            }
        }

        // 放大态侧边缩放按钮：画面静止 5 秒自动隐藏
        if (userScale > 1.01f && controlsVisible) {
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledIconButton(onClick = { zoomStep(1.4f) }) {
                    Text("＋", style = MaterialTheme.typography.titleLarge)
                }
                FilledIconButton(onClick = { zoomStep(1f / 1.4f) }) {
                    Text("－", style = MaterialTheme.typography.titleLarge)
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

        // 选择模式顶部工具条：已选数量 + 翻译选中 + 取消
        if (selecting) {
            val selectedSentence = selectedIndices
                .sortedWith(compareBy({ boxes[it].box.top }, { boxes[it].box.left }))
                .map { boxes[it].text }
                .joinToString(" ")
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 3.dp
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "已选 ${selectedIndices.size} 个词",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { onRequestSentence(selectedSentence) },
                        enabled = selectedIndices.isNotEmpty()
                    ) {
                        Text("翻译选中")
                    }
                    TextButton(onClick = onExitSelect) {
                        Text("取消")
                    }
                }
            }
        }
    }
}

/** 删除拍照缓存残图：FileProvider 的 content URI 不能用 uri.path 删除，这里用真实 File 路径判断。 */
private fun deleteCaptureCache(context: android.content.Context, uri: Uri) {
    if (uri.scheme != "file") return
    val path = uri.path ?: return
    val cacheDir = context.cacheDir.absolutePath
    if (path.startsWith(cacheDir)) {
        runCatching { File(path).delete() }
    }
}

/** 拖动边界约束：图片边缘不离开可视区域太远，避免拖丢。 */
private fun clampOffset(
    t: Offset,
    cw: Float,
    ch: Float,
    offX: Float,
    offY: Float,
    imgW: Int,
    imgH: Int,
    fitScale: Float,
    s: Float
): Offset {
    val contentLeft = offX * s
    val contentTop = offY * s
    val contentRight = (offX + imgW * fitScale) * s
    val contentBottom = (offY + imgH * fitScale) * s
    val minX = cw - contentRight
    val maxX = -contentLeft
    val minY = ch - contentBottom
    val maxY = -contentTop
    val tx = if (minX >= maxX) (minX + maxX) / 2f else t.x.coerceIn(minX, maxX)
    val ty = if (minY >= maxY) (minY + maxY) / 2f else t.y.coerceIn(minY, maxY)
    return Offset(tx, ty)
}

/**
 * 命中策略：先判断触点落在哪个词框内（外扩少量边距，多个命中选面积最贴合的）；
 * 框外再走最近中心匹配，阈值随放大倍率收紧，避免放大后跨词误选。
 * 返回词框在 boxes 中的 index；未命中返回 null。
 */
private fun pickBoxIndex(
    tap: Offset,
    boxes: List<WordBox>,
    scale: Float,
    offX: Float,
    offY: Float,
    zoom: Float
): Int? {
    val pad = 6f / zoom
    var inside: Int? = null
    var insideArea = Float.MAX_VALUE
    boxes.forEachIndexed { index, wb ->
        val l = wb.box.left * scale + offX
        val t = wb.box.top * scale + offY
        val r = wb.box.right * scale + offX
        val b = wb.box.bottom * scale + offY
        if (tap.x in (l - pad)..(r + pad) && tap.y in (t - pad)..(b + pad)) {
            val area = (r - l) * (b - t)
            if (area < insideArea) {
                insideArea = area
                inside = index
            }
        }
    }
    if (inside != null) return inside

    val threshold = 90f / zoom
    var best: Int? = null
    var bestDist = threshold * threshold
    boxes.forEachIndexed { index, wb ->
        val cx = wb.box.exactCenterX() * scale + offX
        val cy = wb.box.exactCenterY() * scale + offY
        val dx = tap.x - cx
        val dy = tap.y - cy
        val d = dx * dx + dy * dy
        if (d < bestDist) {
            bestDist = d
            best = index
        }
    }
    return best
}
