# RFC-001 プロンプトと組み込みのチャンネルを :station-format に移す

## 文書情報

| 項目 | 内容 |
|---|---|
| 文書ID | RFC-001 |
| ドキュメント種別 | リファクタリング計画書 |
| 対象システム/機能 | SanpoGuide のプロンプト（`PromptTemplates`・プロンプトのファイル）と組み込みのチャンネル |
| 関連Skill | refactoring-safety |
| 作成日 | 2026-10-03 |
| 作成者 | Claude（開発者との検討） |
| 承認者 | 開発者（方針は 2026-10-03 に決定。実施結果は PR でレビュー） |
| ステータス | 実施済（PR レビュー待ち） |
| 版 | 1.0 |

## 目的・背景

チャンネル管理システム（[sanpo-channel-console](https://github.com/duwenji/sanpo-channel-console)）は、審査でアプリと同じプロンプトを組み立てて AI に話させる（同リポジトリの API-001 C-1）。そのため、アプリ本体にあったプロンプトの組み立て（`PromptTemplates`・プロンプトのファイル・スロットの差し込み方・出来事のプロンプトのつなぎ方）と、空いたスロットを埋める組み込みのチャンネルを、Android に依存しない `:station-format` に移す。あわせて `:station-format` を GitHub Packages に公開する（同リポジトリの ADR-001 T-4）。

**アプリの動きは変えない。**

## 対応元ID（トレーサビリティ）

| 対応元ID | 内容 | 対応状況 |
|---|---|---|
| sanpo-channel-console API-001 C-1 | 見本用のプロンプトは station-format で組み立てる | 本書で実施 |
| sanpo-channel-console ADR-001 T-4 | station-format の公開方法（GitHub Packages） | 本書で実施 |
| API-003 F-7 | 組み込みのチャンネルの置き場所 | 置き場所を改めた |

## 方針・決定事項

| No. | 論点 | 決定 |
|---|---|---|
| R-1 | ファイルの置き場所 | `station-format/src/main/resources/sanpoguide/{prompts,channels}/` を正本にする。アプリは AssetManager ではなくクラスパスから読む（開発者の決定） |
| R-2 | ZIP の展開・配信元の署名の確認（API-003 の手順 2・4） | 今回は入れない。別の PR で（開発者の決定） |
| R-3 | 公開の座標 | `com.example.sanpoguide:station-format`。版は `gradle.properties` の `stationFormatVersion`（アプリの版とは別）。タグ `station-format-v<版>` で GitHub Actions が公開する（開発者の決定） |
| R-4 | パッケージ名 | Kotlin のパッケージ名は変えない（`com.example.sanpoguide.prompt`）。アプリの import を変えずに済む |
| R-5 | フォルダの一覧 | jar・APK の中のフォルダは一覧できないので、ビルド時に `sanpoguide/channels/index.txt` を作る（`channelIndex` タスク） |
| R-6 | 改行 | `app/src/main/assets/**` と `station-format/src/main/resources/**` を `.gitattributes` で LF に固定する。Windows（`core.autocrlf=true`）で取り出すと組み込みのチャンネルが CR を含み、自分の確認に落ちていた |

### 手順と保証

| 手順 | 内容 | 保証 |
|---|---|---|
| 0 | 改行を LF に固定（R-6） | 変更前の時点で失敗していた 12 件のテストが通る |
| 1 | `PromptTemplates`・`Prompts`（パスの一覧）を移す。`Prompts.fromAssets` を `fromResources` に | `PromptTemplatesTest` を移して通す |
| 2 | プロンプトのファイルと組み込みのチャンネルを移す。`BuiltInChannels`（station-format）を足し、`BuiltInStations`（app）はそれを `Station` にするだけに | 全組み込みチャンネル × システムプロンプト・出来事の追加の指示（36 件）と、プロンプトの見本（48 件）が、移す前と 1 文字も変わらない |
| 3 | スロットの差し込み方（`guideSystemVars` など）と出来事のプロンプトのつなぎ方を `StationPrompts` に移す。`Station`・`WalkCompanion` は呼ぶだけに | 同じ比較に加え、つなぎ方を元の式と比べる単体テスト |
| 4 | 審査の見本の場面（審査基準 2.7）を組み立てる `ReviewSamples` を足す（新しい機能） | 単体テスト 6 本 |
| 5 | GitHub Packages への公開（`maven-publish`、ワークフロー） | `publishToMavenLocal`。公開した jar と org.json だけで見本を組み立てられることを JShell で確認 |

端末での確認（エミュレータ、Android 16）: 起動時に組み込みのチャンネル 4 つを読み込めること、クラスパスからプロンプト（共通の指示の読み込みを含む）と定型文を組み立てられることを、一時的なログで確かめた（コミットしていない）。

### 戻し方

すべて 1 つの PR の中で、コミットごとに戻せる。公開したパッケージは版で固定されるので、戻すときは前の版を使えばよい。

## 未決事項・リスク

| No. | 内容 | 対応 |
|---|---|---|
| 1 | ~~ZIP の展開と配信元の署名の確認（R-2）~~ → station-format 1.1.0 で追加（2026-10-04）。`StationValidator.checkArchive`、署名の確認は差し替えられる `Ed25519Verifier`（JVM は `JcaEd25519`、アプリは Tink を渡す予定） | — |
| 2 | プロンプトのファイルが見つからないときの例外が `FileNotFoundException` から `IllegalArgumentException` に変わった | ファイルはビルドに入っているので通常は起きない。`WalkCompanion` は例外の種類を問わず定型文に戻る |
| 3 | GitHub Packages の Maven は、公開リポジトリでも読むのにトークン（`read:packages`）が要る | 管理システムの CI に設定する |
| 4 | 計装テストがないため、端末での確認は手作業 | 必要になったら androidTest を整える |

## 関連ドキュメント・参照リンク

- [prompts.md](../prompts.md)、[architecture.md](../architecture.md)、[channel-package-format.md](../channel-package-format.md)
- 検討の記録: [skill-logs/refactoring_safety_2026-10-03.md](../skill-logs/refactoring_safety_2026-10-03.md)

## 変更履歴

| 日付 | 版 | 変更内容 | 変更者 |
|---|---|---|---|
| 2026-10-03 | 1.0 | 作成・実施 | Claude |
