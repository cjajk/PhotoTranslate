package com.cjajk.phototranslate.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cjajk.phototranslate.data.AppSettings
import com.cjajk.phototranslate.data.SettingsStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by SettingsStore.settings(context).collectAsState(initial = AppSettings())

    var engine by remember { mutableStateOf(settings.engineType) }
    var key by remember { mutableStateOf(settings.apiKey) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        engine = settings.engineType
        key = settings.apiKey
    }

    val engines = listOf(
        "baidu" to "百度翻译",
        "tencent" to "腾讯翻译君",
        "deepl" to "DeepL",
        "openai" to "OpenAI 兼容接口",
        "offline" to "离线模型（ML Kit）"
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("翻译引擎（下一版接入翻译功能）", style = MaterialTheme.typography.titleMedium)
            engines.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = engine == value, onClick = {
                            engine = value
                            saved = false
                        }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = engine == value, onClick = {
                        engine = value
                        saved = false
                    })
                    Text(label)
                }
            }
            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it
                    saved = false
                },
                label = { Text("API Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    scope.launch {
                        SettingsStore.save(context, engine, key.trim())
                        saved = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (saved) "已保存" else "保存")
            }
            Text(
                "Key 仅保存在本机（DataStore），不会上传。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
