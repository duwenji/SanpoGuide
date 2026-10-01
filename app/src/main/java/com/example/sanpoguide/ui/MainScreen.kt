package com.example.sanpoguide.ui

import com.example.sanpoguide.R
import com.example.sanpoguide.data.FacilityKind
import com.example.sanpoguide.data.Poi
import com.example.sanpoguide.settings.GoogleMapType
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Roofing
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.History

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit,
    onToggleWalk: (walking: Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val liveWalk by viewModel.liveWalk.collectAsStateWithLifecycle()
    val lines by viewModel.companionLines.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val spots by viewModel.spots.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val walking by viewModel.walking.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val facilities by viewModel.facilities.collectAsStateWithLifecycle()
    val nearestFacilities by viewModel.nearestFacilities.collectAsStateWithLifecycle()
    val focus by viewModel.focus.collectAsStateWithLifecycle()
    val route by viewModel.route.collectAsStateWithLifecycle()
    val map by viewModel.map.collectAsStateWithLifecycle()
    val googleCopyright by viewModel.googleCopyright.collectAsStateWithLifecycle()
    val mood by viewModel.mood.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("散策ガイド") },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = hasLocationPermission && !loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "周辺を再検索")
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, contentDescription = "散歩の記録")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "AIガイドの設定")
                    }
                },
            )
        },
        floatingActionButton = {
            if (hasLocationPermission) {
                ExtendedFloatingActionButton(
                    onClick = { onToggleWalk(walking) },
                    icon = {
                        Icon(
                            if (walking) Icons.Filled.Stop else Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = null,
                        )
                    },
                    text = { Text(if (walking) "散策を終える" else "散策をはじめる") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!hasLocationPermission) {
                PermissionPrompt(onRequestPermission)
                return@Column
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (walking || lines.isNotEmpty()) {
                // The scene is part of walk mode, not something to look at while walking.
                CompanionCard(liveWalk, lines, scene = mood.takeIf { settings.moodEnabled && walking })
            }
            if (!settings.isConfigured) {
                Banner("AIのAPIキーが未設定のため、簡易解説で動作しています。タップして設定", onOpenSettings)
            }
            error?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp, 8.dp),
                    )
                }
            }
            map.notice?.let { Banner(it, onOpenSettings) }
            if (nearestFacilities.isNotEmpty()) {
                FacilityBar(nearestFacilities, onClick = { viewModel.focus(it.facility) })
            }
            Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                SpotMap(
                    tiles = map.tiles,
                    location = location,
                    heading = rememberHeading(location),
                    spots = spots,
                    facilities = facilities,
                    route = route,
                    focus = focus,
                    onSpotClick = viewModel::select,
                    onViewportChanged = viewModel::onMapViewport,
                    modifier = Modifier.fillMaxSize(),
                )
                route?.let { RouteLabel(it, onClear = viewModel::clearRoute, modifier = Modifier.align(Alignment.TopStart)) }
                (map.tiles as? MapTiles.Google)?.let { tiles ->
                    GoogleAttribution(tiles, googleCopyright, Modifier.align(Alignment.BottomStart).fillMaxWidth())
                }
            }
            // The list is sorted by distance; when the nearest spot changes, show it again
            // instead of staying anchored to an item that has drifted down the list.
            val listState = rememberLazyListState()
            LaunchedEffect(spots.firstOrNull()?.poi?.id) { listState.scrollToItem(0) }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                items(spots, key = { it.poi.id }) { item ->
                    SpotRow(item, onClick = { viewModel.select(item) })
                    HorizontalDivider()
                }
            }
        }
    }

    selected?.let { spot ->
        ModalBottomSheet(onDismissRequest = viewModel::dismiss) {
            GuideSheet(
                spot = spot,
                onSpeak = { text -> viewModel.speak(spot.item.poi, text) },
                onStop = viewModel::stopSpeaking,
            )
        }
    }
}

/**
 * Required while Google tiles are shown (Map Tiles API policies): the Google Maps logo,
 * unmodified, 16–19dp tall with clear space around it, and the attribution for the area in full.
 * The outlined logo is the one for busy backgrounds like a map; light outline on the road map,
 * dark on photos.
 */
