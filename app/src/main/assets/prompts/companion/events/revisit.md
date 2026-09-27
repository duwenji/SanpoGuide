{{!
  散歩の友: 過去の散歩で訪れたスポットに、また近づいたとき。
  （初めてのスポットは guide/* の解説を使う）
  変数:
    name          スポット名
    category      種別
    total_count   通算何回目の訪問か（今回を含む）
    today_count   今日何回目の訪問か（今回を含む）。今日初めてなら null
    last_when     前回の訪問（「今日」または「N日前」）
    last_remark   前回ここで話した内容（先頭200字）。なければ null
}}
# 出来事
「{{name}}」（{{category}}）の近くに来ました。ここに来るのは通算{{total_count}}回目{{#today_count}}（今日だけで{{today_count}}回目）{{/today_count}}で、前回は{{last_when}}です。
{{#last_remark}}
前回あなたはこう話しました:「{{last_remark}}」。
{{/last_remark}}
同じ説明は繰り返さず、また来たことに気づいた一言や、前回とは違う楽しみ方を短く話してください。
