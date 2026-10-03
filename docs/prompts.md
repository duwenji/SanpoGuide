# プロンプト一覧

AI に送るプロンプトと、AI を使えないときの定型文は、すべて `app/src/main/assets/prompts/` に置いてある。コードにはプロンプトの文言を書かず、値（変数）を渡すだけにしている。

文言を変えるときは該当ファイルを編集し、アプリをビルドし直す。どの AI サービス（Claude / DeepSeek など）を選んでも同じファイルを使う。

## フォルダ構成

```
prompts/
├── shared/                    共通部品（ほかのファイルから読み込む）
│   ├── speech.md                話し方（耳で聞く前提、記号を使わない）
│   ├── facts.md                 事実の扱い（作り話をしない、回数や名前を正確に）
│   └── guard.md                 守ること（個人情報を尋ねない・宣伝しない など。どのチャンネルでも外せない）
├── guide/                     スポット解説
│   ├── system.md                システムプロンプトの枠（役割・内容の重点・長さはチャンネルから）
│   └── user.md                  依頼（スポット名・種別・距離・OSM タグ。設定でオンなら緯度経度も）
├── companion/                 散歩の友の話しかけ
│   ├── system.md                システムプロンプトの枠（役割・話題の選び方はチャンネルから）
│   ├── situation.md             いまの状況（日時・天気・雰囲気・今回と過去の散歩・最近の発言。設定でオンなら現在地と経路も）
│   ├── station_event.md         チャンネルごとの、出来事への追加の指示（チャンネルにあるときだけ）
│   └── events/                  出来事ごとの指示
│       ├── start.md               散歩の開始
│       ├── revisit.md             過去に訪れたスポットへの再訪
│       ├── milestone.md           一定の距離・時間を歩いた
│       ├── rest.md                同じ場所にとどまった（休憩）
│       ├── sunset.md              日の入りが近い
│       ├── weather_change.md      天気が崩れる予報（雷雨・強い雨・降り始め）
│       ├── facility.md            状況に合った施設（トイレ・休憩所など）が近くにある
│       └── finish.md              散歩の終了（振り返り）
├── fallback/                  定型文（AI を使えないとき、そのまま読み上げる）
│   ├── guide.md                 スポット解説（API キー未設定）
│   ├── nearby.md                スポット解説（AI の呼び出しに失敗）
│   └── companion/               話しかけ（API キー未設定、または失敗）
│       └── start.md / revisit.md / milestone.md / rest.md / sunset.md / weather_change.md / facility.md / finish.md
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
| 1 時間以内に天気が崩れる予報 | `companion/system` | `companion/situation` ＋ `companion/events/weather_change` | `fallback/companion/weather_change` |
| 日の入りの 30〜5 分前 | `companion/system` | `companion/situation` ＋ `companion/events/sunset` | `fallback/companion/sunset` |
| 状況に合った施設が近くにある | `companion/system` | `companion/situation` ＋ `companion/events/facility` | `fallback/companion/facility` |
| 散歩の終了 | `companion/system` | `companion/situation` ＋ `companion/events/finish` | `fallback/companion/finish` |
| 設定画面の接続テスト | `connection_test/system` | `connection_test/user` | — |

散歩の友のユーザープロンプトは、`companion/situation` の後ろに空行を挟んで出来事のファイルをつなげたもの。選んでいるチャンネルにその出来事の追加の指示があれば、さらに空行を挟んで `companion/station_event` をつなげる（開始・再訪・区切り・休憩・終了だけ。天気の急変・日の入り・施設にはつかない）。

## チャンネルとの関係

`guide/system` と `companion/system` は枠で、役割・内容の重点・話題の選び方・解説の長さは、選んでいるチャンネルから変数で入る（[channel-package-format.md](channel-package-format.md) のスロット）。チャンネルの文章は `assets/channels/{id}/prompts/` にあり、ないスロットには標準のチャンネル（`assets/channels/standard/`）の文章が入る。話し方・事実の扱い・守ることは、どのチャンネルでも同じ。

今までの文面を変えたいときは、枠の部分なら `prompts/` を、役割や話題なら `assets/channels/standard/prompts/` を直す。標準のチャンネルで組み立てたシステムプロンプトがチャンネル導入前と同じであることを `StationAssetsTest` が確かめているので、意図して文面を変えたときは、基準のファイル（`app/src/test/resources/prompt-baseline/`）も直す。

「いつ話しかけるか」（間隔・優先順位・休憩の判定）はプロンプトではなく、コード（`WalkService`）と設定の「話しかけの頻度」（`TalkLevel`）で決まる。施設をどの条件で案内するかは `FacilityAdvisor`、天気の急変の判定は `WeatherChangeDetector` で決まる。

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
| `guide/system` | `station/Station.kt`（`guideSystemVars`） |
| `companion/system` | `station/Station.kt`（`companionSystemVars`） |
| `companion/station_event` | `companion/WalkCompanion.kt`（`say`。文章は `Station.eventInstructions`） |
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
