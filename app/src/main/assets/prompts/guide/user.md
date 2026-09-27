{{!
  スポット解説の依頼（ユーザープロンプト）。
  変数:
    name        スポット名
    category    種別（神社・寺院・史跡など）
    distance_m  利用者からの距離（m）。不明なら null
    tags        OpenStreetMap のタグ（"key=value" のリスト。解説に役立つものだけ）
}}
散策中の利用者の近くにあるスポットを案内してください。
名称: {{name}}
種別: {{category}}
{{#distance_m}}
利用者からの距離: 約{{distance_m}}m
{{/distance_m}}
OpenStreetMapのタグ:
{{#tags}}
- {{.}}
{{/tags}}
