package com.example.sanpoguide.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sanpoguide.station.Station

/**
 * The channel in use, on the main screen; tapping it lists the others. Choosing one mid-walk
 * switches right away, and the companion introduces the new channel.
 */
@Composable
fun StationPicker(current: Station, stations: List<Station>, onChoose: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        AssistChip(
            onClick = { open = true },
            label = { Text("チャンネル: ${current.name}") },
            leadingIcon = { Icon(Icons.Filled.Radio, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = "チャンネルを選ぶ") },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            stations.forEach { station ->
                DropdownMenuItem(
                    text = {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(station.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                station.manifest.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = {
                        if (station.id == current.id) Icon(Icons.Filled.Check, contentDescription = "選択中")
                    },
                    onClick = {
                        open = false
                        if (station.id != current.id) onChoose(station.id)
                    },
                )
            }
        }
    }
}