@Composable
private fun GoogleAttribution(tiles: MapTiles.Google, copyright: String?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Image(
            painterResource(
                if (tiles.type == GoogleMapType.SATELLITE) R.drawable.google_maps_logo_dark_outline
                else R.drawable.google_maps_logo_light_outline,
            ),
            contentDescription = "Google マップ",
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 5.dp).height(18.dp),
        )
        Spacer(Modifier.weight(1f))
        copyright?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF202124),
                modifier = Modifier
                    .padding(start = 8.dp)
                    .background(Color.White.copy(alpha = 0.75f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

/** Which spot the line on the map leads to, and a way to hide it. */
@Composable
private fun RouteLabel(route: RouteToSpot, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shadowElevation = 2.dp,
        modifier = modifier.padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    "${route.spot.name}へ", style = MaterialTheme.typography.labelLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        route.loading -> "ルートを検索中…"
                        route.route.onPaths -> "道沿いのルート"
                        else -> "方向のみ（ルートを取得できませんでした）"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = "ルートを消す")
            }
        }
    }
}

@Composable
private fun SpotRow(item: SpotItem, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(item.poi.name) },
        supportingContent = { Text(item.poi.category) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.visitCount > 0) {
                    Icon(
                        Icons.Filled.CheckCircle, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${item.visitCount}回", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                item.distanceM?.let { Text(formatDistance(item.poi, it), style = MaterialTheme.typography.labelLarge) }
            }
        },
    )
}

@Composable
private fun GuideSheet(spot: SelectedSpot, onSpeak: (String) -> Unit, onStop: () -> Unit) {
    val poi = spot.item.poi
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SpotPhotoView(spot.photo)
        Text(poi.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            listOfNotNull(poi.category, spot.item.distanceM?.let { formatDistance(poi, it) }).joinToString(" ・ "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        when (val guide = spot.guide) {
            GuideState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("ガイドが解説を準備しています…")
            }
            is GuideState.Failed -> Text(guide.message, color = MaterialTheme.colorScheme.error)
            is GuideState.Ready -> {
                Text(guide.text, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onSpeak(guide.text) }) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("読み上げ")
                    }
                    OutlinedButton(onClick = onStop) { Text("停止") }
                }
            }
        }
    }
}

/** The spot's photo with its credit line (the license requires it); nothing when there's none. */
@Composable
private fun SpotPhotoView(state: PhotoState) {
    when (state) {
        PhotoState.None -> Unit
        PhotoState.Loading -> Text(
            "写真を読み込んでいます…", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp),
        )
        PhotoState.WaitingForWifi -> Text(
            "写真は Wi-Fi 接続時に表示します（設定で変更できます）", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp),
        )
        is PhotoState.Ready -> {
            val photo = state.photo
            val uriHandler = LocalUriHandler.current
            Image(
                photo.bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
            )
            Text(
                listOfNotNull("写真: ${photo.author ?: "作者不明"}", photo.license, "Wikimedia Commons").joinToString(" / "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri(photo.pageUrl) }.padding(top = 4.dp, bottom = 12.dp),
            )
        }
    }
}

/** The nearest facility of each kind; tapping one pans the map to it. */
@Composable
private fun FacilityBar(items: List<FacilityItem>, onClick: (FacilityItem) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            val kind = item.facility.kind
            AssistChip(
                onClick = { onClick(item) },
                label = { Text(listOfNotNull(kind.label, item.distanceM?.let(::formatMeters)).joinToString(" ")) },
                leadingIcon = {
                    // The map marker's dot, so the chips also serve as the map legend.
                    Box(Modifier.size(20.dp).background(facilityColor(kind), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(facilityIcon(kind), contentDescription = null, Modifier.size(12.dp), tint = Color.White)
                    }
                },
            )
        }
    }
}

private fun facilityIcon(kind: FacilityKind) = when (kind) {
    FacilityKind.TOILETS -> Icons.Filled.Wc
    FacilityKind.DRINKING_WATER -> Icons.Filled.WaterDrop
    FacilityKind.VENDING_MACHINE -> Icons.Filled.LocalDrink
    FacilityKind.SHELTER -> Icons.Filled.Roofing
    FacilityKind.BENCH -> Icons.Filled.Chair
}

@Composable
private fun Banner(text: String, onClick: (() -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp, 8.dp))
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("周辺のスポットを案内するには、位置情報の許可が必要です。")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRequest) { Text("位置情報を許可する") }
        }
    }
}

private fun formatDistance(poi: Poi, m: Int): String = if (poi.isInside(m)) "敷地内" else formatMeters(m)

private fun formatMeters(m: Int): String = if (m < 1000) "${m}m" else "%.1fkm".format(m / 1000f)
