# AGENTS.md

This file provides guidance to AI agents when working with code in this repository.

## アプリの概要
このアプリは、ユーザーの端末に保存されている音楽ファイルを再生するプレイヤーアプリです。以下の画面で構成されています。
- 手動追加したフォルダのファイル一覧（音声・画像・PDF・txt）
- プレイリスト一覧とトラック一覧
- 再生画面とミニプレイヤー
- フォルダ登録・再読み込み・アクセス復旧の設定画面

## 言語設定

このプロジェクトのファーストランゲージは日本語です。コードコメント、ドキュメント、コミットメッセージなどは基本的に日本語で記述してください。

## 開発環境

- JDK: プロジェクトはJDK 17を使用(Android Studioで設定)
- 必要ツール: Android Studio

## 技術スタック
- Jetpack Compose
- Media3

## アーキテクチャ

Androidの公式ドキュメントのアーキテクチャガイドラインに従います
https://developer.android.com/topic/architecture?hl=ja

1. UIレイヤ
UI レイヤは、次の 2 種類の構成要素で構成されています。
- データを画面にレンダリングするUI要素。これらの要素は、Jetpack Compose関数を使用して作成します。
- データを保持してUIに公開し、ロジックを処理する状態ホルダー（ViewModel など）

2. データレイヤ
アプリのデータレイヤには、ビジネス ロジックが含まれています。ビジネス ロジックはアプリに価値をもたらすものであり、アプリがデータを作成、保存、変更する方法を決定するルールで構成されています。
データレイヤは、それぞれが 0 から多数のデータソースを含むことができるリポジトリで構成されています。アプリで処理するデータの種類ごとにリポジトリ クラスを作成する必要があります。


## パッケージと依存方向

単一のappモジュールを保ち、実装の生成は`di/AppContainer`と`di/ViewModelFactories`に集約します。

- `ui`: Routeによる接続と、Screenによる描画、ViewModelによるデータ状態。Repositoryの契約を使用し、DAO・DataSource・Impl・SAFのパスCodecを直接参照しません。
- `domain`: 複数画面で共有する処理。`ResolvePlaylistTracks`は重複行のIDと順序を保ったまま、アクセスできない曲を除外します。
- `data/repository`: Repositoryのinterface。`data/repository/impl`の実装は`internal`です。
- `data/local/database`, `dao`, `entity`: Room。EntityはUIへ公開しません。
- `data/datasource/document`, `preferences`: DocumentsProvider、URI権限、SharedPreferencesとの接続。
- `data/library`: 走査・パスCodec・モデル変換。走査完了後にだけスナップショットをDBへ反映します。
- `playback/model`, `loop`: 型付き再生キュー・要求・開始位置解決・ABリピートの規則。
- `playback`: Media3接続、MediaItemと通知Intentの変換。UI全体で1つの接続を共有し、未接続futureを含めて解放します。
- `ui/preview`: 座標変換を担当するView、読込と組版を担当するLoader、PDF描画とリソースを所有するRenderer。PDFのopen・描画・closeは同じ直列executorで実行します。

依存は`UI -> domain / repository契約 -> data実装 -> DataSource / DAO`です。生成時にだけDIが各実装を知ります。`DependencyBoundaryTest`で逆依存を検出します。単なる委譲だけのUseCaseや、全クラスへのinterface追加は不要です。

## リファクタリング時に維持する仕様

- 音声の端末全体自動検出は行わず、SAFで手動追加したフォルダだけを読み込みます。
- 登録解除・一時的なアクセス喪失・走査キャンセルで保存済み文書IDを変更しません。再追加・同じURIの再許可で曲IDを維持します。
- プレイリストは同じ曲を複数回登録できます。曲IDと行IDを区別し、開始位置・並べ替え・削除は対象の行を保持します。
- DBは`asmr_player.db`、version 7、設定は`library_settings` / `setup_complete`を維持します。既存テーブル、通知ACTION・EXTRAキー、キュー種別文字列を変更する場合は移行方針と回帰テストが必要です。
- 画像はトラック指定 > キュー指定 > メタデータの順に表示します。永続URI権限はRepositoryが管理し、両方の保存先から参照がなくなってから解放します。
- ABリピートは再生画面でのみ監視し、保存範囲の復元時に自動開始しません。
- suspend処理では`CancellationException`を通常のエラー表示に変換しません。`runSuspendCatching`または明示的な再throwを使用します。
- テキストの読込上限・文字コード切替、PDFのパイプから一時ファイルへのコピー、画像の専用ダイアログを維持します。

## 検証方針

JDK 17で実行します。ビルドが遅くても、合理的な理由なしに中断しません。

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest
```

JVMテストはAB状態機械・開始位置・文字コード・座標変換・ViewModelのキャンセルと失敗時の状態遷移・依存境界を検証します。ViewModelテストは`kotlinx-coroutines-test`でMainを差し替え、待機時間に依存させません。

実機テストはRoomの移行とID維持、走査スナップショット、URI権限と画像優先順位、通知の復元、Media3の接続と解放、Composeの操作、PDF/txt/画像の描画を検証します。DB検証は専用DBまたはin-memory DBを使用し、利用者のDBを消去しません。

PRとmain/masterへのpushで単体テスト・Lint・本体APKとテストAPKのビルドを実行します。PRでは`.github/fixtures/google-services-test.json`の検証専用ダミー設定を使用し、配布APKはpush時だけ生成した成果物を公開します。実機テストの成功とCIの成功は別に報告します。
