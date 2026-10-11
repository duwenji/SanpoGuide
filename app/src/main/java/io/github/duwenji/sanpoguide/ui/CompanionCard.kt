package io.github.duwenji.sanpoguide.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.duwenji.sanpoguide.companion.WalkCompanion
import io.github.duwenji.sanpoguide.companion.LiveWalk
import io.github.duwenji.sanpoguide.companion.Utterance
import io.github.duwenji.sanpoguide.mood.Mood
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The companion's latest words, plus live walk figures. Tap to see earlier lines.
 * With a [scene], the figures sit on a small picture of the current mood.
 */
@Composable
fun CompanionCard(live: LiveWalk?, lines: List<Utterance>, scene: Mood? = null) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val now by produceState(System.currentTimeMillis(), live) {
        while (live != null) {
            value = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val timeFormat = SimpleDateFormat("H:mm", Locale.JAPAN)

    val status = if (live != null) {
        "散策中　${WalkCompanion.minutes(now - live.startedAt)}分・" +
            "${WalkCompanion.km(live.distanceM)}km・案内${live.spotCount}か所"
    } else {
        "散策の振り返り"
    }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.animateContentSize(),
    ) {
        Column {
            if (scene != null) SceneHeader(scene, status)
            Column(Modifier.padding(16.dp, if (scene != null) 2.dp else 10.dp, 16.dp, 10.dp)) {
                if (scene == null) {
                    Text(
                        status,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                val shown = if (expanded) lines.take(6) else lines.take(1)
                if (shown.isEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "歩きはじめたら話しかけますね", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                shown.forEach { line ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        buildString {
                            append(timeFormat.format(Date(line.at)))
                            line.spotName?.let { append("　").append(it) }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    Text(
                        line.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The walk figures over the mood picture, with the mood in words in the corner. */
@Composable
private fun SceneHeader(scene: Mood, status: String) {
    Box(Modifier.fillMaxWidth().height(72.dp)) {
        MoodScene(scene, Modifier.matchParentSize())
        val onScene = MaterialTheme.typography.labelLarge.copy(
            color = Color.White, shadow = Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 6f),
        )
        Text(
            scene.summary, style = onScene.copy(fontSize = 11.sp),
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp, 6.dp),
        )
        Text(status, style = onScene, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp, 8.dp))
    }
}
