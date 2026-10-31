package com.cjajk.phototranslate.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cjajk.phototranslate.data.FavoritesStore
import com.cjajk.phototranslate.data.SearchHistoryStore
import com.cjajk.phototranslate.data.SettingsStore
import com.cjajk.phototranslate.model.FavoriteItem
import com.cjajk.phototranslate.model.LookupResult
import com.cjajk.phototranslate.translate.createTranslator
import com.cjajk.phototranslate.ui.common.WordResultCard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private sealed interface SearchUi {
    object Idle : SearchUi
    object Loading : SearchUi
    data class Done(val result: LookupResult) : SearchUi
    data class Error(val message: String) : SearchUi
}

/** 手动查词 / 查句子：单词词组走词典，句子走整句翻译；结果可直接收藏。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<SearchUi>(SearchUi.Idle) }
    var favorited by remember { mutableStateOf<String?>(null) }
    val history by SearchHistoryStore.history(context).collectAsState(initial = emptyList())

    fun submit(q: String) {
        val text = q.trim()
        if (text.isEmpty()) return
        scope.launch {
            SearchHistoryStore.add(context, text)
            state = SearchUi.Loading
            state = try {
                val settings = SettingsStore.settings(context).first()
                val translator = createTranslator(settings.engineType, settings.appId, settings.apiKey)
                val result = if (Regex("^[A-Za-z][A-Za-z'\\- ]*$").matches(text)) {
                    translator.lookup(text)
                } else {
                    LookupResult(word = text, plainText = translator.translate(text), source = "translate")
                }
                SearchUi.Done(result)
            } catch (e: Exception) {
                SearchUi.Error(e.message ?: "查询失败")
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("查词") }) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("输入单词、词组或句子") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit(query) })
                )
                FilledIconButton(onClick = { submit(query) }) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索")
                }
            }

            when (val s = state) {
                SearchUi.Idle -> {
                    if (history.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("最近搜索", style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { scope.launch { SearchHistoryStore.clear(context) } }) {
                                Text("清空")
                            }
                        }
                        history.forEach { h ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { query = h; submit(h) }
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "• $h",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                SearchUi.Loading -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                is SearchUi.Done -> {
                    WordResultCard(s.result, Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            scope.launch {
                                FavoritesStore.add(
                                    context,
                                    FavoriteItem(
                                        text = s.result.word,
                                        type = if (s.result.word.contains(" ") ||
                                            Regex("[\\u4e00-\\u9fff]").containsMatchIn(s.result.word)
                                        ) "SENTENCE" else "WORD",
                                        translation = s.result.summaryText(),
                                        createdAt = System.currentTimeMillis()
                                    )
                                )
                                favorited = s.result.word
                            }
                        },
                        enabled = favorited != s.result.word,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (favorited == s.result.word) "已收藏" else "收藏到复习")
                    }
                }

                is SearchUi.Error -> Text(
                    s.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
