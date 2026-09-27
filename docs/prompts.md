# プロンプト一覧

AI に送るプロンプトと、AI を使えないときの定型文は、すべて `app/src/main/assets/prompts/` に置いてある。コードにはプロンプトの文言を書かず、値（変数）を渡すだけにしている。

文言を変えるときは該当ファイルを編集し、アプリをビルドし直す。どの AI サービス（Claude / DeepSeek など）を選んでも同じファイルを使う。

## フォルダ構成

```
prompts/
├── shared/                    共通部品（ほかのファイルから読み込む）
│   ├── speech.md                話し方（耳で聞く前提、記号を使わない）
│   └── facts.md                 事実の扱い（作り話をしない、回数や名前を正確に）
├── guide/                     スポット解説
│   ├── system.md                システムプロンプト（地元のガイド役）
│   └── user.md                  依頼（スポット名・種別・距離・OSM タグ）
├── companion/                 散歩の友の話しかけ
│   ├── system.md                システムプロンプト（散歩仲間の友だち役）
│   ├── situation.md             いまの状況（日時・天気・今回と過去の散歩・最近の発言）
│   └── events/                  出来事ごとの指示
│       ├── start.md               散歩の開始
│       ├── revisit.md             過去に訪れたスポットへの再訪
│       ├── milestone.md           一定の距離・時間を歩いた
│       ├── rest.md                同じ場所にとどまった（休憩）
│       └── finish.md              散歩の終了（振り返り）
├── fallback/                  定型文（AI を使えないとき、そのまま読み上げる）
│   ├── guide.md                 スポット解説（API キー未設定）
│   ├── nearby.md                スポット解説（AI の呼び出しに失敗）
│   └── companion/               話しかけ（API キー未設定、または失敗）
│       └── start.md / revisit.md / milestone.md / rest.md / finish.md
└── connection_test/           設定画面の「接続テスト」
    ├── system.md
    └── user.md
```

## どの場面でどのファイルを使うか

AI に送るのは「システムプロンプト」と「ユーザープロンプト」の組。

| 場面 | システムプロンプト | ユーザープロンプト | AI を使えないとき |
|---|---|---|---|
| 一覧でスポットをタップ | `guide/system` | `guide/user` | `fallback/guide`（キー未設定） |
| 散策モードで初めてのスポットに近づいた | `guide/system` | `guide/user` | `fallback/guide`（キー未設定）／`fallback/nearby`（失敗） |
| 散歩の開始 | `companion/system` | `companion/situation` ＋ `companion/events/start` | `fallback/companion/start` |
| 過去に訪れたスポットに再び近づいた | `companion/system` | `companion/situation` ＋ `companion/events/revisit` | `fallback/companion/revisit` |
| 一定の距離・時間を歩いた | `companion/system` | `companion/situation` ＋ `companion/events/milestone` | `fallback/companion/milestone` |
| 同じ場所に 4 分以上とどまった | `companion/system` | `companion/situation` ＋ `companion/events/rest` | `fallback/companion/rest` |
| 散歩の終了 | `companion/system` | `companion/situation` ＋ `companion/events/finish` | `fallback/companion/finish` |
| 設定画面の接続テスト | `connection_test/system` | `connection_test/user` | — |

散歩の友のユーザープロンプトは、`companion/situation` の後ろに空行を挟んで出来事のファイルをつなげたもの。

「いつ話しかけるか」（間隔・優先順位・休憩の判定）はプロンプトではなく、コード（`WalkService`）と設定の「話しかけの頻度」（`TalkLevel`）で決まる。

## 書き方

各ファイルは Markdown のテキストで、次の記法が使える（Mustache の一部）。

| 記法 | 意味 |
|---|---|
| `{{name}}` | 変数の値を入れる |
| `{{#name}} … {{/name}}` | 値があるときだけ中身を出す。リストなら要素ごとに繰り返し（要素は `{{.}}`）、入れ子の値なら中でその項目を使える |
| `{{^name}} … {{/name}}` | 値がない（null・空・false）ときだけ中身を出す |
| `{{> shared/speech}}` | 別のファイルを読み込む（拡張子なし、`prompts/` からの相対パス） |
| `{{! … }}` | コメント（AI には送られない） |

- タグだけの行は、行ごと消える（空行が残らない）。
- 3 行以上続く空行は 1 行にまとまり、前後の空白は取り除かれる。
- 同じ名前のセクションを入れ子にはできない。

各ファイルの先頭のコメントに、使う場面と変数の一覧を書いてある。変数を追加・変更するときは、そのコメントとコード（下表）の両方を直す。

## 変数を渡しているコード

| ファイル | 変数を作るコード |
|---|---|
| `guide/user`、`fallback/guide` | `guide/GuidePrompt.kt`（`spotVars` / `fallbackVars`） |
| `companion/situation` | `companion/WalkCompanion.kt`（`situationVars`） |
| `companion/events/*` | `companion/WalkCompanion.kt`（`eventVars`） |
| `fallback/companion/*` | `companion/WalkCompanion.kt`（`fallbackVars`） |
| `fallback/nearby` | `walk/WalkService.kt`（`talkAboutSpot`） |

ファイルのパスは `prompt/Prompts.kt` に定数としてまとめてある。

## 確認方法

```sh
./gradlew testDebugUnitTest
```

`PromptFilesTest` が、すべてのプロンプトファイルを見本の値で組み立て、次を確認する。

- 変数名の誤り・渡し忘れがない（未定義の変数を使うとエラーになる）
- `prompts/` にあるファイルがすべて使われていて、コードが参照するファイルがすべて存在する
- タグが残っていない、空行が続いていない

組み立てた結果は `app/build/prompt-samples/` に書き出されるので、文言を変えたあとの見え方をそこで確認できる。
