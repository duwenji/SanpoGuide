# 秘密メッセージ サーバー API（OpenAPI）の説明と使い方

[sanpo-messages.openapi.yaml](sanpo-messages.openapi.yaml) は、位置に紐づく秘密メッセージのサーバー API を OpenAPI 3.1 で書いたもの。サーバーを作る人とアプリを作る人が、同じ定義を見て実装するためのファイル。

- **正本は契約書** [../secret-messages-api.md](../secret-messages-api.md)（API-001）。判断の理由・暗号方式・配信の判定規則は契約書にある。食い違いがあれば契約書に合わせてこのファイルを直す
- サーバーは、この API に準拠していれば実装方法（言語・フレームワーク・データベース）も置き場所も問わない。アプリは設定の URL だけで接続先を選ぶ

## フォルダーの中身

| ファイル | 内容 |
|---|---|
| `sanpo-messages.openapi.yaml` | API の定義（エンドポイント、リクエスト・レスポンスの形、エラー） |
| `redocly.yaml` | 検証ツール（Redocly CLI）の設定。意図して外したルールと理由 |
| `README.md` | このファイル |

## 見る・検証する

Node.js があれば、インストールなしで使える。

```sh
cd docs/api

# 検証（定義の書き間違いを見つける）
npx @redocly/cli lint sanpo-messages.openapi.yaml

# 読みやすい HTML にする（ブラウザーで開く。生成物はコミットしない）
npx @redocly/cli build-docs sanpo-messages.openapi.yaml -o api.html
```

