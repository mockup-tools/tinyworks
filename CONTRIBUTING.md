# 開発ガイド

## 開発環境と確認

環境の準備は[開発環境構築手順](docs/30_開発環境構築手順.md)を参照してください。Android Studioで開く場合は、リポジトリ直下の`android/`を選びます。

リポジトリ直下から、次の検証を実行します。

```sh
cd android
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Windowsでは`./gradlew`を`.\gradlew.bat`に置き換えます。UI、通知、ウィジェットなどは、変更に応じてAPI 37以上の実機またはエミュレーターでも確認してください。

## コードと設計資料の置き場所

- 起動処理と全体ナビゲーションは`MainActivity.kt`で組み立てます。
- 各機能のコードは`android/app/src/main/kotlin/io/github/mockuptools/tinyworks/feature/<機能名>/`、設計資料は`docs/apps/<機能名>/`に置きます。
- 複数機能で実際に共有する処理は`core/`へ置きます。`core`から`feature`への依存や、機能間の直接依存は避けます。
- 単体テストは`android/app/src/test/`に置き、対象と同じパッケージを使います。
- 新しい機能を追加したら、[README](README.md)と[ドキュメント一覧](docs/README.md)にも追記します。

## 変更の確認

- 目的ごとにブランチを作り、関連する実装・設計資料・検証結果をまとめてPull Requestに記載します。
- パスやビルド設定を変更する場合は、README、設計資料、`.github/workflows/`、`.devcontainer/`の参照先も確認します。
- 依存バージョンは`android/gradle/libs.versions.toml`とGradle Wrapperで管理します。
- ビルド生成物、`local.properties`、署名鍵、Firebase認証情報はコミットしません。

`main`へのpush後はCIの成功を受けてFirebase配布が実行されます。配布の詳細は[共通CI/CD設計書](docs/35_共通CI-CD設計書.md)を参照してください。
