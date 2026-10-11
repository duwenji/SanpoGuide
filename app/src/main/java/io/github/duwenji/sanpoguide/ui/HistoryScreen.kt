package io.github.duwenji.sanpoguide.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.duwenji.sanpoguide.SanpoApp
import io.github.duwenji.sanpoguide.companion.WalkCompanion
import io.github.duwenji.sanpoguide.history.WalkRecord
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val history = (application as SanpoApp).history
    private val stations = (application as SanpoApp).stations
    val walks = history.walks

    fun stationsOf(walk: WalkRecord): String = stations.namesOf(walk)

    fun clearAll() {
        viewModelScope.launch { history.clear() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onClose: () -> Unit) {
    val walks by viewModel.walks.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("散歩の記録") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    if (walks.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "記録をすべて削除")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (walks.isEmpty()) {
            Text(
                "まだ記録はありません。「散策をはじめる」で散歩すると、ここに残ります。",
                modifier = Modifier.padding(padding).padding(24.dp),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Summary(walks) }
            items(walks, key = { it.id }) { walk ->
                WalkRow(walk, viewModel.stationsOf(walk))
                HorizontalDivider()
            }
            item {
                Text(
                    "記録はこの端末内にだけ保存されます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("記録をすべて削除しますか？") },
            text = { Text("散歩の履歴と訪れた場所の記録が消え、元に戻せません。") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAll(); confirmClear = false }) { Text("削除") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun Summary(walks: List<WalkRecord>) {
    val totalKm = WalkCompanion.km(walks.sumOf { it.distanceM })
    val totalMin = WalkCompanion.minutes(walks.sumOf { it.durationMs })
    val favorites = walks.flatMap { w -> w.visits.distinctBy { it.name } }
        .groupBy { it.name }
        .map { (_, v) -> v.first().name to v.size }
        .filter { it.second >= 2 }
        .sortedByDescending { it.second }
        .take(3)

    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("これまでの散歩", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Stat("回数", "${walks.size}回")
                Stat("距離", "${totalKm}km")
                Stat("時間", formatDuration(totalMin))
            }
            if (favorites.isNotEmpty()) {
                Text(
                    "よく訪れる場所：" + favorites.joinToString("、") { (name, n) -> "$name（${n}回）" },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun WalkRow(walk: WalkRecord, stations: String) {
    val date = SimpleDateFormat("M月d日（E）H:mm", Locale.JAPAN).format(Date(walk.startedAt))
    ListItem(
        headlineContent = { Text(date) },
        supportingContent = {
            Text(
                "${WalkCompanion.km(walk.distanceM)}km・${formatDuration(WalkCompanion.minutes(walk.durationMs))}" +
                    "\nチャンネル: $stations" +
                    if (walk.visits.isEmpty()) "" else "\n" + walk.visits.joinToString("、") { it.name },
            )
        },
    )
}

private fun formatDuration(minutes: Int): String =
    if (minutes < 60) "${minutes}分" else "${minutes / 60}時間${minutes % 60}分"
