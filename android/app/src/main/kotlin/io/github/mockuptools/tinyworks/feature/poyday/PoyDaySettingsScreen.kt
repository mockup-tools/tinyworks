package io.github.mockuptools.tinyworks.feature.poyday

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PoyDaySettingsScreen(
    rules: List<PoyDayRule>,
    modifier: Modifier = Modifier,
    onSave: (List<PoyDayRule>) -> Unit,
) {
    var drafts by remember(rules) {
        mutableStateOf(
            PoyDayCategory.entries.associateWith { category ->
                rules.filter { it.category == category }.ifEmpty {
                    listOf(PoyDayRule(category))
                }
            },
        )
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "区分ごとの収集日を設定",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PoyDayCategory.entries.forEach { category ->
            PoyDayRuleGroup(
                category = category,
                rules = drafts[category].orEmpty(),
                onRulesChange = { updatedRules ->
                    drafts = drafts + (category to updatedRules)
                },
            )
        }

        Button(
            onClick = {
                onSave(PoyDayCategory.entries.flatMap { drafts[it].orEmpty() })
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("保存")
        }
    }
}

@Composable
private fun PoyDayRuleGroup(
    category: PoyDayCategory,
    rules: List<PoyDayRule>,
    onRulesChange: (List<PoyDayRule>) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = category.icon, fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = {
                        onRulesChange(rules + PoyDayRule(category = category, enabled = true))
                    },
                ) {
                    Text("＋ 追加")
                }
            }

            rules.forEachIndexed { index, rule ->
                PoyDayRuleEditor(
                    rule = rule,
                    index = index,
                    canRemove = rules.size > 1,
                    onChange = { updated ->
                        onRulesChange(rules.mapIndexed { ruleIndex, current ->
                            if (ruleIndex == index) updated else current
                        })
                    },
                    onRemove = {
                        onRulesChange(rules.filterIndexed { ruleIndex, _ -> ruleIndex != index })
                    },
                )
            }
        }
    }
}

@Composable
private fun PoyDayRuleEditor(
    rule: PoyDayRule,
    index: Int,
    canRemove: Boolean,
    onChange: (PoyDayRule) -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "設定 ${index + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Switch(
                checked = rule.enabled,
                onCheckedChange = { onChange(rule.copy(enabled = it)) },
            )
            if (canRemove) {
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = onRemove) {
                    Text("削除")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PoyDayMode.entries.forEach { mode ->
                if (rule.mode == mode) {
                    Button(
                        onClick = { onChange(rule.copy(mode = mode)) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(mode.displayName)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onChange(rule.copy(mode = mode)) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(mode.displayName)
                    }
                }
            }
        }

        if (rule.mode == PoyDayMode.WEEK) {
            PoyDaySelector(
                label = "週",
                value = rule.weekPattern.displayName,
                options = PoyDayWeekPattern.entries.toList(),
                optionLabel = PoyDayWeekPattern::displayName,
                onSelected = { onChange(rule.copy(weekPattern = it)) },
            )
            PoyDaySelector(
                label = "曜日",
                value = "${rule.weekday.displayName}曜日",
                options = PoyDayWeekday.entries.toList(),
                optionLabel = { "${it.displayName}曜日" },
                onSelected = { onChange(rule.copy(weekday = it)) },
            )
            } else {
            PoyDaySelector(
                label = "毎月の日",
                value = "${rule.dayOfMonth}日",
                options = (1..31).toList(),
                optionLabel = { "${it}日" },
                onSelected = { onChange(rule.copy(dayOfMonth = it)) },
            )
        }
    }
}

@Composable
private fun <T> PoyDaySelector(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.width(72.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(value)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        },
                    )
                }
            }
        }
    }
}
