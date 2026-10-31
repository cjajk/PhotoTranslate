package com.cjajk.phototranslate.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cjajk.phototranslate.model.LookupResult

/**
 * 词典结果卡片：音标 → 词性分组义项 → 词形变化 → 考纲标签 → 可折叠英英释义。
 * 点词弹窗与查词页共用；无词典数据时降级为普通译文。
 */
@Composable
fun WordResultCard(lookup: LookupResult, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(lookup.word, style = MaterialTheme.typography.titleLarge)
            val ph = listOfNotNull(
                lookup.phoneticEn?.let { "英 /$it/" },
                lookup.phoneticAm?.let { "美 /$it/" }
            ).joinToString("　")
            if (ph.isNotBlank()) {
                Text(
                    ph,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!lookup.hasDict) {
                Text(
                    lookup.plainText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                lookup.posMeans.forEach { pm ->
                    Text(pm.part, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    pm.means.forEachIndexed { i, m ->
                        Text("${i + 1}. $m", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (lookup.exchange.isNotEmpty()) {
                    Text(
                        lookup.exchange.entries.joinToString("　") { "${it.key}：${it.value}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (lookup.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        lookup.tags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    tag,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
                if (lookup.englishDefinitions.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "收起英英释义" else "英英释义（${lookup.englishDefinitions.size}）")
                    }
                    if (expanded) {
                        lookup.englishDefinitions.forEach { def ->
                            Text("• $def", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
