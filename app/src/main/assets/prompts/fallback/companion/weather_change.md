{{!
  定型文: 天気が崩れる予報。API キーが未設定、または AI の呼び出しに失敗したときにそのまま読み上げる。
  変数: companion/events/weather_change と同じ
}}
{{#minutes}}あと{{minutes}}分ほどで{{/minutes}}{{^minutes}}まもなく{{/minutes}}{{description}}になりそうです。
{{#thunder}}
雷が鳴りそうなので、早めに建物の中へ入りましょう。
{{/thunder}}
{{#heavy_rain}}
雨宿りできる場所を探しておきましょう。
{{/heavy_rain}}
{{#rain}}
傘の用意をしておきましょうか。
{{/rain}}