ほかの見方:
- VS Code の拡張機能「OpenAPI (Swagger) Editor」などで、エディターの中でプレビューできる
- [Swagger Editor](https://editor.swagger.io/) にファイルの中身を貼る（外部のサイトに送ることになる点に注意）
- この README のシーケンス図（Mermaid）は GitHub の画面、または VS Code の Markdown プレビュー（Mermaid 対応の拡張機能が必要）で図になる

## 定義の読み方

### 独自の項目 `x-sanpo-feature`・`x-sanpo-release`

各操作に、その操作が属する機能と版を付けている。

| `x-sanpo-feature` | 内容 | 版 |
|---|---|---|
| `core` | 検出・認証・アカウント・通報。すべての準拠サーバーが実装する | 1 |
| `direct` | 個人宛てのメッセージ、管理、受信箱、添付、プッシュ | 1 |
| `group` | グループ | 1 |
| `subscription` | 受信フィルター | 1 |
| `admin` | 運営 API | 2 |

サーバーは対応する機能を検出 API（`/.well-known/sanpo-messages`）の `features` で知らせる。`features` にない機能の操作には `400 unsupported_feature` を返す。アプリは `features` にある機能だけを画面に出す。

`public`（公開メッセージ）・`audience`（配信条件）・`publisher` も第 2 版の機能だが、専用のエンドポイントはなく、既存の操作の項目（封筒の `visibility: public`、受信箱の `cells` など）として入る。スキーマの説明で **［第 2 版］** と書いてある項目がそれにあたる。

### 封筒（`Envelope`）は二重構造

メッセージは「封筒」で送る。封筒の `protected` は base64url で包んだ JSON で、中身の形は `EnvelopeHeader` スキーマに書いてある（OpenAPI の `contentSchema`）。

```
Envelope
├── protected  … base64url(EnvelopeHeader の JSON)  ← サーバーが読む（宛先・種類・期間など）
├── ciphertext … base64url(nonce + 暗号文)          ← サーバーは読めない（本文・正確な位置）
└── sig        … protected + "." + ciphertext への送り手の署名
```

サーバーは `protected` を読んで配信に使うが、**受け取った 3 つの文字列を変えずに保存して返す**こと（整形すると署名が合わなくなる）。グループの名簿（`SignedRoster` → `Roster`）も同じ構造。

### エラー

エラーはすべて `application/problem+json`（RFC 9457）で、`code` で種類を見分ける。応答の定義（`components/responses`）の説明に、その HTTP ステータスで返りうる `code` を書いている。`code` ごとのクライアントの動き方は契約書の「エラー表」を参照。

### スキーマで表せないルール

次は OpenAPI では書けないため、説明文と契約書にだけある。準拠テストで確認する。

- 署名の検証（封筒・名簿・チャレンジ）
- フィルター式の入れ子は 4 段まで、条件は合計 32 個まで
- 同じ封筒の再送は `200` で同じ結果、中身が違えば `409 id_conflict`
- 更新の `rev` は今の版 + 1
- 配信の判定（宛先 → 受信フィルター → ブロック）

## 使い方の流れ（シーケンス図）

図の「アプリ」は SanpoGuide、「サーバー」は設定した URL の準拠サーバー。暗号の処理（鍵の作成・暗号化・署名）はすべてアプリの中で行う。

### 1. 接続先の確認と初回登録

設定画面で URL を保存したときの流れ。以後のリクエストはすべてトークンを付ける。

```mermaid
sequenceDiagram
    autonumber
    actor U as 利用者
    participant App as アプリ
    participant S as サーバー
    U->>App: 設定で URL を入力して保存
    App->>S: GET /.well-known/sanpo-messages
    S-->>App: 200 ServerInfo（versions・features・limits・push）
    App->>App: v1・sanpo-v1 に対応しているか確認し、サーバー名を表示
    Note over App: 鍵がなければ作る（署名鍵 Ed25519・暗号化鍵 X25519）
    App->>S: POST /v1/auth/challenge { account }
    S-->>App: 200 { nonce, expiresAt }
    App->>App: "sanpo-auth-v1\n{origin}\n{nonce}" に署名
    App->>S: POST /v1/auth/token { account, nonce, sig, sigKey }
    S-->>App: 200 { token, expiresAt }
    App->>S: PUT /v1/accounts/me { sigKey, encKeys }
    S-->>App: 200 Account
    App->>S: PUT /v1/devices/{deviceId} { method: poll | unifiedpush }
    S-->>App: 204
```

リクエストの例:

```http
POST /v1/auth/token HTTP/1.1
Content-Type: application/json

{ "account": "sg1abcd…", "nonce": "q3v…", "sig": "MEU…", "sigKey": "11qY…" }
```

```http
HTTP/1.1 200 OK
Content-Type: application/json

{ "token": "eyJ…", "expiresAt": "2026-09-30T11:00:00+09:00" }
```

### 2. 友だちを追加して、メッセージを置く

公開鍵は対面の QR コードで交換する。サーバーから取った鍵は、QR コードで得たアカウントIDと照合してから使う。

```mermaid
sequenceDiagram
    autonumber
    participant A as アプリ（送り手）
    participant S as サーバー
    participant B as アプリ（受け手）
    B->>A: QR コード（アカウントID・接続先 URL・署名鍵）
    A->>S: GET /v1/accounts/{B のアカウントID}
    S-->>A: 200 Account（sigKey・encKeys）
    A->>A: sigKey からアカウントIDを計算して一致を確認、encKeys の署名を確認
    A->>A: DEK を作り、本文・正確な位置を暗号化
    A->>A: DEK を B と自分の暗号化鍵で包む（HPKE）、封筒に署名
    opt 音声・写真がある
        A->>S: PUT /v1/blobs/{blobId}（暗号化したバイト列）
        S-->>A: 201
    end
    A->>S: POST /v1/messages（Envelope）
    S->>S: 署名・上限を確認、B の受信フィルター・ブロックを判定して配信記録を作る
    S-->>A: 201 { id, rev: 1, state: active }
    S--)B: UnifiedPush の合図 { type: inbox }（登録していれば）
```

`POST /v1/messages` の応答が届かなかったときは、**同じ封筒をそのまま再送する**（`200` で同じ結果が返る）。作り直すと別のメッセージになる。

### 3. 受け取って、その場所で開ける

```mermaid
sequenceDiagram
    autonumber
    participant B as アプリ（受け手）
    participant S as サーバー
    Note over B: きっかけ: UnifiedPush の合図、散策モードの開始時と 15 分ごと、散策モード外は数時間ごと
    loop hasMore が false になるまで
        B->>S: GET /v1/inbox?cursor={前回の cursor}
        S-->>B: 200 { items, cursor, hasMore }
    end
    B->>B: type: message → 署名を確認して復号、同じ id の古い版を置き換えて保存
    B->>B: type: revoked → 端末からも消す
    B->>S: POST /v1/inbox/{id}/ack { state: delivered }
    S-->>B: 204
    Note over B: 散策中、radiusM 以内に来て opensAfter を過ぎたら
    B->>B: 通知と話しかけ「○○さんからのメッセージがあります」
    opt 添付がある
        B->>S: GET /v1/blobs/{blobId}
        S-->>B: 200（暗号化したバイト列）
    end
    B->>S: POST /v1/inbox/{id}/ack { state: opened }（開けたことを知らせる設定のときだけ）
    S-->>B: 204
```

`cursor` は端末に保存しておき、次の取得で渡す（差分だけが返る）。第 1 版では `cells` を送らないので、サーバーは受け手の位置を知らない。

### 4. 送り手がメッセージを管理する

```mermaid
sequenceDiagram
    autonumber
    participant A as アプリ（送り手）
    participant S as サーバー
    participant B as アプリ（受け手）
    A->>S: GET /v1/messages?state=active,paused
    S-->>A: 200 { messages: [{ id, rev, state, counts }] }
    A->>S: GET /v1/messages/{id}/deliveries
    S-->>A: 200 { counts, recipients: [{ account, state }] }
    A->>S: PATCH /v1/messages/{id} { state: paused }
    S-->>A: 200（新しく配信しない。届いた分は残る）
    A->>S: PUT /v1/messages/{id}（rev: 2 の封筒）
    S-->>A: 200 { rev: 2 }
    B->>S: GET /v1/inbox
    S-->>B: type: message（rev: 2）→ 古い版を置き換える
    A->>S: DELETE /v1/messages/{id}
    S-->>A: 204
    B->>S: GET /v1/inbox
    S-->>B: type: revoked（reason: deleted）→ 端末からも消す
```

- 2 台の端末から同時に更新すると、遅いほうは `409 rev_conflict` になる。`GET /v1/messages/{id}` で最新を取り直して作り直す
- `ended`（期限切れ）の再開や、`deleted` の更新は `409 invalid_state`

### 5. グループで送る

```mermaid
sequenceDiagram
    autonumber
    participant O as アプリ（作成者）
    participant S as サーバー
    participant M as アプリ（メンバー）
    O->>O: 名簿 { id, owner, rev: 1, members } を作って署名
    O->>S: POST /v1/groups（SignedRoster）
    S-->>O: 201
    M->>S: GET /v1/groups/{groupId}
    S-->>M: 200 SignedRoster
    M->>M: 作成者の署名を確認し、メンバー全員の暗号化鍵を取得・確認
    M->>M: DEK をメンバー全員分包み、groupRev: 1 で封筒を作る
    M->>S: POST /v1/messages（visibility: group）
    alt 名簿が更新されていた（groupRev が古い）
        S-->>M: 409 rev_conflict
        M->>S: GET /v1/groups/{groupId}（最新の名簿を取り直して作り直す）
    else 最新
        S-->>M: 201
    end
```

名簿の署名を確認するので、サーバーが勝手にメンバーを足しても送り手が気づける。

### 6. 受信フィルターを設定する

```mermaid
sequenceDiagram
    autonumber
    actor U as 利用者
    participant App as アプリ
    participant S as サーバー
    App->>S: GET /.well-known/sanpo-messages
    S-->>App: filterFields.subscription（使える項目）
    U->>App: 条件を選ぶ（送り手・種類・タグ・時間帯・ブロック）
    App->>S: POST /v1/filters/validate { kind: subscription, filter }
    alt 問題あり
        S-->>App: 400 invalid_filter { path, detail }
        App->>U: どこが誤りかを表示
    else 問題なし
        S-->>App: 200 { ok: true }
        App->>S: PUT /v1/subscriptions/me { filter, blocked, quietHours, shareOpened }
        S-->>App: 200 Subscription
    end
```

フィルター式の例（友だちとグループからは全部、それ以外はイベントとお知らせだけ）:

```json
{
  "any": [
    { "field": "message.visibility", "op": "in", "value": ["direct", "group"] },
    { "field": "message.category", "op": "in", "value": ["event", "notice"] }
  ]
}
```

### 7. トークンの期限切れ・エラーへの対応

```mermaid
sequenceDiagram
    autonumber
    participant App as アプリ
    participant S as サーバー
    App->>S: 任意の /v1 のリクエスト（Bearer token）
    S-->>App: 401 token_expired
    App->>S: POST /v1/auth/challenge → POST /v1/auth/token
    S-->>App: 新しい token
    App->>S: 同じリクエストを 1 回だけ再送
    alt 429 rate_limited
        S-->>App: Retry-After: 30
        App->>App: 30 秒待ってから再送
    else 5xx
        S-->>App: 500 server_error
        App->>App: 間隔を広げながら再試行
    end
```

## 立場ごとの使い方

### サーバーを作る人

1. 契約書の「サーバーの準拠要件」を読む
2. このファイルから、使う言語のサーバーのひな形を生成してもよい（例: [OpenAPI Generator](https://openapi-generator.tech/)）。生成しなくても、この定義どおりに応答すれば準拠できる
3. 第 1 版は `x-sanpo-release: 1` の操作をすべて実装し、検出 API の `features` を `["direct", "group", "subscription"]` にする
4. 準拠テスト（作成予定。任意のサーバーの URL に対して流せるもの）で確認する

### アプリを作る人

- 接続先は設定の URL だけで決める。特定のサーバーの名前や証明書を埋め込まない
- リクエスト・レスポンスのデータクラスは、このファイルから生成しても、手で書いてもよい（既存の API クライアントは OkHttp と手書きの JSON 処理）
- 検出 API の `features`・`limits`・`filterFields` に合わせて、画面に出す機能と入力の上限を変える
- 署名の合わない封筒、名簿と合わない配信など、準拠していない応答は使わずに捨てる

## 定義を変えるとき

1. 先に契約書を直し、承認をとる（変更履歴に追記）
2. このファイルを直して `npx @redocly/cli lint sanpo-messages.openapi.yaml` を通す
3. 項目・エンドポイントの追加は互換性のある変更。既存の項目の意味の変更・削除は `/v2` を検討する（契約書の「互換性方針」）
