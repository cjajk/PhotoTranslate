package com.cjajk.phototranslate.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("翻译密钥配置教程") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "为什么需要自己填密钥？",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "百度翻译与有道智云都要求“开发者密钥”，它相当于您个人的用量账号。" +
                    "如果把公共密钥内置进 App，一旦被他人提取滥用，密钥会被服务商封禁，App 里的翻译也会全部失效。" +
                    "所以 App 让您填写自己的免费密钥，只存在本机，最安全也最稳定。",
                style = MaterialTheme.typography.bodyMedium
            )

            EngineSection(
                title = "方式一：百度翻译（免费，不限字符量）",
                steps = listOf(
                    "打开「百度翻译开放平台」fanyi-api.baidu.com，用百度账号登录。",
                    "点「立即使用」或进入「管理控制台」，开通「通用文本翻译」（标准版免费，无需企业认证，不限字符量）。",
                    "进入「开发者信息 → 创建应用」：应用名称随便填（如 PhotoTranslate），接入方式选「HTTP API」，开通服务勾选「通用文本翻译」，提交。",
                    "创建成功后页面会显示「APP ID」和「密钥」（App Key），复制保存。",
                    "打开本 App「设置」→ 引擎选「百度翻译」→ 把 APP ID 填入「APP ID / 应用ID」，密钥填入「密钥 / 应用密钥」→ 点「保存」。",
                    "回到拍照翻译页，点词即可翻译。"
                )
            )

            EngineSection(
                title = "方式二：有道智云（注册送体验金）",
                steps = listOf(
                    "打开「有道智云」ai.youdao.com，注册并登录（新用户注册赠送体验金，约 100 万字符当量）。",
                    "进入「控制台 → 我的应用」→「创建应用」：应用名称随意（如 PhotoTranslate）。",
                    "创建后进入应用详情，点「添加服务」，选择「自然语言翻译 → 文本翻译」，提交开通。",
                    "在应用详情页复制「应用ID」（appKey）和「应用密钥」（appSecret）。",
                    "打开本 App「设置」→ 引擎选「有道智云」→ 应用ID 填入「APP ID / 应用ID」，应用密钥填入「密钥 / 应用密钥」→ 点「保存」。",
                    "回到拍照翻译页，点词即可翻译。"
                )
            )

            Text("常见问题", style = MaterialTheme.typography.titleMedium)
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "· 翻译提示「请先在设置页填写…」：说明密钥没填或没保存，回设置页检查后重新保存。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "· 提示 errorCode 54003 / 401 等：多为 APP ID 或密钥抄错、复制带了空格，重新核对粘贴。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "· 密钥只存本机（DataStore），不会上传，翻译时只把原文发给您选择的翻译平台。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "· 翻译需要联网，请保持网络可用。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EngineSection(title: String, steps: List<String>) {
    Card {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            steps.forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        "${i + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(step, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
