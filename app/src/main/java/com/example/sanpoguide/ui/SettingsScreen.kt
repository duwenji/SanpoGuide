package com.example.sanpoguide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sanpoguide.guide.Provider
import com.example.sanpoguide.settings.TalkLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onClose: () -> Unit) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val test by viewModel.test.collectAsStateWithLifecycle()
    val provider = draft.provider

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AIガイドの設定") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("AIサービス")
            Column(Modifier.selectableGroup()) {
                Provider.entries.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(p == provider, role = Role.RadioButton) { viewModel.selectProvider(p) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = p == provider, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(p.label, modifier = Modifier.weight(1f))
                        if (draft.apiKey(p).isNotBlank()) {
                            Text(
                                "キー設定済み", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            SectionTitle("APIキー")
            ApiKeyField(value = draft.apiKey(), onValueChange = viewModel::setApiKey)
            if (provider.keyPageUrl.isNotEmpty()) {
                val uriHandler = LocalUriHandler.current
                TextButton(onClick = { uriHandler.openUri(provider.keyPageUrl) }) {
                    Text("${provider.label} のAPIキーを取得する")
                }
            }

            if (provider == Provider.CUSTOM) {
                SectionTitle("接続先URL")
                OutlinedTextField(
                    value = draft.customBaseUrl,
                    onValueChange = viewModel::setCustomBaseUrl,
                    placeholder = { Text("https://example.com/v1") },
                    supportingText = { Text("/chat/completions の手前までを入力します") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionTitle("モデル")
            if (provider == Provider.CLAUDE) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Provider.CLAUDE_MODELS.forEach { m ->
                        FilterChip(
                            selected = draft.model() == m,
                            onClick = { viewModel.setModel(m) },
                            label = { Text(m) },
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = draft.models[provider].orEmpty(),
                    onValueChange = viewModel::setModel,
                    placeholder = { Text(provider.defaultModel.ifEmpty { "モデル名" }) },
                    supportingText = if (provider.defaultModel.isNotEmpty()) {
                        { Text("空欄の場合は ${provider.defaultModel} を使います") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionTitle("散歩中の話しかけ")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TalkLevel.entries.forEach { level ->
                    FilterChip(
                        selected = draft.talkLevel == level,
                        onClick = { viewModel.setTalkLevel(level) },
                        label = { Text(level.label) },
                    )
                }
            }
            Text(
                "スポットの案内のほかに、歩いた距離や時間、休憩のときに話しかける頻度です。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = viewModel::runTest, enabled = test != TestState.Running) {
                    Text("接続テスト")
                }
                Button(onClick = { viewModel.save(); onClose() }) { Text("保存") }
            }
            TestResult(test)

            Text(
                "APIキーはこの端末内に暗号化して保存され、選択したAIサービスへの通信にのみ使われます。" +
                    "利用料金は、そのAPIキーのアカウントに請求されます。\n" +
                    "散歩の記録はこの端末内にだけ保存されます。\n" +
                    "地図: 国土地理院　スポット: © OpenStreetMap contributors　天気: Open-Meteo.com",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ApiKeyField(value: String, onValueChange: (String) -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("APIキーを貼り付け") },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "キーを隠す" else "キーを表示",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TestResult(test: TestState) {
    when (test) {
        TestState.Idle -> Unit
        TestState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("接続を確認しています…")
        }
        is TestState.Success -> Text(
            "接続できました（応答: ${test.reply}）", color = MaterialTheme.colorScheme.primary,
        )
        is TestState.Failed -> Text(test.message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}
