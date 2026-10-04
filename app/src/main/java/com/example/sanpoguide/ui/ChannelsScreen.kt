package com.example.sanpoguide.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sanpoguide.station.remote.ListedStatus
import com.example.sanpoguide.station.remote.ListedView
import com.example.sanpoguide.station.remote.ProviderView
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME = DateTimeFormatter.ofPattern("M/d HH:mm").withZone(ZoneId.systemDefault())

/** The channel providers and what they deliver (API-002 設定画面の提供元). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(viewModel: ChannelsViewModel, onClose: () -> Unit) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val notices by viewModel.notices.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val walking by viewModel.walking.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配信されているチャンネル") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (notices.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("お知らせ", style = MaterialTheme.typography.titleSmall)
                        notices.forEach { Text("${TIME.format(it.at)}  ${it.text}", style = MaterialTheme.typography.bodySmall) }
                        TextButton(onClick = viewModel::dismissNotices) { Text("確認した") }
                    }
                }
            }
            Text(
                "審査を経て提供元が署名したチャンネルだけを取り込みます。提供元のリストは 1 日ごと（アプリの起動時と散策の開始時）に確かめます。" +
                    "天気の急変・日の入りの案内など、安全のための案内はどのチャンネルでも同じです。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (providers.isEmpty()) {
                Text("提供元はまだありません。アプリに内蔵の提供元は、公開の準備ができてから加わります。", style = MaterialTheme.typography.bodyMedium)
            }
            providers.forEach { ProviderCard(it, viewModel, enabled = !busy) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { adding = true }, enabled = !busy) { Text("提供元を足す") }
                // Never during a walk (API-002 取得の時期).
                OutlinedButton(onClick = viewModel::refresh, enabled = !busy && !walking && providers.isNotEmpty()) { Text("今すぐ確かめる") }
            }
        }
    }
    if (adding) AddProviderDialog(onDismiss = { adding = false }, onAdd = { url, id -> viewModel.addProvider(url, id) { adding = false } })
}

@Composable
private fun ProviderCard(view: ProviderView, viewModel: ChannelsViewModel, enabled: Boolean) {
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val p = view.record
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(view.name, style = MaterialTheme.typography.titleMedium)
                    Text(p.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = p.enabled, onCheckedChange = { viewModel.setEnabled(p.id, it) }, enabled = enabled)
            }
            Text(
                buildString {
                    append(p.fetchedAt?.let { "最後に確かめた日時: ${TIME.format(it)}" } ?: "まだ確かめていません")
                    view.expiresAt?.let { append("（リストの期限 ${TIME.format(it)}）") }
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (!view.listValid && p.list != null) Text("リストの期限が切れています。このチャンネルは使えません", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            p.error?.let { Text("確かめられませんでした: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            view.channels.forEach { ChannelRow(p.id, it, viewModel, enabled) }
            view.gone.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${c.id}（リストから外れたため使えません）", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { viewModel.remove(p.id, c.id) }, enabled = enabled) { Text("削除") }
                }
            }
            TextButton(onClick = { confirmRemove = true }, enabled = enabled) { Text("この提供元を外す") }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("提供元を外しますか？") },
            text = { Text("この提供元のチャンネルは端末から消え、使えなくなります。") },
            confirmButton = { TextButton(onClick = { confirmRemove = false; viewModel.removeProvider(p.id) }) { Text("外す") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("やめる") } },
        )
    }
}

@Composable
private fun ChannelRow(providerId: String, view: ListedView, viewModel: ChannelsViewModel, enabled: Boolean) {
    val c = view.channel
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(c.name, style = MaterialTheme.typography.bodyLarge)
        Text("${c.summary}（配信: ${c.publisherName}、版 ${c.version}）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        c.publisherChange?.let { Text("配信元の鍵が移し替えられました: ${it.reason}", style = MaterialTheme.typography.bodySmall) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (view.status) {
                ListedStatus.AVAILABLE -> Button(onClick = { viewModel.use(providerId, c.id) }, enabled = enabled) { Text("取り込んで使う") }
                ListedStatus.INSTALLED -> Text("取り込み済み", style = MaterialTheme.typography.labelMedium)
                ListedStatus.UPDATE_PENDING -> OutlinedButton(onClick = { viewModel.update(providerId, c.id) }, enabled = enabled) { Text("新しい版を取り込む") }
                ListedStatus.BLOCKED -> Text("配信元が裏付けなく変わったため、新しい版は使いません", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                ListedStatus.NEEDS_APP_UPDATE -> Text("アプリの更新が必要です", style = MaterialTheme.typography.bodySmall)
                ListedStatus.UNSUPPORTED -> Text("このアプリでは使えない形式です", style = MaterialTheme.typography.bodySmall)
            }
            if (view.installed != null) TextButton(onClick = { viewModel.remove(providerId, c.id) }, enabled = enabled) { Text("削除") }
        }
    }
}

@Composable
private fun AddProviderDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("https://") }
    var id by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提供元を足す") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "この提供元が審査したチャンネルを信用することになります。提供元IDは、提供元のサイトなど、このアプリとは別の方法で確かめたものを入れてください。",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(url, { url = it }, label = { Text("URL") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(id, { id = it.trim() }, label = { Text("提供元ID（sc1…）") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(url, id) }, enabled = url.length > 8 && id.isNotEmpty()) { Text("足す") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
    )
}
