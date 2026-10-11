package io.github.duwenji.sanpoguide.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.duwenji.sanpoguide.BuildConfig
import io.github.duwenji.sanpoguide.guide.Provider
import io.github.duwenji.sanpoguide.settings.MapStyle
import io.github.duwenji.sanpoguide.settings.TalkLevel
import io.github.duwenji.sanpoguide.settings.Threshold
import io.github.duwenji.sanpoguide.settings.Thresholds
import io.github.duwenji.sanpoguide.station.Station
import io.github.duwenji.sanpoguide.station.StationLabels
import io.github.duwenji.sanpoguide.station.format.GuideLength
import io.github.duwenji.sanpoguide.station.format.SoundChoice
import io.github.duwenji.sanpoguide.station.format.SpotKind
import io.github.duwenji.sanpoguide.station.format.TalkEventKind
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onClose: () -> Unit, onOpenChannels: () -> Unit) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val stations by viewModel.stations.collectAsStateWithLifecycle()
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

            val station = viewModel.draftStation(draft)
            SectionTitle("チャンネル")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stations.forEach { s ->
                    FilterChip(
                        selected = station.id == s.id,
                        onClick = { viewModel.selectStation(s.id) },
                        label = { Text(s.displayName) },
                    )
                }
            }
            Text(
                station.manifest.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            station.source?.let { source ->
                Text(
                    "配信: ${source.publisherName}（提供元: ${source.providerName}）。話しかけの頻度や場面は配信元が決めます",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "チャンネルによって、語り手や話題、話しかける場面が変わります。" +
                    "天気の急変・日の入り・施設の案内は、どのチャンネルでもお知らせします。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpenChannels) { Text("配信されているチャンネル・提供元") }

            // A third party's channel is as its publisher made it (ADR-001): nothing to adjust here.
            if (station.isBuiltIn) {
                SectionTitle("散歩中の話しかけ（${station.name}）")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TalkLevel.entries.forEach { level ->
                        FilterChip(
                            selected = station.talkLevel == level,
                            onClick = { viewModel.setTalkLevel(level) },
                            label = { Text(level.label) },
                        )
                    }
                }
                Text(
                    "スポットの案内のほかに、歩いた距離や時間、休憩のときに話しかける頻度です。チャンネルごとに保存します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StationSettings(station, viewModel)
            }

            SectionTitle("雰囲気・背景音・写真")
            SwitchRow(
                "画面を雰囲気に合わせる", "時間帯・季節・天気・場所に合わせて、画面の色と散策中の上部の絵を変えます",
                draft.moodEnabled, viewModel::setMoodEnabled,
            )
            SwitchRow(
                "散策中に背景音を流す",
                "雨音・波・鳥の声・虫の音などを、その場の雰囲気に合わせて流します。" +
                    "読み上げ中は小さくし、ほかのアプリで音楽などを再生しているときは流しません",
                draft.ambientEnabled, viewModel::setAmbientEnabled,
            )
            if (draft.ambientEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("音量", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(48.dp))
                    Slider(
                        value = draft.ambientVolume.toFloat(),
                        onValueChange = { viewModel.setAmbientVolume(it.roundToInt()) },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${draft.ambientVolume}%", modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
                }
                SwitchRow(
                    "イヤホンのときだけ流す", "オフにすると、スマートフォンのスピーカーからも流します",
                    draft.ambientEarphonesOnly, viewModel::setAmbientEarphonesOnly,
                )
                Text(
                    "車や自転車の近づく音が聞こえる音量にしてください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            SwitchRow(
                "スポットの写真を表示する", "解説の画面に Wikimedia Commons の写真を表示します（写真が登録されているスポットのみ）",
                draft.spotPhotos, viewModel::setSpotPhotos,
            )
            if (draft.spotPhotos) {
                SwitchRow(
                    "モバイル通信でも写真を取得する", "オフのときは Wi-Fi などの定額の回線でだけ取得します",
                    draft.photosOnMobileData, viewModel::setPhotosOnMobileData,
                )
            }

            SectionTitle("地図")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MapStyle.entries.forEach { style ->
                    FilterChip(
                        selected = draft.mapStyle == style,
                        onClick = { viewModel.setMapStyle(style) },
                        label = { Text(style.label) },
                    )
                }
            }
            Text(
                draft.mapStyle.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (draft.mapStyle.needsGoogleKey) {
                Text("Google Maps Platform の APIキー", style = MaterialTheme.typography.labelLarge)
                ApiKeyField(value = draft.googleMapsApiKey, onValueChange = viewModel::setGoogleMapsApiKey)
                Text(
                    "Google Cloud でプロジェクトに請求先を登録し、Map Tiles API を有効にしてからキーを作成してください。" +
                        "表示した地図の量に応じて、そのアカウントに料金がかかることがあります（無料枠あり）。" +
                        "キーは「API の制限」で Map Tiles API だけに絞り、割り当て（上限）も設定しておくと安全です。" +
                        "地図を表示すると、表示している場所の地図画像を Google から取得します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val uriHandler = LocalUriHandler.current
                TextButton(onClick = { uriHandler.openUri(MAP_TILES_API_URL) }) {
                    Text("Map Tiles API を有効にする（Google Cloud）")
                }
                if (draft.googleMapsApiKey.isBlank()) {
                    Text(
                        "キーが未設定のあいだは、地理院の標準地図で表示します。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            SectionTitle("位置情報")
            SwitchRow(
                "AI に緯度経度と歩いた経路を送る",
                "オンにすると、スポットと現在地の緯度経度、今回歩いた経路（間引いたもの）も AI に送り、" +
                    "場所に即した解説や話しかけにします。オフのときは、スポット名や距離などだけを送ります",
                draft.shareLocationWithAi, viewModel::setShareLocationWithAi,
            )
            Text(
                "地図のルート表示は、この設定にかかわらず、現在地とスポットの緯度経度を経路検索サービス（OpenStreetMap）に送ります。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("案内のしきい値")
            Text(
                "天気の急変・日の入り・近くの施設を、どんなときに知らせるかです。" +
                    "施設の距離は、周辺の検索範囲（ベンチ・自動販売機は 250m、ほかは 600m）より遠くは案内できません。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ThresholdFields(draft.thresholds, onChange = viewModel::setThreshold)
            TextButton(onClick = viewModel::resetThresholds, enabled = draft.thresholds.values.isNotEmpty()) {
                Text("しきい値を既定値に戻す")
            }

            val thresholdsValid = draft.thresholds.values.all { (t, v) -> t.isValid(v) }
            val conflicts = draft.thresholds.conflicts()
            conflicts.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = viewModel::runTest, enabled = test != TestState.Running) {
                    Text("接続テスト")
                }
                Button(onClick = { viewModel.save(); onClose() }, enabled = thresholdsValid && conflicts.isEmpty()) {
                    Text("保存")
                }
            }
            if (!thresholdsValid) {
                Text("しきい値に範囲外の値があります", color = MaterialTheme.colorScheme.error)
            }
            TestResult(test)

            Text(
                "APIキーはこの端末内に暗号化して保存され、選択したAIサービスへの通信にのみ使われます。" +
                    "利用料金は、そのAPIキーのアカウントに請求されます。\n" +
                    "散歩の記録はこの端末内にだけ保存されます。\n" +
                    "地図: 国土地理院　スポット: © OpenStreetMap contributors　天気: Open-Meteo.com　" +
                    "写真: Wikimedia Commons（作者・ライセンスは各写真の下に表示）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DeveloperSection(viewModel, onOpenChannels)
        }
    }
}

/**
 * The version; tapping it seven times turns developer mode on (as Android's own), for publishers
 * trying their channel from a test ticket before review (API-002 試用チケット).
 */
@Composable
private fun DeveloperSection(viewModel: SettingsViewModel, onOpenChannels: () -> Unit) {
    val developerMode by viewModel.developerMode.collectAsStateWithLifecycle()
    var taps by remember { mutableIntStateOf(0) }
    Text(
        "バージョン ${BuildConfig.VERSION_NAME}" + when {
            developerMode -> "（開発者モード）"
            taps in 3..6 -> "（あと ${7 - taps} 回で開発者モード）"
            else -> ""
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(enabled = !developerMode) {
            taps += 1
            if (taps >= 7) viewModel.setDeveloperMode(true)
        }.padding(vertical = 8.dp),
    )
    if (developerMode) {
        SectionTitle("開発者向け")
        SwitchRow(
            "開発者モード",
            "配信元が審査の前に、自分のチャンネルを試用チケット（QR コード）で試すためのものです。オフにすると、試用中のチャンネルは使えなくなります",
            true,
        ) { on ->
            if (!on) {
                taps = 0
                viewModel.setDeveloperMode(false)
            }
        }
        OutlinedButton(onClick = onOpenChannels) { Text("試用チケットを読み込む") }
    }
}

private const val MAP_TILES_API_URL = "https://console.cloud.google.com/apis/library/tile.googleapis.com"

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

/** One number field per [Threshold], under its group's heading. */
@Composable
private fun ThresholdFields(thresholds: Thresholds, onChange: (Threshold, Int?) -> Unit) {
    Threshold.entries.groupBy { it.group }.forEach { (group, items) ->
        Text(group, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
        items.forEach { t ->
            val value = thresholds.values[t] ?: t.default
            val valid = t.isValid(value)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "既定値 ${t.default}${t.unit}（${t.min}〜${t.max}）",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.width(12.dp))
                ThresholdField(t, value, valid, onChange = { onChange(t, it) })
            }
        }
    }
}

@Composable
private fun ThresholdField(t: Threshold, value: Int, valid: Boolean, onChange: (Int?) -> Unit) {
    fun textOf(v: Int) = if (v == SettingsViewModel.INVALID) "" else v.toString()
    // The field keeps its own text: rewriting "08" to "8" under the cursor made it jump.
    var text by remember(t) { mutableStateOf(textOf(value)) }
    // Follow changes from outside the field (reset to defaults), not the field's own edits.
    LaunchedEffect(value) {
        if ((text.toIntOrNull() ?: SettingsViewModel.INVALID) != value) text = textOf(value)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input.filter(Char::isDigit).take(4)
            onChange(text.toIntOrNull())
        },
        suffix = { Text(t.unit) },
        isError = !valid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(120.dp),
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

/** The chosen built-in channel's own settings (docs/channels.md「利用者のカスタマイズ」). Its voice and topics can't be changed. */
@Composable
private fun StationSettings(station: Station, viewModel: SettingsViewModel) {
    Text("話しかける場面", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TalkEventKind.entries.forEach { kind ->
            FilterChip(
                selected = station.talksOn(kind),
                onClick = { viewModel.setEvent(kind, !station.talksOn(kind)) },
                label = { Text(StationLabels.of(kind)) },
            )
        }
    }
    Text(
        "天気の急変・日の入り・施設の案内は、安全のため、どのチャンネルでもお知らせします。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Text("解説の長さ", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GuideLength.entries.forEach { length ->
            FilterChip(
                selected = station.guideLength == length,
                onClick = { viewModel.setGuideLength(length) },
                label = { Text(StationLabels.of(length)) },
            )
        }
    }

    Text("優先する話題", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpotKind.entries.forEach { kind ->
            val on = kind in station.prefer
            FilterChip(selected = on, onClick = { viewModel.setPrefer(kind, !on) }, label = { Text(kind.category) })
        }
    }
    Text(
        "近くに複数のスポットがあるとき、選んだ種類から先に話題にします。" +
            "飲食店から専門店までは、選んだときだけ探して地図と一覧に出します（チェーン店は除きます）。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    SwitchRow(
        "話し方を雰囲気に合わせる", "時間帯・季節・天気・場所に合わせて、話しかけのトーンを変えます",
        station.moodTone, viewModel::setMoodTone,
    )

    Text("背景音の種類", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SoundChoice.entries.forEach { sound ->
            FilterChip(
                selected = station.sound == sound,
                onClick = { viewModel.setSound(sound) },
                label = { Text(StationLabels.of(sound)) },
            )
        }
    }
    Text(
        "背景音を流すかどうかと音量は、下の「散策中に背景音を流す」で決めます。「自動」は雰囲気に合わせて選びます。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    TextButton(onClick = viewModel::resetStation, enabled = !station.overrides.isEmpty) {
        Text("「${station.name}」を初期値に戻す")
    }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}
