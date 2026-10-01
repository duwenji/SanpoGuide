{{!
  散歩の友: いまの状況（ユーザープロンプトの前半）。後ろに companion/events/* のどれかが続く。
  変数:
    now           日時（例: 9月28日（月）14:05）
    time_of_day   朝・昼・夕方・夜・深夜
    season        春・夏・秋・冬
    weather       いまの天気（例: 霧雨、気温19℃）。取得できない／再訪の一言では null
    mood          いまの雰囲気。設定で雰囲気をオフにしている／再訪の一言では null
      summary       季節・時間帯・空模様・場所をまとめたもの（例: 秋の夕方、雨、寺社の近く）
    walk          今回の散歩（散歩の終了時は null）
      minutes       開始からの分数
      km            歩いた距離（km）
      spots         今回案内した場所（「、」区切り）。なければ null
    last_walk     前回の散歩（初めての散歩なら null）
      past_count    これまでの散歩の回数（今回を除く）
      when          「今日」または「N日前」
      clock         前回の開始時刻（例: 13:50）
      km            前回の距離（km）
      minutes       前回の時間（分）
      spots         前回立ち寄った場所（最大3件、「、」区切り）。なければ null
      week_count    この7日間の散歩の回数（今回を除く）
      today_number  今回が今日何回目の散歩か。今日初めてなら null
    recent        今回の散歩で最近話したこと（なければ null）
      lines         発言のリスト（古い順、最大4件）
    location      位置（設定で位置情報を AI に送らないとき・散歩の外では null）
      here          現在地の緯度経度（例: 35.32580, 139.55620）
      route         今回歩いた経路（最大20点に間引いた緯度経度を「 → 」でつないだもの、古い順）。1点だけなら null
}}
# いまの状況
- 日時: {{now}}（{{time_of_day}}、{{season}}）
{{#weather}}
- 天気: {{weather}}
{{/weather}}
{{#mood}}
- 雰囲気: {{summary}}
{{/mood}}
{{#walk}}
- 今回の散歩: 開始から{{minutes}}分、{{km}}km
{{#spots}}
- 今回案内した場所: {{spots}}
{{/spots}}
{{/walk}}
{{#location}}
- 現在地（緯度, 経度）: {{here}}
{{#route}}
- 今回歩いた経路（緯度,経度、古い順）: {{route}}
{{/route}}
{{/location}}

# これまでの散歩
{{^last_walk}}
- 今回が、この利用者との初めての散歩です
{{/last_walk}}
{{#last_walk}}
- 通算{{past_count}}回（今回を除く）
- 前回: {{when}}の{{clock}}開始、{{km}}km・{{minutes}}分{{#spots}}、立ち寄り: {{spots}}{{/spots}}
- この7日間の散歩: {{week_count}}回
{{#today_number}}
- 今回は、今日{{today_number}}回目の散歩です
{{/today_number}}
{{/last_walk}}
{{#recent}}

# 今回の散歩であなたが最近話したこと（繰り返さないこと）
{{#lines}}
- {{.}}
{{/lines}}
{{/recent}}
