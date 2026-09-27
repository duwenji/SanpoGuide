{{!
  定型文: スポット解説。API キーが未設定のときに、AI の代わりにそのまま読み上げる。
  （AI の呼び出しに失敗したときは fallback/nearby を使う）
  変数:
    name          スポット名
    category      種別
    description   OpenStreetMap の description タグ。なければ null
    start_date    OpenStreetMap の start_date タグ。なければ null
}}
{{name}}は、近くにある{{category}}です。{{#description}}{{description}}。{{/description}}{{#start_date}}{{start_date}}からあるそうです。{{/start_date}}AIによる解説を使うには、設定画面でAPIキーを入力してください。
