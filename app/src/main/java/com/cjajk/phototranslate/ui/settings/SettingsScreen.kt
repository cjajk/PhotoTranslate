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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.cjajk.phototranslate.data.AppSettings
import com.cjajk.phototranslate.data.SettingsStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenTutorial: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by SettingsStore.settings(context).collectAsState(initial = AppSettings())

    var engine by remember { mutableStateOf(settings.engineType) }
    var appId by remember { mutableStateOf(settings.appId) }
    var key by remember { mutableStateOf(settings.apiKey) }
    var appIdVisible by remember { mutableStateOf(false) }
    var keyVisible by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        engine = settings.engineType
        appId = settings.appId
        key = settings.apiKey
    }

    val engines = listOf(
        "baidu" to "百度翻译（已接入）",
        "youdao" to "有道智云（已接入）",
        "tencent" to "腾讯翻译君（即将支持）",
        "deepl" to "DeepL（即将支持）",
        "offline" to "离线模型（即将支持）"
    )
    val supported = setOf("baidu", "youdao")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                actions = {
                    IconButton(onClick = onOpenTutorial) {
                        Text(
                            "?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("翻译引擎", style = MaterialTheme.typography.titleMedium)
            engines.forEach { (value, label) ->
                val enabled = value in supported
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = engine == value, enabled = enabled, onClick = {
                            engine = value
                            saved = false
                        }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = engine == value, enabled = enabled, onClick = {
                        engine = value
                        saved = false
                    })
                    Text(label)
                }
            }
            OutlinedTextField(
                value = appId,
                onValueChange = {
                    appId = it
                    saved = false
                },
                label = { Text("APP ID / 应用ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (appIdVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { appIdVisible = !appIdVisible }) {
                        Text(
                            if (appIdVisible) "隐藏" else "显示",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it
                    saved = false
                },
                label = { Text("密钥 / 应用密钥") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Text(
                            if (keyVisible) "隐藏" else "显示",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
            Button(
                onClick = {
                    scope.launch {
                        SettingsStore.save(context, engine, appId.trim(), key.trim())
                        saved = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (saved) "已保存" else "保存")
            }
            Text(
                "密钥仅保存在本机（DataStore），不会上传；输入框默认密文显示，点击显示/隐藏可临时查看。\n" +
                    "百度翻译开放平台、有道智云注册均为免费，把 APP ID 与密钥填到这里即可翻译。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
