{{!
  定型文: 近くの施設の案内。API キーが未設定、または AI の呼び出しに失敗したときにそのまま読み上げる。
  変数: companion/events/facility と同じ（name と need_* は文面では一部のみ使用）
}}
{{#direction}}{{direction}}{{/direction}}{{distance_m}}mほどのところに{{label}}があります。
{{#need_shelter}}
{{^rain_coming}}
雨宿りにどうぞ。
{{/rain_coming}}
{{#rain_coming}}
降ってきたら、ここで雨宿りできますよ。
{{/rain_coming}}
{{/need_shelter}}
{{#need_drink}}
水分補給もお忘れなく。
{{/need_drink}}
{{#need_seat}}
ひと休みにどうぞ。
{{/need_seat}}
