# tinyworks

個人用の小さな機能を一つにまとめたAndroidアプリです。機能ごとにコードと設計書を分け、一つのAPKとして提供します。

## 機能

| 機能 | 内容 | 設計資料 |
| --- | --- | --- |
| ホーム | 機能一覧と各機能への入口 | [ホーム](docs/apps/home/00_企画書.md) |
| Pomodoro | 作業・休憩タイマー、設定、統計・履歴 | [Pomodoro](docs/apps/pomodoro/00_企画書.md) |
| ポイの日（PoyDay） | ゴミ出しルールの設定、カレンダー、ホーム画面ウィジェット | [ポイの日](docs/apps/poyday/00_企画書.md) |

## リポジトリ構成

```text
tinyworks/
├─ android/                 # AndroidプロジェクトとGradle Wrapper
│  └─ app/src/
│     ├─ main/
│     │  ├─ kotlin/io/github/mockuptools/tinyworks/
│     │  │  ├─ MainActivity.kt
│     │  │  ├─ core/         # 共通UI・保存基盤
│     │  │  └─ feature/      # home・pomodoro・poyday
│     │  └─ res/
│     └─ test/               # 単体テスト
├─ docs/                    # 全体・共通設計と機能別設計
├─ .devcontainer/           # Codespacesの開発環境
└─ .github/workflows/       # 検証とFirebase配布
```

Gradleモジュールは`app`一つです。機能間の共有処理は`core`、各機能の画面・状態・保存処理は`feature/<機能名>`で管理します。

## 開発を始める

GitHub Codespacesで開くと、`.devcontainer`で定義したJDK 17とAndroid SDKを利用できます。ローカルの場合もJDK 17、Android SDK API 37、Build Tools 37.0.0を用意し、SDKの場所を`ANDROID_HOME`または`android/local.properties`の`sdk.dir`に設定してください。

リポジトリ直下から実行します。

```sh
cd android
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Windows（PowerShell）の場合:

```powershell
cd android
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug
```

Debug APKの出力先は`android/app/build/outputs/apk/debug/app-debug.apk`です。Gradleは同梱のWrapperを使います。対応端末はAndroid API 37以上です。

詳しい手順は[開発環境構築手順](docs/30_開発環境構築手順.md)、変更時の確認事項は[開発ガイド](CONTRIBUTING.md)を参照してください。

## 検証と配布

- CIは単体テスト、Android Lint、Debug APKビルドを実行します。
- `main`へのpushでCIが成功すると、固定署名鍵で署名したAPKをFirebase App Distributionへ配布します。
- 配布用の認証情報はGitHub Actions Secretsで管理します。設定は[共通CI/CD設計書](docs/35_共通CI-CD設計書.md)に記載しています。

## ドキュメント

[ドキュメント一覧](docs/README.md)から、企画、設計、開発環境、配布・運用、プライバシー方針を参照できます。

## ライセンス

ソースコードと設計資料は[MIT License](LICENSE)のもとで公開します。
