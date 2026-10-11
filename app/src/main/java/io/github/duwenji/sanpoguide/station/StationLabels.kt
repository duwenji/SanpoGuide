package io.github.duwenji.sanpoguide.station

import io.github.duwenji.sanpoguide.station.format.GuideLength
import io.github.duwenji.sanpoguide.station.format.SoundChoice
import io.github.duwenji.sanpoguide.station.format.TalkEventKind

/** How the channel format's values are named on screen. */
object StationLabels {
    fun of(kind: TalkEventKind): String = when (kind) {
        TalkEventKind.SPOT -> "初めてのスポットの解説"
        TalkEventKind.REVISIT -> "再訪したスポットの一言"
        TalkEventKind.MILESTONE -> "距離・時間の区切り"
        TalkEventKind.REST -> "休憩の声かけ"
        TalkEventKind.START -> "開始のあいさつ"
        TalkEventKind.FINISH -> "終了の振り返り"
    }

    fun of(length: GuideLength): String = when (length) {
        GuideLength.SHORT -> "短め"
        GuideLength.NORMAL -> "ふつう"
        GuideLength.LONG -> "長め"
    }

    fun of(sound: SoundChoice): String = when (sound) {
        SoundChoice.AUTO -> "自動"
        SoundChoice.RAIN -> "雨音"
        SoundChoice.WAVES -> "波の音"
        SoundChoice.WIND -> "風の音"
        SoundChoice.BIRDS -> "鳥の声"
        SoundChoice.INSECTS -> "虫の音"
        SoundChoice.TEMPLE -> "遠くの鐘"
    }
}
